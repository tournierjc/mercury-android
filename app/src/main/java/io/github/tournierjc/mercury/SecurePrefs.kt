package io.github.tournierjc.mercury

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Shared encrypted preferences (Android Keystore backed) with a plain-text
 * fallback for devices where the Keystore is unavailable (some emulators/ROMs).
 *
 * The single factory for `mercury_secure`: AppContainer (cookies) and BridgePrefs
 * (bridge token) both come through here, so the fallback decision is made once.
 * When the fallback fires, [degraded] turns true and the bridge refuses to serve —
 * a bearer token sitting in plain prefs must not front the phone's data.
 */
object SecurePrefs {
    @Volatile
    private var cached: SharedPreferences? = null

    @Volatile
    private var degradedCache = false

    /** True when the Keystore was unusable and secrets live in plain-text prefs. */
    val degraded: Boolean
        get() = degradedCache

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val (prefs, degraded) = runCatching {
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
            }.map { it to false }.getOrElse {
                context.getSharedPreferences("mercury_secure_plain", Context.MODE_PRIVATE) to true
            }
            degradedCache = degraded
            cached = prefs
            return prefs
        }
    }
}
