package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/api/spotify/now-playing` is not JSON about a track: it is one line the daemon formats
 * (`"Artist — Title [██░░] 1:23/3:45"`). The card needs fields back, and must not invent a
 * track when the daemon says nothing is playing.
 */
class SpotifyJsonTest {

    @Test
    fun `a playing line is split into artist, title and progress`() {
        val track = SpotifyJson.nowPlaying("Daft Punk — Instant Crush [████████░░░░░░░░░░░░] 1:23/3:45")

        assertNotNull(track)
        assertEquals("Daft Punk", track!!.artist)
        assertEquals("Instant Crush", track.title)
        assertEquals(83_000L, track.positionMs)
        assertEquals(225_000L, track.durationMs)
        assertEquals("1:23 / 3:45", track.progressLabel)
        assertEquals(0.368f, track.ratio, 0.001f)
    }

    @Test
    fun `a track without a known duration keeps its title and no progress`() {
        val track = SpotifyJson.nowPlaying("Air — La Femme d'Argent")

        assertNotNull(track)
        assertEquals("Air", track!!.artist)
        assertEquals("La Femme d'Argent", track.title)
        assertEquals(0L, track.durationMs)
        assertEquals("0:00", track.progressLabel)
        assertEquals(0f, track.ratio, 0.0001f)
    }

    @Test
    fun `nothing playing is absence, not an empty track`() {
        assertNull(SpotifyJson.nowPlaying("Nothing playing"))
        assertNull(SpotifyJson.nowPlaying("  "))
        assertNull(SpotifyJson.nowPlaying(null))
        assertNull(SpotifyJson.nowPlaying("Could not fetch playback state"))
    }

    @Test
    fun `a title holding a dash keeps its artist on the left`() {
        val track = SpotifyJson.nowPlaying("Moderat — A New Error — Live [██░] 0:30/6:00")

        assertEquals("Moderat", track?.artist)
        assertEquals("A New Error — Live", track?.title)
    }

    @Test
    fun `a device body nested twice is still read`() {
        val node = Json.parseToJsonElement(
            """{"devices":{"devices":[
                 {"id":"abc","name":"MacBook","type":"Computer","is_active":true,"volume_percent":42},
                 {"id":"def","name":"Cuisine","type":"speaker","is_active":false}
               ]}}""",
        )

        val devices = SpotifyJson.devices(node)

        assertEquals(2, devices.size)
        assertEquals("MacBook", devices[0].name)
        assertEquals("Ordinateur · actif", devices[0].label)
        assertEquals(42, devices[0].volume)
        assertNull(devices[1].volume)
        assertEquals("abc", SpotifyJson.activeDevice(devices)?.id)
    }

    @Test
    fun `a bare array and a missing body both decode without throwing`() {
        val bare = Json.parseToJsonElement("""[{"id":"x","name":"TV","type":"tv","is_active":false}]""")
        assertEquals(1, SpotifyJson.devices(bare).size)

        assertTrue(SpotifyJson.devices(null).isEmpty())
        assertTrue(SpotifyJson.devices(JsonPrimitive("nope")).isEmpty())
        assertTrue(SpotifyJson.devices(Json.parseToJsonElement("""{"devices":{}}""")).isEmpty())
    }

    @Test
    fun `a free account is named as such`() {
        assertEquals("Premium", SpotifyJson.productLabel("premium"))
        assertEquals("Gratuit", SpotifyJson.productLabel("free"))
        assertEquals("—", SpotifyJson.productLabel(null))
    }
}
