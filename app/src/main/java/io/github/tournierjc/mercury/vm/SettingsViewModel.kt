package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.InstanceDraft
import io.github.tournierjc.mercury.data.InstancePatch
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.ChatSettings
import io.github.tournierjc.mercury.data.dto.ChatSettingsUpdate
import io.github.tournierjc.mercury.data.dto.InstanceConfig
import io.github.tournierjc.mercury.data.dto.ModelsResponse
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.ProviderRoutingDto
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
    /** The instance's identity, default provider and global budget (`GET /api/config`). */
    val instance: InstanceConfig? = null,
    val providerTest: ProviderTestResult? = null,
    val providerTestFor: String = "",
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
) {
    /**
     * The provider keys the "provider par défaut" list can offer: the console's provider rows and the
     * chat picker name the same six, but a daemon may answer only one of the two routes.
     */
    val providerNames: List<String>
        get() = (providers.map { it.name } + models?.providers.orEmpty().map { it.name })
            .filter { it.isNotBlank() }
            .distinct()
}

/**
 * Model picker, permission mode, budget, the instance's own settings and the account. Everything
 * here maps to a real Mercury route (`/api/chat/models*`, `/api/chat/settings`, `/api/usage`,
 * `/api/config`, `/api/auth/...`) — nothing is faked locally.
 */
class SettingsViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val providerEditor = ProviderEditor(api)

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
                // Best-effort: a daemon that predates the route still shows its model, budget and
                // account sections instead of failing the whole screen for one missing read.
                val instance = runCatching { api.instanceConfig() }.getOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        models = models,
                        settings = settings,
                        usage = usage,
                        providers = providers,
                        instance = instance,
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
        routing: ProviderRoutingDto? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            providerEditor.save(id, apiKey, baseUrl, model, enabled, routing)
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
            val result = providerEditor.test(id)
            _state.update { it.copy(providerTest = result, providerTestFor = id) }
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

    /**
     * Identity, default provider and global budget, as one route: `PUT /api/config`.
     *
     * The patch carries only what differs from the daemon's copy (see `InstancePatch`), so a form
     * submitted without an edit sends nothing at all. The answer's `restartRequired` decides the
     * notice: the daemon reads the provider and the budget at boot, and telling the owner "saved"
     * alone would let them wait for an effect that needs a restart.
     */
    fun saveInstance(name: String, owner: String, defaultProvider: String, tokenBudget: String) {
        viewModelScope.launch {
            val current = _state.value.instance ?: return@launch
            val patch = InstancePatch.config(
                current,
                InstanceDraft(name = name, owner = owner, defaultProvider = defaultProvider, tokenBudget = tokenBudget),
            )
            if (patch.isEmpty()) {
                // Nothing to write: a PUT of the same values still rewrites config.yaml.
                refresh()
                return@launch
            }
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.saveInstanceConfig(patch) }
                .onSuccess { saved ->
                    _state.update { s ->
                        s.copy(
                            busy = false,
                            message = Notice(
                                if (saved.restartRequired) NoticeKey.INSTANCE_RESTART_REQUIRED else NoticeKey.INSTANCE_SAVED,
                            ),
                        )
                    }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /**
     * The workspace is a chat setting, not an instance one: `PUT /api/chat/settings`.
     *
     * The notice quotes the folder the daemon answered with rather than the typed text — a host that
     * resolves the path (`~`, a symlink) would otherwise leave the app showing a path nobody uses.
     */
    fun saveWorkspace(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.saveWorkspace(path) }
                .onSuccess { settings ->
                    _state.update { s ->
                        s.copy(
                            busy = false,
                            settings = settings,
                            message = Notice(NoticeKey.WORKSPACE_SET, args = listOf(settings.workspace)),
                        )
                    }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
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
