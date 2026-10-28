package io.github.tournierjc.mercury.vm

import io.github.tournierjc.mercury.data.dto.ModelRef
import io.github.tournierjc.mercury.data.dto.ModelsResponse
import io.github.tournierjc.mercury.data.dto.ProviderDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "provider par défaut" list, fed by two routes that name the same providers.
 *
 * `/api/providers` is the console's list and `/api/chat/models` the picker's; a daemon may answer
 * only one of them, and the chat catalogue repeats a provider once per model. The setting must offer
 * each key once — a duplicate entry would look like two different providers.
 */
class InstanceProviderChoicesTest {

    private fun provider(name: String) = ProviderDto(name = name)

    private fun model(provider: String, model: String) = ModelRef(name = provider, model = model)

    @Test
    fun `the keys of both routes are offered once each`() {
        val state = SettingsUiState(
            providers = listOf(provider("deepseek"), provider("openrouter")),
            models = ModelsResponse(
                providers = listOf(
                    model("deepseek", "deepseek-v4.1-flash"),
                    model("deepseek", "deepseek-v4.1"),
                    model("openrouter", "deepseek/deepseek-v4.1-flash"),
                    model("ollama", "qwen3"),
                ),
            ),
        )

        assertEquals(listOf("deepseek", "openrouter", "ollama"), state.providerNames)
    }

    @Test
    fun `a daemon that only answers the chat route still offers its provider`() {
        val state = SettingsUiState(
            providers = emptyList(),
            models = ModelsResponse(providers = listOf(model("custom:qwen", "qwen3.8-flash-next"))),
        )

        assertEquals(listOf("custom:qwen"), state.providerNames)
    }

    @Test
    fun `blank names never become a choice`() {
        val state = SettingsUiState(
            providers = listOf(provider(""), provider("deepseek")),
            models = ModelsResponse(providers = listOf(model(" ", "x"))),
        )

        assertEquals(listOf("deepseek"), state.providerNames)
    }
}
