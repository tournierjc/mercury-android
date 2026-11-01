package io.github.tournierjc.mercury.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieJarTest {

    private val origin = "https://spark.example.ts.net:8443".toHttpUrl()

    private fun cookie(value: String, expiresAt: Long = Long.MAX_VALUE) = Cookie.Builder()
        .name("mercury_session")
        .value(value)
        .hostOnlyDomain("spark.example.ts.net")
        .path("/")
        .expiresAt(expiresAt)
        .build()

    @Test
    fun `saves and reloads the session cookie`() {
        val store = InMemoryCookieStore()
        val jar = SessionCookieJar(store)
        jar.saveFromResponse(origin, listOf(cookie("tok-1")))

        assertTrue(jar.hasSession())

        val restored = SessionCookieJar(store)
        val sent = restored.loadForRequest(origin)
        assertEquals(1, sent.size)
        assertEquals("tok-1", sent[0].value)
        assertTrue(restored.hasSession())
    }

    @Test
    fun `expired cookies are not sent and are dropped`() {
        val store = InMemoryCookieStore()
        val jar = SessionCookieJar(store)
        jar.saveFromResponse(origin, listOf(cookie("old", expiresAt = 1_000L)))
        jar.loadForRequest(origin)

        assertFalse(jar.hasSession())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `hasSessionCookie looks for the named session cookie only`() {
        val jar = SessionCookieJar(InMemoryCookieStore())
        val other = Cookie.Builder()
            .name("other_cookie")
            .value("x")
            .hostOnlyDomain("spark.example.ts.net")
            .path("/")
            .expiresAt(Long.MAX_VALUE)
            .build()
        jar.saveFromResponse(origin, listOf(other))

        assertFalse(jar.hasSessionCookie())
        assertFalse(jar.hasSessionCookie("mercury_session"))

        jar.saveFromResponse(origin, listOf(cookie("tok")))
        assertTrue(jar.hasSessionCookie())
        assertFalse(jar.hasSessionCookie("another"))
    }

    @Test
    fun `cookies for another host are not sent`() {
        val jar = SessionCookieJar(InMemoryCookieStore())
        jar.saveFromResponse(origin, listOf(cookie("tok")))
        assertTrue(jar.loadForRequest("https://other.example.ts.net".toHttpUrl()).isEmpty())
    }

    @Test
    fun `clear empties the jar and the store`() {
        val store = InMemoryCookieStore()
        val jar = SessionCookieJar(store)
        jar.saveFromResponse(origin, listOf(cookie("tok")))
        jar.clear()

        assertFalse(jar.hasSession())
        assertTrue(store.load().isEmpty())
    }
}
