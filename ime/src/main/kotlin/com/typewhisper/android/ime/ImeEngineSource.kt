package com.typewhisper.android.ime

import android.content.Context
import com.typewhisper.android.engineapi.EnginePlugin

/**
 * The keyboard's only view of the engine world. `:ime` must not depend on `:engines:*`,
 * so the app installs a resolver at startup and the keyboard asks it, per dictation,
 * for the engine the user selected in settings. The second argument receives
 * model-download progress in [0, 1] when getting the engine ready costs a fetch:
 *
 * ```
 * ImeEngineSource.resolver = { context, progress -> ... }
 * ```
 *
 * Until a resolver is installed the keyboard refuses to dictate with an explanation
 * rather than inventing a transcript. No engine is ever loaded dynamically.
 */
object ImeEngineSource {

    @Volatile
    var resolver: (suspend (Context, (Float) -> Unit) -> EnginePlugin)? = null
}
