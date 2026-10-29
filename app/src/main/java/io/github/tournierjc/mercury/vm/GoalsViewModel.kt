package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Goals and projects are not their own collection: the webapp asks the memory route for
 * `type=goal` and `type=project`. The two tabs are therefore two filters, not two endpoints.
 */
enum class GoalTab { Goals, Projects }

/**
 * The word the confirmation banner uses.
 *
 * Objectifs and projets live in the memory route, and the screen used to reuse the memory notices:
 * adding a project answered « Souvenir ajouté ». The owner never calls them souvenirs, and a
 * confirmation that names the wrong object reads as a bug in what was just saved.
 */
internal fun noticeForAdd(tab: GoalTab): NoticeKey =
    if (tab == GoalTab.Goals) NoticeKey.GOAL_ADDED else NoticeKey.PROJECT_ADDED

/** Deleting is ambiguous — the same `-` sits on both tabs — so the list the row came from decides. */
internal fun noticeForForget(wasGoal: Boolean): NoticeKey =
    if (wasGoal) NoticeKey.GOAL_FORGOTTEN else NoticeKey.PROJECT_FORGOTTEN

data class GoalsUiState(
    val loading: Boolean = true,
    val tab: GoalTab = GoalTab.Goals,
    val goals: List<MemoryDto> = emptyList(),
    val projects: List<MemoryDto> = emptyList(),
    val busy: Boolean = false,
    val message: Notice? = null,
    val error: Notice? = null,
) {
    /** The list the open tab shows. */
    val shown: List<MemoryDto> get() = if (tab == GoalTab.Goals) goals else projects
}

/**
 * What the agent is trying to do (goals) and what it is building (projects), read from the memory
 * route with the type the webapp uses.
 *
 * Both scopes are asked for: a goal the brain keeps below the threshold is still a goal, and a
 * screen that showed only conscious ones would quietly hide half of them. Adding and deleting go
 * through the same POST/DELETE the memory screen uses; editing the detail stays in the webapp.
 */
class GoalsViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { memoriesOf(GOAL) to memoriesOf(PROJECT) }
                .onSuccess { (goals, projects) ->
                    _state.update { it.copy(loading = false, goals = goals, projects = projects, error = null) }
                }
                .onFailure { failure -> _state.update { it.copy(loading = false, error = failure.toNotice()) } }
        }
    }

    fun selectTab(tab: GoalTab) {
        _state.update { it.copy(tab = tab) }
    }

    /** Adds to the type of the open tab; the summary is the only required field, as in the webapp. */
    fun add(summary: String, detail: String?) {
        if (summary.isBlank()) return
        val goal = _state.value.tab == GoalTab.Goals
        val type = if (goal) GOAL else PROJECT
        val added = noticeForAdd(_state.value.tab)
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.addMemory(type, summary.trim(), detail?.trim()?.ifBlank { null }) }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = Notice(added)) }; refresh() }
                .onFailure { failure -> _state.update { it.copy(busy = false, error = failure.toNotice()) } }
        }
    }

    fun forget(id: String) {
        // Which list holds the row decides the word: the same `-` button deletes an objectif or a
        // projet, and the confirmation belongs to the one that just went away.
        val forgotten = noticeForForget(wasGoal = _state.value.goals.any { it.id == id })
        viewModelScope.launch {
            runCatching { api.forgetMemory(id) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            goals = s.goals.filterNot { it.id == id },
                            projects = s.projects.filterNot { it.id == id },
                            message = Notice(forgotten),
                        )
                    }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.toNotice()) } }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    /** One type across both scopes, deduplicated, biggest first — the order the webapp sorts on. */
    private suspend fun memoriesOf(type: String): List<MemoryDto> {
        val conscious = api.memories(type = type, scope = CONSCIOUS, limit = LIMIT)
        val subconscious = runCatching { api.memories(type = type, scope = SUBCONSCIOUS, limit = LIMIT) }.getOrNull()
        return (conscious.memories + subconscious?.memories.orEmpty())
            .distinctBy { it.id }
            .sortedWith(compareByDescending<MemoryDto> { it.importance }.thenByDescending { it.createdAt })
    }

    companion object {
        private const val GOAL = "goal"
        private const val PROJECT = "project"
        private const val CONSCIOUS = "conscious"
        private const val SUBCONSCIOUS = "subconscious"
        private const val LIMIT = 100

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GoalsViewModel(container.api) }
        }
    }
}
