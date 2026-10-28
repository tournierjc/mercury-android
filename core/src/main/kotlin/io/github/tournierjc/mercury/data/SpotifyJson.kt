package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One track as the daemon's single-line `now-playing` answer describes it. */
data class NowPlaying(
    val artist: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
) {
    /** 0…1 for the progress bar; a track without a known duration stays at 0. */
    val ratio: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** `1:23 / 3:45`, or `1:23` when Spotify reports no duration. */
    val progressLabel: String
        get() = if (durationMs <= 0) clock(positionMs) else "${clock(positionMs)} / ${clock(durationMs)}"
}

/** A playback device, from Spotify's `/me/player/devices` body. */
data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val volume: Int?,
) {
    /** `Ordinateur` — the active badge is drawn separately, so the word does not repeat. */
    val typeNotice: Notice
        get() = deviceTypeNotice(type)
}

/**
 * Reading the Spotify routes.
 *
 * `/now-playing` is a *formatted string*, not JSON: `"Artist — Title [████░░] 1:23/3:45"`,
 * built by `getNowPlayingText()` in the daemon. The card still wants a title, an artist and
 * a progress ratio, so this parses the line back — and returns null rather than a
 * plausible-looking empty track when the daemon says nothing is playing.
 */
object SpotifyJson {

    /** `[████░░░░]` followed by `m:ss/m:ss`, the only part `formatProgressBar` emits. */
    private val PROGRESS = Regex("""\[[^]]*]\s*(\d+):(\d{2})\s*/\s*(\d+):(\d{2})$""")

    private const val DASH = " — "

    /** Null when nothing plays, when the daemon could not read the player, or on blank. */
    fun nowPlaying(text: String?): NowPlaying? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.equals("Nothing playing", ignoreCase = true)) return null
        if (raw.startsWith("Could not fetch", ignoreCase = true)) return null

        val bar = PROGRESS.find(raw)
        val head = if (bar != null) raw.substring(0, bar.range.first).trim() else raw
        val position = bar?.let { clockMs(it.groupValues[1], it.groupValues[2]) } ?: 0L
        val duration = bar?.let { clockMs(it.groupValues[3], it.groupValues[4]) } ?: 0L

        val artist = head.substringBefore(DASH, missingDelimiterValue = "").trim()
        val title = if (artist.isEmpty()) head else head.substringAfter(DASH).trim()
        return NowPlaying(
            artist = artist,
            title = title.ifBlank { "Titre inconnu" },
            positionMs = position,
            durationMs = duration,
        )
    }

    /**
     * The route answers `{devices: <Spotify body>}`, where the body is itself
     * `{devices:[…]}` — the list therefore sits two keys deep. A bare array is accepted too:
     * a runtime that forwards Spotify's body as-is sends that.
     */
    fun devices(node: JsonElement?): List<SpotifyDevice> {
        val array = unwrap(node) ?: return emptyList()
        return array.mapNotNull { entry -> runCatching { entry.jsonObject }.getOrNull() }
            .map { device ->
                SpotifyDevice(
                    id = string(device, "id"),
                    name = string(device, "name"),
                    type = string(device, "type"),
                    active = bool(device, "is_active"),
                    volume = (device["volume_percent"] as? JsonPrimitive)?.intOrNull,
                )
            }
            .filter { it.name.isNotBlank() || it.id.isNotBlank() }
    }

    /** The device Spotify is currently playing on, if any. */
    fun activeDevice(devices: List<SpotifyDevice>): SpotifyDevice? = devices.firstOrNull { it.active }

    fun productLabel(product: String?): String = when (product?.lowercase()) {
        "premium" -> "Premium"
        "free" -> "Gratuit"
        null, "" -> "—"
        else -> product
    }

    private fun clockMs(minutes: String, seconds: String): Long =
        ((minutes.toLongOrNull() ?: 0L) * 60L + (seconds.toLongOrNull() ?: 0L)) * 1000L

    /** `{devices:[…]}`, `{devices:{devices:[…]}}` and a bare array all resolve to a list. */
    private fun unwrap(node: JsonElement?): JsonArray? = when (node) {
        is JsonArray -> node
        is JsonObject -> when (val inner = node["devices"]) {
            is JsonArray -> inner
            is JsonObject -> inner["devices"] as? JsonArray
            else -> null
        }
        else -> null
    }

    private fun string(node: JsonObject, key: String): String =
        (node[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun bool(node: JsonObject, key: String): Boolean =
        (node[key] as? JsonPrimitive)?.booleanOrNull == true
}

/** `1:23` — minutes and two-digit seconds, as Spotify writes them. */
internal fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Spotify's device `type` values, in French. Unknown types stay verbatim. */
internal fun deviceTypeNotice(type: String): Notice = when (type.lowercase()) {
    "computer" -> Notice(NoticeKey.SPOTIFY_COMPUTER)
    "smartphone" -> Notice(NoticeKey.SPOTIFY_PHONE)
    "speaker" -> Notice(NoticeKey.SPOTIFY_SPEAKER)
    "tv" -> Notice(NoticeKey.SPOTIFY_TV)
    "tablet" -> Notice(NoticeKey.SPOTIFY_TABLET)
    "game_console" -> Notice(NoticeKey.SPOTIFY_CONSOLE)
    "castvideo", "castaudio" -> Notice(NoticeKey.SPOTIFY_CAST)
    "automobile" -> Notice(NoticeKey.SPOTIFY_CAR)
    "" -> Notice(NoticeKey.SPOTIFY_DEVICE)
    else -> Notice(NoticeKey.SPOTIFY_OTHER, args = listOf(type))
}
