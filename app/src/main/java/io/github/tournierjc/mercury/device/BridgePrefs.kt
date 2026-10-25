package io.github.tournierjc.mercury.device

import android.content.Context
import io.github.tournierjc.mercury.SecurePrefs
import java.security.SecureRandom
import java.util.Base64

/**
 * Réglages du pont : token d'accès, port, état activé.
 *
 * Le token est un secret bearer : il vit dans les prefs chiffrées (Keystore),
 * jamais dans les prefs en clair ni dans les logs.
 */
class BridgePrefs(context: Context) {

    private val secure = SecurePrefs.get(context)
    private val plain = context.getSharedPreferences("mercury_bridge", Context.MODE_PRIVATE)

    var token: String
        get() {
            secure.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
            return rotateToken()
        }
        set(value) = secure.edit().putString(KEY_TOKEN, value).apply()

    var port: Int
        get() = plain.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = plain.edit().putInt(KEY_PORT, value).apply()

    var enabled: Boolean
        get() = plain.getBoolean(KEY_ENABLED, false)
        set(value) = plain.edit().putBoolean(KEY_ENABLED, value).apply()

    fun rotateToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        token = generated
        return generated
    }

    fun isAuthorized(header: String?): Boolean {
        val expected = token
        if (header.isNullOrBlank() || expected.isEmpty()) return false
        val presented = header.removePrefix("Bearer ").trim()
        return MessageDigestSupport.constantTimeEquals(expected, presented)
    }

    companion object {
        const val DEFAULT_PORT = 8790
        private const val KEY_TOKEN = "bridge_token"
        private const val KEY_PORT = "bridge_port"
        private const val KEY_ENABLED = "bridge_enabled"
    }
}

internal object MessageDigestSupport {
    /** Comparaison à temps constant : un token ne doit pas fuiter par timing. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray()
        val right = b.toByteArray()
        if (left.size != right.size) return false
        var diff = 0
        for (i in left.indices) diff = diff or (left[i].toInt() xor right[i].toInt())
        return diff == 0
    }
}
