package io.github.tournierjc.mercury.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Where the session cookie is kept between launches. */
interface CookieStore {
    fun load(): List<String>
    fun save(lines: List<String>)
}

/**
 * Cookie jar that persists Mercury's `mercury_session` cookie.
 *
 * Mercury's dashboard auth is a plain session cookie (`POST /api/auth/login`
 * answers 302 with `Set-Cookie: mercury_session=…`), so keeping the jar on disk
 * is what makes the app stay signed in across restarts.
 */
class SessionCookieJar(private val store: CookieStore) : CookieJar {

    private val cookies = LinkedHashMap<String, Cookie>()

    init {
        store.load().forEach { line ->
            parseLine(line)?.let { cookies[key(it)] = it }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        cookies.forEach { this.cookies[key(it)] = it }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val valid = cookies.values.filter { it.matches(url) && it.expiresAt > now }
        if (valid.size != cookies.size) {
            cookies.values.removeAll { it.expiresAt <= now }
            persist()
        }
        return valid
    }

    fun clear() {
        cookies.clear()
        persist()
    }

    fun hasSession(): Boolean = cookies.values.any { it.expiresAt > System.currentTimeMillis() }

    /** True when the jar holds a live [name] cookie — Mercury's session is `mercury_session`. */
    fun hasSessionCookie(name: String = "mercury_session"): Boolean =
        cookies.values.any { it.name == name && it.expiresAt > System.currentTimeMillis() }

    private fun key(cookie: Cookie) = "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun persist() {
        store.save(cookies.values.map { serialize(it) })
    }

    private fun serialize(cookie: Cookie): String = listOf(
        cookie.name,
        cookie.value,
        cookie.domain,
        cookie.path,
        cookie.expiresAt.toString(),
        if (cookie.secure) "1" else "0",
        if (cookie.hostOnly) "1" else "0",
    ).joinToString("\t")

    private fun parseLine(line: String): Cookie? {
        val parts = line.split('\t')
        if (parts.size != 7) return null
        return runCatching {
            val builder = Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .path(parts[3])
                .expiresAt(parts[4].toLong())
            if (parts[6] == "1") builder.hostOnlyDomain(parts[2]) else builder.domain(parts[2])
            if (parts[5] == "1") builder.secure()
            builder.build()
        }.getOrNull()
    }
}
