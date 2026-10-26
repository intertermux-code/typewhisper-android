package com.typewhisper.android.engines.whispercpp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Download failures surfaced to the settings/onboarding UI. Kept local because
 * fetching a model is not part of the [com.typewhisper.android.engineapi.EnginePlugin]
 * transcribe contract and its error shapes differ (policy blocks, HTTP, I/O).
 */
sealed class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InsufficientStorage(message: String) : ModelDownloadException(message)
    class MeteredNetwork(message: String) : ModelDownloadException(message)
    class Http(val code: Int, message: String) : ModelDownloadException(message)
    class Failed(message: String, cause: Throwable?) : ModelDownloadException(message, cause)
}

/**
 * Fetches quantized whisper.cpp models into the app's files dir with progress,
 * resumable range requests, a free-space pre-check, and a cellular guard.
 *
 * Only the pure decisions (storage headroom, resume eligibility, metered policy,
 * progress math) live in [DownloadPolicy] and are unit-tested; the socket work
 * here is exercised by manual device testing.
 */
class WhisperModelDownloader(
    context: Context,
    private val client: OkHttpClient = DEFAULT_CLIENT,
) {
    private val appContext = context.applicationContext
    /** Where the engine looks for models; public so callers can build a plugin. */
    val modelsDir: File = File(appContext.filesDir, "whisper").apply { mkdirs() }

    fun modelFile(model: WhisperModel): File = File(modelsDir, model.fileName)

    fun isDownloaded(model: WhisperModel): Boolean = modelFile(model).isFile

    /**
     * Downloads [model] unless already present, returning the model file.
     *
     * @param allowMetered overrides the cellular guard (user opt-in).
     * @param onProgress fraction in [0,1] plus raw byte counts.
     */
    suspend fun download(
        model: WhisperModel,
        allowMetered: Boolean = false,
        onProgress: (fraction: Float, bytesDone: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val target = modelFile(model)
        if (target.isFile && target.length() == model.sizeBytes) {
            onProgress(1f, model.sizeBytes, model.sizeBytes)
            return@withContext target
        }

        if (isMetered(appContext) && !allowMetered) {
            throw ModelDownloadException.MeteredNetwork(
                "'${model.id}' is ${model.sizeBytes} bytes on a metered network; " +
                    "re-enable on Wi-Fi or opt in to the download",
            )
        }

        val part = File(modelsDir, model.fileName + ".part")
        val partBytes = if (part.isFile) part.length() else 0L
        enforceStorageBudget(model, existingPartialBytes = partBytes)

        // A partial at/over the full size is corrupt; drop it and start clean.
        if (partBytes >= model.sizeBytes) part.delete()
        // We optimistically assume the CDN honors ranges and confirm with the
        // response status below; shouldResume encodes the byte-count rules.
        val existing = if (DownloadPolicy.shouldResume(partBytes, model.sizeBytes, serverAcceptsRanges = true)) {
            partBytes
        } else {
            0L
        }

        val requestBuilder = Request.Builder().url(model.url)
        if (existing > 0L) {
            requestBuilder.header("Range", "bytes=$existing-")
        }

        try {
            downloadInto(model, requestBuilder.build(), part, target, existing, onProgress)
        } catch (e: ModelDownloadException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "download of '${model.id}' failed", e)
            throw ModelDownloadException.Failed("could not download '${model.id}': ${e.message}", e)
        }

        onProgress(1f, model.sizeBytes, model.sizeBytes)
        target
    }

    private suspend fun downloadInto(
        model: WhisperModel,
        request: Request,
        part: File,
        target: File,
        existing: Long,
        onProgress: (fraction: Float, bytesDone: Long, totalBytes: Long) -> Unit,
    ) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ModelDownloadException.Http(response.code, "HTTP ${response.code} fetching '${model.id}'")
            }

            val body = response.body ?: throw ModelDownloadException.Failed("empty response body", null)
            val acceptsRanges = response.code == 206 && existing > 0L
            val total = when {
                acceptsRanges -> model.sizeBytes
                else -> body.contentLength().takeIf { it > 0 } ?: model.sizeBytes
            }

            // A 200 (not 206) means the server ignored the Range header, so any
            // partial bytes are unreliable; rewrite from scratch. RandomAccessFile
            // has no write-only mode, so "rw" plus a truncate stands in for "w".
            val appendFrom = if (acceptsRanges) existing else 0L

            var written = appendFrom
            RandomAccessFile(part, "rw").use { raf ->
                if (!acceptsRanges) raf.setLength(0L)
                raf.seek(appendFrom)

                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        written += read
                        onProgress(DownloadPolicy.progressFraction(written, total), written, total)
                    }
                }
            }

            if (written != model.sizeBytes) {
                // A truncated transfer stays as .part so the next call resumes.
                throw ModelDownloadException.Failed(
                    "download incomplete: $written of ${model.sizeBytes} bytes",
                    IOException("short read"),
                )
            }

            if (!part.renameTo(target)) {
                throw ModelDownloadException.Failed("could not finalize ${target.name}", null)
            }
        }
    }

    private fun enforceStorageBudget(model: WhisperModel, existingPartialBytes: Long) {
        val available = StatFs(modelsDir.absolutePath).availableBytes
        val stillNeeded = (model.sizeBytes - existingPartialBytes).coerceAtLeast(0L)
        if (!DownloadPolicy.hasEnoughFreeSpace(available, stillNeeded)) {
            throw ModelDownloadException.InsufficientStorage(
                "need ~${stillNeeded / BYTES_PER_MB} MB free for '${model.id}', " +
                    "only ${available / BYTES_PER_MB} MB available",
            )
        }
    }

    private fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        internal const val TAG = "WhisperModelDownloader"
        private val DEFAULT_CLIENT = OkHttpClient.Builder().build()
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
