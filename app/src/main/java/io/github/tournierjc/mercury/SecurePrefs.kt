package io.github.tournierjc.mercury

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Shared encrypted preferences (Android Keystore backed) with a plain-text
 * fallback for devices where the Keystore is unavailable (some emulators/ROMs).
 */
object SecurePrefs {
    @Volatile
    private var cached: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "mercury_secure",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ) as SharedPreferences
            }.getOrElse {
                context.getSharedPreferences("mercury_secure_plain", Context.MODE_PRIVATE)
            }
            cached = prefs
            return prefs
        }
    }
}
