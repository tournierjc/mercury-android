package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One person the brain has met, flattened to what a row shows. */
data class PersonUi(
    val id: String = "",
    val name: String = "",
    val relation: String = "",
    val summary: String = "",
)

data class PersonsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val persons: List<PersonUi> = emptyList(),
    val total: Int = 0,
    /** False when the route answered `available: false` — no people route, not no people. */
    val available: Boolean = true,
    val error: Notice? = null,
)

/**
 * The people the brain has tied memories to.
 *
 * The payload is a list of loose objects (Mercury only exposes a person once it has met one and
 * does not document the shape), so the fields are read by name with a fallback, and a person with
 * no name at all is dropped rather than shown as a blank row.
 */
class PersonsViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(PersonsUiState())
    val state: StateFlow<PersonsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val query = _state.value.query
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { api.persons(query = query) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            available = page.available,
                            total = page.total,
                            persons = page.persons.mapNotNull { person -> toPerson(person) },
                            error = null,
                        )
                    }
                }
                .onFailure { failure -> _state.update { it.copy(loading = false, error = failure.toNotice()) } }
        }
    }

    /** Debounced like the memory search: one request per pause, not per key. */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            runCatching { api.persons(query = query) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            available = page.available,
                            total = page.total,
                            persons = page.persons.mapNotNull { person -> toPerson(person) },
                        )
                    }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.toNotice()) } }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(error = null) }
    }

    private fun toPerson(person: JsonObject): PersonUi? {
        val name = person.text("name").ifBlank { person.text("label") }
        if (name.isBlank()) return null
        return PersonUi(
            id = person.text("id"),
            name = name,
            relation = person.text("relation"),
            summary = person.text("summary").ifBlank { person.text("detail") },
        )
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PersonsViewModel(container.api) }
        }
    }
}
