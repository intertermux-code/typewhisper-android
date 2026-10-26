package com.typewhisper.android.app.dictation

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: a always-available trigger that needs no overlay or
 * launcher permission.
 *
 * A tile is a poor place to *run* a recording — its host can be torn down the
 * moment the shade closes — so it only ever forwards start/stop/toggle to
 * [RecordingService] and reflects the service's state back onto itself.
 *
 * A tile is therefore tap-only whatever the dictation-mode setting says: the platform hands
 * it a click and no way to tell a press from a release, so the push-to-talk gesture cannot
 * exist here.
 */
class DictationTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var renderJob: Job? = null

    override fun onClick() {
        DictationControl.toggle(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        renderJob = scope.launch {
            DictationControl.phase.collect { phase -> render(phase) }
        }
    }

    override fun onStopListening() {
        renderJob?.cancel()
        renderJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(phase: DictationPhase) {
        qsTile?.apply {
            // Active for the whole in-flight dictation, transcription included: a tile that
            // goes quiet the moment the microphone does invites a tap that starts a second
            // recording over the words the user is still waiting for.
            state = if (phase == DictationPhase.Idle) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            label = when (phase) {
                DictationPhase.Idle -> "TypeWhisper"
                DictationPhase.Recording -> "Stop dictation"
                DictationPhase.Finishing -> "Stopping…"
            }
            updateTile()
        }
    }
}
