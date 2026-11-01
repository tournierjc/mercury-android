package io.github.tournierjc.mercury.vm

import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.ProviderRoutingDto
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.ProviderUpdateRequest

/**
 * The provider edit two screens share: Réglages and Fournisseurs write the same fields through the
 * same route and probe the same row. The request shaping and the probe's failure folding live here
 * once; what stays with each screen is its banner policy — Réglages confirms with a notice,
 * Fournisseurs does not — and when each reloads.
 */
class ProviderEditor(private val api: MercuryApi) {

    /**
     * Writes what the edit sheet collected: a key, an endpoint, a model, the enabled flag, routing.
     * A blank field means "leave the stored value", which is why the trim happens here rather than
     * in whichever screen happened to collect the text.
     */
    suspend fun save(
        id: String,
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        enabled: Boolean? = null,
        routing: ProviderRoutingDto? = null,
    ): Result<Unit> = runCatching {
        api.saveProvider(
            id,
            ProviderUpdateRequest(
                apiKey = apiKey?.trim()?.ifBlank { null },
                baseUrl = baseUrl?.trim()?.ifBlank { null },
                model = model?.trim()?.ifBlank { null },
                enabled = enabled,
                routing = routing,
            ),
        )
    }

    /**
     * Probes one provider. A failure is folded into the DTO the row displays — its `error` is the
     * server's own words (the contract's `detail`), not a sentence this app writes.
     */
    suspend fun test(id: String): ProviderTestResult =
        runCatching { api.testProvider(id) }.getOrElse { e ->
            ProviderTestResult(success = false, error = e.message.orEmpty())
        }
}
