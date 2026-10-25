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
import io.github.tournierjc.mercury.data.dto.UsageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val models: ModelsResponse? = null,
    val settings: ChatSettings? = null,
    val usage: UsageDto? = null,
    val providers: List<ProviderDto> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
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
                _state.update { it.copy(busy = false, message = "Modèle actif : $model") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.readable()) }
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
                        message = if (enabled) "Outils approuvés automatiquement" else "Demande d'approbation rétablie",
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
                .onSuccess { _state.update { it.copy(message = "Libellé enregistré") }; refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    /** The caller keeps the cleartext in the text field; it never enters state. */
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.changePassword(currentPassword, newPassword) }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = "Mot de passe modifié") } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun changeUsername(currentPassword: String, newUsername: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.changeUsername(currentPassword, newUsername) }
                .onSuccess {
                    container.lastUsername = newUsername
                    _state.update { s -> s.copy(busy = false, message = "Identifiant modifié") }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun logoutServer() {
        viewModelScope.launch {
            runCatching { api.logout() }
            _state.update { it.copy(message = "Session révoquée côté serveur") }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun Throwable.readable(): String = when (this) {
        is AuthException -> "Session expirée — reconnecte-toi"
        else -> message ?: "Requête refusée"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.api, container) }
        }
    }
}
