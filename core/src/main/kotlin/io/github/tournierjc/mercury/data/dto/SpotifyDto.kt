package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `src/web/api/spotify.ts` — status, playback and devices.
 *
 * Playback controls answer `{ok:true}`, or `{error}` with a 400 when the account is not
 * connected (`Spotify not connected`) and a 500 with Spotify's own refusal (a free
 * account cannot control playback) — see [io.github.tournierjc.mercury.data.SimpleResponse].
 */

@Serializable
data class SpotifyStatusDto(
    val available: Boolean = false,
    val connected: Boolean = false,
    val accountName: String? = null,
    val product: String? = null,
    val deviceId: String? = null,
)

/**
 * `GET /api/spotify/now-playing` answers one preformatted line, not a track object:
 * `"Artist — Title [██░░] 1:23/3:45"`. [io.github.tournierjc.mercury.data.SpotifyJson]
 * reads it back into fields the card can draw.
 */
@Serializable
data class SpotifyNowPlayingDto(
    val text: String = "",
    val error: String = "",
)

/**
 * `GET /api/spotify/devices` wraps Spotify's own body once more:
 * `{devices: {devices: [{id,name,type,is_active,volume_percent,…}]}}`. The inner shape is
 * kept as [JsonElement] because it is Spotify's schema, not Mercury's.
 */
@Serializable
data class SpotifyDevicesDto(
    val devices: JsonElement? = null,
    val error: String = "",
)
