package com.typewhisper.android.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API keys and the Mac relay token, in a file encrypted with a Keystore master key.
 *
 * Values are never returned by a flow, never written to a log, and never placed in
 * DataStore — the plain preferences file is world-readable in the app's own backup
 * scope, and a leaked cloud key spends the user's money.
 */
public class SecretStore(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext, MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val store = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Null when the user has not configured the engine, which is the default for every engine. */
    public fun secret(pluginId: String): String? =
        store.getString(keyFor(pluginId), null)?.takeIf { it.isNotEmpty() }

    public fun saveSecret(pluginId: String, value: String) {
        store.edit().apply {
            if (value.isEmpty()) remove(keyFor(pluginId)) else putString(keyFor(pluginId), value)
        }.apply()
    }

    public fun clearSecret(pluginId: String) {
        store.edit().remove(keyFor(pluginId)).apply()
    }

    public fun hasSecret(pluginId: String): Boolean = secret(pluginId) != null

    private fun keyFor(pluginId: String): String = PrefKeys.API_KEY_PREFIX + pluginId

    private companion object {
        private const val FILE_NAME = "tw_secrets"
        private const val MASTER_KEY_ALIAS = "typewhisper_master_key"
    }
}
