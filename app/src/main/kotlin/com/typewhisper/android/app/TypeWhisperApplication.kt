package com.typewhisper.android.app

import android.app.Application
import android.content.Context
import com.typewhisper.android.app.data.AppContainer
import com.typewhisper.android.app.dictation.DictationEngineSource
import com.typewhisper.android.app.engine.EngineCatalog
import com.typewhisper.android.app.engine.OnDeviceModels
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engines.whispercpp.ModelDownloadException
import com.typewhisper.android.ime.ImeEngineSource

/**
 * Holds the [AppContainer] so services, tiles, and composables all share one set of
 * repositories — DataStore and Room are process-singletons, and two instances of either
 * in one process deadlocks on file locks.
 *
 * Also the one place the keyboard's engine resolver is installed: the IME lives in this
 * process and module rules forbid it from reaching into `:engines:*`, so the app hands
 * it a function instead of a dependency.
 */
class TypeWhisperApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ImeEngineSource.resolver = { context: Context, onModelProgress ->
            val id = DictationEngineSource.engineId(context)
            if (id == EngineCatalog.ID_WHISPERCPP) {
                try {
                    OnDeviceModels.ensureReady(context, onModelProgress)
                } catch (e: ModelDownloadException) {
                    throw EngineException.BadRequest(OnDeviceModels.explain(context, e))
                }
            }
            DictationEngineSource.engine(context, id)
        }
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as TypeWhisperApplication).container
    }
}
