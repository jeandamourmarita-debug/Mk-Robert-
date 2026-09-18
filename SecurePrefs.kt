package com.mkrobot.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user-supplied AI API key encrypted on-device.
 *
 * The key is NEVER hard-coded, never bundled in the APK, and never leaves
 * the device except as an Authorization header sent directly from this
 * device to the AI endpoint the user configures.
 */
object SecurePrefs {

    private const val FILE_NAME = "mk_robot_secure_prefs"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_AI_ENDPOINT = "ai_endpoint"
    private const val KEY_LANGUAGE = "assistant_language" // "rw" or "en"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(context: Context): String? =
        prefs(context).getString(KEY_API_KEY, null)

    fun saveEndpoint(context: Context, url: String) {
        prefs(context).edit().putString(KEY_AI_ENDPOINT, url).apply()
    }

    fun getEndpoint(context: Context): String =
        prefs(context).getString(KEY_AI_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT

    fun saveLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()
    }

    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "rw") ?: "rw"

    // Anthropic's Messages API is the default target; the user can point this
    // at any compatible endpoint from the Settings screen.
    private const val DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages"
}
