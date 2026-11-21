package com.cg.readingcompanion.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "reading_companion_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var tencentSecretId: String
        get() = prefs.getString("tencent_secret_id", "") ?: ""
        set(value) = prefs.edit().putString("tencent_secret_id", value).apply()

    var tencentSecretKey: String
        get() = prefs.getString("tencent_secret_key", "") ?: ""
        set(value) = prefs.edit().putString("tencent_secret_key", value).apply()

    var tencentRegion: String
        get() = prefs.getString("tencent_region", "ap-guangzhou") ?: "ap-guangzhou"
        set(value) = prefs.edit().putString("tencent_region", value).apply()

    var model: String
        get() = prefs.getString("model", "deepseek-v4.1-flash") ?: "deepseek-v4.1-flash"
        set(value) = prefs.edit().putString("model", value).apply()

    var token: String
        get() = prefs.getString("token", "change-me") ?: "change-me"
        set(value) = prefs.edit().putString("token", value).apply()

    var port: Int
        get() = prefs.getInt("port", 8790)
        set(value) = prefs.edit().putInt("port", value).apply()

    fun llmConfig(): LlmConfig = LlmConfig(
        model = model,
        apiKey = apiKey,
    )
}
