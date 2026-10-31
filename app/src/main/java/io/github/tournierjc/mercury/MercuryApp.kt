package io.github.tournierjc.mercury

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.tournierjc.mercury.bots.BotOrderMemory
import io.github.tournierjc.mercury.bots.BotNotifyMemory
import io.github.tournierjc.mercury.bots.BotNotifier
import io.github.tournierjc.mercury.bots.BotWatcher
import io.github.tournierjc.mercury.data.CookieStore
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.SessionCookieJar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Manual dependency container: one HTTP client, one cookie jar, one API.
 * Kept tiny on purpose — the app has a single screen graph.
 */
class AppContainer(context: Context) {

    private val prefs = context.getSharedPreferences("mercury", Context.MODE_PRIVATE)

    private val securePrefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "mercury_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { context.getSharedPreferences("mercury_secure_plain", Context.MODE_PRIVATE) }

    private val cookieStore = object : CookieStore {
        override fun load(): List<String> =
            securePrefs.getString(COOKIES_KEY, null)
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?: emptyList()

        override fun save(lines: List<String>) {
            securePrefs.edit().putString(COOKIES_KEY, lines.joinToString("\n")).apply()
        }
    }

    val cookieJar: SessionCookieJar = SessionCookieJar(cookieStore)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE: no read timeout
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val api: MercuryApi = MercuryApi(httpClient, cookieJar)

    /** Process-wide scope for the bot event stream (roster + notifications). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val botNotifier = BotNotifier(context.applicationContext)

    /** Who the owner has spoken to — a conversation is a notification subscription. */
    val botNotifyMemory = BotNotifyMemory(context.applicationContext)

    /** The order the owner arranged the crew in; empty until he does. */
    val botOrderMemory = BotOrderMemory(context.applicationContext)

    val botWatcher = BotWatcher(api, botNotifier, appScope, botNotifyMemory)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value).apply()
        }

    var lastUsername: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_USERNAME, value).apply()
        }

    init {
        api.restoreBaseUrl(serverUrl)
    }

    private companion object {
        const val COOKIES_KEY = "cookies"
        const val KEY_SERVER_URL = "serverUrl"
        const val KEY_USERNAME = "username"
    }
}

class MercuryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.botWatcher.start()
        // La vérification de fond survit au process : WorkManager la reprend au boot.
        io.github.tournierjc.mercury.bots.AttentionSchedule.apply(this)
    }
}
