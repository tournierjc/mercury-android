package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The nine Spotify routes. Two shapes matter: `/status` answering 200 with
 * `{available:false}` on a runtime without a client (absence is not an error), and the
 * playback routes refusing with `{error}` — including Spotify's own Premium message,
 * which the card repeats as-is.
 */
class SpotifyApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MercuryApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MercuryApi(OkHttpClient.Builder().build())
        api.restoreBaseUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `status decodes the account and the product`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"available":true,"connected":true,"accountName":"Jean","product":"premium","deviceId":"abc"}""",
            ),
        )

        val status = api.spotifyStatus()

        assertTrue(status.available)
        assertTrue(status.connected)
        assertEquals("Jean", status.accountName)
        assertEquals("premium", status.product)
        assertEquals("/api/spotify/status", server.takeRequest().path)
    }

    @Test
    fun `a runtime without a spotify client is available and unconnected`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"available":false,"connected":false}"""))

        val status = api.spotifyStatus()

        assertFalse(status.available)
        assertFalse(status.connected)
    }

    @Test
    fun `now-playing carries the formatted line through untouched`() = runBlocking {
        val line = "Daft Punk — Instant Crush [████░░░░░░░░░░░░░░░░] 1:23/3:45"
        server.enqueue(MockResponse().setBody("""{"text":${kotlinx.serialization.json.JsonPrimitive(line)}}"""))

        val answer = api.spotifyNowPlaying()

        assertEquals(line, answer.text)
        assertEquals("Daft Punk", SpotifyJson.nowPlaying(answer.text)?.artist)
    }

    @Test
    fun `an unconnected account surfaces the route's own words`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Spotify not connected"}"""))

        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.spotifyNowPlaying() }
        }
        assertEquals("Spotify not connected", error.message)
    }

    @Test
    fun `a free account keeps Spotify's premium refusal verbatim`() {
        val refusal = "Spotify Premium is required for playback control (play, pause, skip, volume, etc.). " +
            "Read-only features like search, playlists, and liked songs work on free accounts."
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody("""{"error":${kotlinx.serialization.json.JsonPrimitive(refusal)}}"""),
        )

        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.spotifyPlay() }
        }
        assertEquals(refusal, error.message)
    }

    @Test
    fun `play and pause are empty posts on their own routes`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        assertTrue(api.spotifyPlay().ok)
        assertTrue(api.spotifyPause().ok)

        val play = server.takeRequest()
        assertEquals("/api/spotify/play", play.path)
        assertEquals("POST", play.method)
        assertEquals("""{}""", play.body.readUtf8())
        assertEquals("/api/spotify/pause", server.takeRequest().path)
    }

    @Test
    fun `next and previous go to their own routes`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.spotifyNext()
        api.spotifyPrevious()

        assertEquals("/api/spotify/next", server.takeRequest().path)
        assertEquals("/api/spotify/previous", server.takeRequest().path)
    }

    @Test
    fun `volume is a number between 0 and 100`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.spotifyVolume(65)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals(65, body["volume"]!!.jsonPrimitive.int)
    }

    @Test
    fun `volume is clamped before it leaves the phone`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.spotifyVolume(140)
        api.spotifyVolume(-3)

        val high = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        val low = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals(100, high["volume"]!!.jsonPrimitive.int)
        assertEquals(0, low["volume"]!!.jsonPrimitive.int)
    }

    @Test
    fun `shuffle sends a boolean state`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.spotifyShuffle(true)
        api.spotifyShuffle(false)

        val on = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        val off = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("true", on["state"]!!.jsonPrimitive.content)
        assertEquals("false", off["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun `devices are read out of the doubly nested body`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"devices":{"devices":[
                     {"id":"abc","name":"MacBook Pro","type":"Computer","is_active":true,"volume_percent":42},
                     {"id":"def","name":"Cuisine","type":"speaker","is_active":false}
                   ]}}""",
            ),
        )

        val devices = api.spotifyDevices()

        assertEquals(2, devices.size)
        assertEquals("abc", SpotifyJson.activeDevice(devices)?.id)
        assertEquals("/api/spotify/devices", server.takeRequest().path)
    }
}
