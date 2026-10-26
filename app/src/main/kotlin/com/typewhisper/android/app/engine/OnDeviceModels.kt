package com.typewhisper.android.app.engine

import android.content.Context
import android.net.ConnectivityManager
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.engines.whispercpp.ModelDownloadException
import com.typewhisper.android.engines.whispercpp.WhisperCppEnginePlugin
import com.typewhisper.android.engines.whispercpp.WhisperModel
import com.typewhisper.android.engines.whispercpp.WhisperModelDownloader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The on-device model's lifecycle outside the engine: which checkpoint the user picked,
 * whether its file is on disk, and fetching it when it is not.
 *
 * Every dictation path (service, keyboard, settings screen) goes through [ensureReady],
 * so there is exactly one place that decides whether a missing model is a silent
 * download, a progress report, or a human explanation of why it cannot happen right now.
 */
object OnDeviceModels {

    private val downloadLock = Mutex()

    // One plugin instance per model id: each owns a native whisper context worth tens
    // of megabytes, so handing out a fresh one per dictation would leak the last.
    private val engineLock = Any()
    private var cachedEngine: Pair<String, WhisperCppEnginePlugin>? = null

    /** The on-device engine for [modelId], reusing the loaded native context when possible. */
    fun engine(context: Context, modelId: String): WhisperCppEnginePlugin = synchronized(engineLock) {
        cachedEngine?.takeIf { it.first == modelId }?.second?.let { return it }
        cachedEngine?.second?.release()
        WhisperCppEnginePlugin(
            modelsDir = WhisperModelDownloader(context).modelsDir,
            modelId = modelId,
        ).also { cachedEngine = modelId to it }
    }

    fun downloader(context: Context): WhisperModelDownloader = WhisperModelDownloader(context)

    suspend fun currentModel(context: Context): WhisperModel =
        WhisperModel.fromId(TypeWhisperApplication.container(context).engineConfig.whisperModelId.first())

    suspend fun isReady(context: Context): Boolean =
        downloader(context).isDownloaded(currentModel(context))

    /**
     * Downloads the selected model when its file is missing, reporting fraction-complete
     * through [onProgress]. Throws [ModelDownloadException] when the network or the disk
     * says no; [explain] turns one into the sentence the user should see.
     */
    suspend fun ensureReady(context: Context, onProgress: (Float) -> Unit = {}) {
        downloadLock.withLock {
            val model = currentModel(context)
            val downloader = downloader(context)
            if (downloader.isDownloaded(model)) return
            val allowMetered =
                TypeWhisperApplication.container(context).preferences.downloadOverCellular.first()
            downloader.download(model, allowMetered = allowMetered) { fraction, _, _ ->
                onProgress(fraction)
            }
        }
    }

    /** A progress line for screens without a progress bar of their own. */
    fun progressNote(fraction: Float): String =
        "Downloading the on-device model — ${(fraction * 100).toInt()}%"

    /** A down-at-the-socket network, not merely a metered one. */
    fun isOffline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager == null || manager.activeNetwork == null
    }

    suspend fun explain(context: Context, error: ModelDownloadException): String = when {
        isOffline(context) ->
            "No connection — the on-device model hasn't been fetched yet. " +
                "Connect once to download it, then dictation works offline."
        error is ModelDownloadException.MeteredNetwork ->
            "You're on mobile data and the model is ${modelMb(context)} MB. " +
                "Connect to Wi-Fi, or allow cellular downloads in Settings."
        error is ModelDownloadException.InsufficientStorage ->
            "Not enough free space for the model — ${error.message}"
        else -> error.message ?: "The model download failed. Try again."
    }

    private suspend fun modelMb(context: Context): Long = currentModel(context).sizeBytes / (1024 * 1024)
}
