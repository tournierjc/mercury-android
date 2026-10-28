package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.ChatSettings
import io.github.tournierjc.mercury.data.dto.ChatSettingsUpdate
import io.github.tournierjc.mercury.data.dto.ModelsResponse
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.ProviderUpdateRequest
import io.github.tournierjc.mercury.data.dto.UsageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice

data class SettingsUiState(
    val loading: Boolean = true,
    val models: ModelsResponse? = null,
    val settings: ChatSettings? = null,
    val usage: UsageDto? = null,
    val providers: List<ProviderDto> = emptyList(),
    val providerTest: ProviderTestResult? = null,
    val providerTestFor: String = "",
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
)

/**
 * Model picker, permission mode, budget and account. Everything here maps to a
 * real Mercury route (`/api/chat/models*`, `/api/chat/settings`, `/api/usage`,
 * `/api/auth/...`) — nothing is faked locally.
 */
class SettingsViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val models = api.models()
                val settings = api.settings()
                val usage = runCatching { api.usage() }.getOrNull()
                val providers = runCatching { api.providers() }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        loading = false,
                        models = models,
                        settings = settings,
                        usage = usage,
                        providers = providers,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.readable()) }
            }
        }
    }

    fun selectModel(provider: String, model: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                api.switchModel(provider, model)
                _state.update { it.copy(busy = false, message = Notice(NoticeKey.MODEL_ACTIVE, args = listOf(model))) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.readable()) }
            }
        }
    }

    /** A provider is a setting: its key, its base URL, its model, its on/off switch. */
    fun saveProvider(
        id: String,
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        enabled: Boolean? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                api.saveProvider(
                    id,
                    ProviderUpdateRequest(
                        apiKey = apiKey?.trim()?.ifBlank { null },
                        baseUrl = baseUrl?.trim()?.ifBlank { null },
                        model = model?.trim()?.ifBlank { null },
                        enabled = enabled,
                    ),
                )
            }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.PROVIDER_SAVED, args = listOf(id))) }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun testProvider(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(providerTest = null, providerTestFor = id, error = null) }
            runCatching { api.testProvider(id) }
                .onSuccess { result -> _state.update { it.copy(providerTest = result, providerTestFor = id) } }
                .onFailure { e ->
                    _state.update {
                        // The DTO mirrors the server's own answer: its `error` is server words
                        // (the `detail` of the contract), not a sentence this app writes.
                        it.copy(providerTest = ProviderTestResult(success = false, error = e.message.orEmpty()), providerTestFor = id)
                    }
                }
        }
    }

    fun setAutoApprove(enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val settings = api.updateSettings(ChatSettingsUpdate(bypassPermissions = enabled))
                _state.update {
                    it.copy(
                        busy = false,
                        settings = settings,
                        message = Notice(if (enabled) NoticeKey.BYPASS_APPROVED else NoticeKey.BYPASS_ASKED),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.readable()) }
            }
        }
    }

    fun setRestrictUser(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { api.updateSettings(ChatSettingsUpdate(restrictUser = enabled)) }
                .onSuccess { settings -> _state.update { it.copy(settings = settings) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun renameModel(provider: String, model: String, label: String) {
        viewModelScope.launch {
            runCatching { api.setModelPrefs(provider, model, label = label.ifBlank { null }) }
                .onSuccess { _state.update { it.copy(message = Notice(NoticeKey.LABEL_SAVED)) }; refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    /** The caller keeps the cleartext in the text field; it never enters state. */
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.changePassword(currentPassword, newPassword) }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.PASSWORD_CHANGED)) } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun changeUsername(currentPassword: String, newUsername: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.changeUsername(currentPassword, newUsername) }
                .onSuccess {
                    container.lastUsername = newUsername
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.USERNAME_CHANGED)) }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun logoutServer() {
        viewModelScope.launch {
            runCatching { api.logout() }
            _state.update { it.copy(message = Notice(NoticeKey.SESSION_REVOKED)) }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    /** One mapping for the whole app (see `toNotice`), no local copy of the wording. */
    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.api, container) }
        }
    }
}
