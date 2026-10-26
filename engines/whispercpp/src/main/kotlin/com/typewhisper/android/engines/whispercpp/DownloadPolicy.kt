package com.typewhisper.android.engines.whispercpp

/**
 * Pure decision logic for the model downloader, factored out of the Android
 * I/O code so it can be unit tested without a device or a live socket.
 */
internal object DownloadPolicy {

    /** Extra headroom demanded beyond the model size so a partial download or
     *  an OS cache flush does not fill the volume mid-write. */
    const val MIN_FREE_MARGIN_BYTES: Long = 32L * 1024 * 1024

    /** Extra margin on top of the target size so an interrupted resume still
     *  has room to finish. */
    fun requiredFreeBytes(modelSizeBytes: Long): Long =
        modelSizeBytes + MIN_FREE_MARGIN_BYTES

    /**
     * Decides whether [availableBytes] is enough to fetch a [modelSizeBytes]
     * model. Negative availability (unknown) is reported as insufficient
     * rather than guessed at, so the UI surfaces a real storage error.
     */
    fun hasEnoughFreeSpace(availableBytes: Long, modelSizeBytes: Long): Boolean {
        if (availableBytes < 0) return false
        return availableBytes >= requiredFreeBytes(modelSizeBytes)
    }

    /**
     * Fraction complete in [0, 1]. Unknown total (<= 0) yields 0 so progress bars
     * stay pinned at the start instead of showing a bogus 100%.
     */
    fun progressFraction(bytesDone: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L || bytesDone <= 0L) return 0f
        val f = bytesDone.toFloat() / totalBytes.toFloat()
        return if (f > 1f) 1f else f
    }

    /**
     * Whether a resume should be attempted: we have some bytes already, the
     * server advertised byte ranges, and the partial file is smaller than the
     * full model (i.e. it is not already complete or corrupt-oversized).
     */
    fun shouldResume(existingBytes: Long, totalBytes: Long, serverAcceptsRanges: Boolean): Boolean {
        if (!serverAcceptsRanges) return false
        if (existingBytes <= 0L) return false
        if (totalBytes > 0L && existingBytes >= totalBytes) return false
        return true
    }

    /**
     * Gate for cellular downloads. On a metered network the download only
     * proceeds when the user has explicitly opted in; unmetered (Wi-Fi, or
     * unknown treated as unmetered to preserve airplane-mode-free usability)
     * always proceeds.
     */
    fun shouldDownload(isMetered: Boolean, userOptedInMetered: Boolean): Boolean =
        !isMetered || userOptedInMetered
}
