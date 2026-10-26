package com.typewhisper.android.app.engine

import android.content.Context
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.engineapi.EnginePlugin
import com.typewhisper.android.engines.cloud.GroqEnginePlugin
import com.typewhisper.android.engines.mac.MacEnginePlugin
import com.typewhisper.android.storage.PreferencesRepository

/**
 * One selectable row in the engine picker.
 *
 * [factory] reads the encrypted credential store and the relay settings, so it must be
 * called from a background dispatcher — the recording path and the settings screen both
 * do. It never throws for a missing credential: the engines raise a typed
 * [com.typewhisper.android.engineapi.EngineException.Authentication] when they are asked
 * to transcribe without one, which is what keeps the picker and the failure message
 * telling the same story.
 */
data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    /** True when the engine needs a credential the user has to paste in. */
    val requiresCredential: Boolean,
    /** Field label, e.g. "API key" or "Mac API token"; null when the engine takes no credential at all. */
    val credentialLabel: String?,
    /**
     * Phrase for the hint under a row whose address the user has not typed yet, or null
     * when the engine talks to something this app already knows.
     */
    val endpointHint: String? = null,
    val factory: (Context) -> EnginePlugin,
)

/**
 * The curated engine list. Engines are built-ins, enumerated here and nowhere else —
 * Play policy forbids loading engines at runtime.
 */
object EngineCatalog {

    const val ID_WHISPERCPP: String = PreferencesRepository.DEFAULT_ENGINE_ID
    const val ID_MAC: String = MacEnginePlugin.PLUGIN_ID
    const val ID_GROQ: String = "groq"
    const val ID_OPENAI: String = "openai"
    const val ID_CUSTOM: String = "custom"

    const val OPENAI_BASE_URL: String = "https://api.openai.com/v1"
    const val OPENAI_MODEL: String = "gpt-4o-transcribe"

    /** Model the custom engine asks for when the user leaves the model field empty. */
    const val CUSTOM_DEFAULT_MODEL: String = "whisper-large-v3-turbo"

    /** On-device first: it is the default and the only engine that needs no setup. */
    val entries: List<EngineDescriptor> = listOf(
        EngineDescriptor(
            id = ID_WHISPERCPP,
            displayName = "On-device (whisper.cpp)",
            description = "Runs on the phone, offline. No account, no key.",
            requiresCredential = false,
            credentialLabel = null,
            factory = { context ->
                OnDeviceModels.engine(
                    context,
                    TypeWhisperApplication.container(context).whisperModelId.value,
                )
            },
        ),
        EngineDescriptor(
            id = ID_MAC,
            displayName = "TypeWhisper on Mac",
            description = "Uses whatever engines the Mac app has, through a relay on your network.",
            requiresCredential = true,
            credentialLabel = "Mac API token",
            endpointHint = "the Mac relay host under Mac harness",
            factory = { context ->
                val container = TypeWhisperApplication.container(context)
                MacEnginePlugin(
                    baseUrl = container.macBaseUrl().orEmpty(),
                    token = container.secrets.secret(ID_MAC).orEmpty(),
                )
            },
        ),
        EngineDescriptor(
            id = ID_GROQ,
            displayName = "Groq",
            description = "whisper-large-v3-turbo over Groq's API. Needs a Groq API key.",
            requiresCredential = true,
            credentialLabel = "Groq API key",
            factory = { context ->
                GroqEnginePlugin(apiKey = credential(context, ID_GROQ))
            },
        ),
        EngineDescriptor(
            id = ID_OPENAI,
            displayName = "OpenAI",
            description = "gpt-4o-transcribe over OpenAI's API. Needs an OpenAI API key.",
            requiresCredential = true,
            credentialLabel = "OpenAI API key",
            factory = { context ->
                GroqEnginePlugin(
                    apiKey = credential(context, ID_OPENAI),
                    baseUrl = OPENAI_BASE_URL,
                    model = OPENAI_MODEL,
                    pluginId = ID_OPENAI,
                    displayName = "OpenAI",
                )
            },
        ),
        EngineDescriptor(
            id = ID_CUSTOM,
            displayName = "Custom endpoint",
            description = "Any OpenAI-compatible /audio/transcriptions endpoint you run or rent. " +
                "Type its URL below; nothing is assumed for you.",
            requiresCredential = false,
            credentialLabel = "Bearer token (optional)",
            endpointHint = "a base URL under Custom endpoint",
            factory = { context ->
                val container = TypeWhisperApplication.container(context)
                GroqEnginePlugin(
                    apiKey = credential(context, ID_CUSTOM),
                    baseUrl = container.customBaseUrl.value.trim(),
                    model = CUSTOM_DEFAULT_MODEL,
                    pluginId = ID_CUSTOM,
                    displayName = "Custom endpoint",
                    requiresApiKey = false,
                )
            },
        ),
    )

    val default: EngineDescriptor = entries.first { it.id == ID_WHISPERCPP }

    fun byId(id: String?): EngineDescriptor? = entries.firstOrNull { it.id == id }

    /**
     * What [descriptor] still needs typed into settings before it can run, or null when it
     * is ready. Engines aimed at a machine this app cannot guess — the Mac relay, an
     * endpoint of the user's own — stay disabled until they are named, because a blank
     * address would otherwise surface as a failed dictation rather than as setup.
     */
    fun missingSetup(descriptor: EngineDescriptor, macHost: String, customBaseUrl: String, hasCredential: Boolean): String? =
        when {
            descriptor.requiresCredential && !hasCredential -> descriptor.credentialLabel ?: "an API key"
            descriptor.endpointHint != null && address(descriptor, macHost, customBaseUrl).isNullOrBlank() ->
                descriptor.endpointHint
            else -> null
        }

    private fun address(descriptor: EngineDescriptor, macHost: String, customBaseUrl: String): String? =
        when (descriptor.id) {
            ID_MAC -> macHost
            ID_CUSTOM -> customBaseUrl
            else -> null
        }

    /**
     * The stored credential for [id], or "" when there is none. The engines accept an
     * empty key as "not configured" and fail typed, so a missing key is never a crash.
     */
    private fun credential(context: Context, id: String): String =
        TypeWhisperApplication.container(context).secrets.secret(id).orEmpty()
}
