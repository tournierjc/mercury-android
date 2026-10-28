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
import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One person the brain has met, flattened to what a row shows. */
data class PersonUi(
    val id: String = "",
    val name: String = "",
    val relation: String = "",
    val summary: String = "",
)

/** One memory the brain attached to a person. */
data class PersonMemoryUi(
    val type: String = "",
    val summary: String = "",
    val createdAt: String = "",
)

/** What a person's row unfolds into: who they are, and what the brain tied to them. */
data class PersonDetailUi(
    val summary: String = "",
    val traits: List<String> = emptyList(),
    val memories: List<PersonMemoryUi> = emptyList(),
    val total: Int = 0,
)

/**
 * The webapp opens a page per person (`/second-brain/persons/:id`); on a phone the list is where
 * the question is asked, so the row unfolds instead of pushing a screen away.
 *
 * The payload is undocumented loose JSON like the rest of the brain routes, hence the reading by
 * name with fallbacks — and a parser kept pure so the shapes can be tested without a server.
 */
object PersonDetailParser {
    fun parse(person: JsonObject, memories: JsonObject): PersonDetailUi {
        val card = (person["person"] as? JsonObject) ?: person
        val rows = (memories["memories"] as? JsonArray) ?: JsonArray(emptyList())
        val list = rows.mapNotNull { row -> (row as? JsonObject)?.let { memory(it) } }
        val total = (memories["total"] as? JsonPrimitive)?.content?.toIntOrNull() ?: list.size
        return PersonDetailUi(
            summary = card.text("summary").ifBlank { card.text("detail") },
            traits = traitsOf(card),
            memories = list,
            total = maxOf(total, list.size),
        )
    }

    private fun traitsOf(card: JsonObject): List<String> {
        val raw = card["traits"] as? JsonArray ?: return emptyList()
        return raw.mapNotNull { trait ->
            when (trait) {
                is JsonPrimitive -> trait.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                is JsonObject -> listOf("label", "name", "text")
                    .firstNotNullOfOrNull { key -> trait[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } }
                else -> null
            }
        }.distinct()
    }

    private fun memory(row: JsonObject): PersonMemoryUi = PersonMemoryUi(
        type = row.text("type"),
        summary = row.text("summary").ifBlank { row.text("content") }.ifBlank { row.text("statement") },
        createdAt = row.text("createdAt").ifBlank { row.text("created_at") },
    )

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
}

data class PersonsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val persons: List<PersonUi> = emptyList(),
    val total: Int = 0,
    /** False when the route answered `available: false` — no people route, not no people. */
    val available: Boolean = true,
    val error: Notice? = null,
    /** The person whose row is unfolded, empty when the list is flat. */
    val expanded: String = "",
    val detail: PersonDetailUi? = null,
    val detailLoading: Boolean = false,
    val detailFailed: Boolean = false,
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

    /**
     * Unfold a row, or fold it back. The detail is fetched once per unfold and dropped on the way
     * out: a stale copy of someone's memories is worse than a second request.
     */
    fun toggle(person: PersonUi) {
        if (_state.value.expanded == person.id) {
            _state.update { it.copy(expanded = "", detail = null, detailLoading = false, detailFailed = false) }
            return
        }
        _state.update { it.copy(expanded = person.id, detail = null, detailLoading = true, detailFailed = false) }
        viewModelScope.launch {
            runCatching { loadDetail(person.id) }
                .onSuccess { detail ->
                    // The row may have been folded (or another one unfolded) while the request ran.
                    _state.update { if (it.expanded == person.id) it.copy(detailLoading = false, detail = detail) else it }
                }
                .onFailure {
                    _state.update { if (it.expanded == person.id) it.copy(detailLoading = false, detailFailed = true) else it }
                }
        }
    }

    private suspend fun loadDetail(id: String): PersonDetailUi {
        val encoded = URLEncoder.encode(id, "UTF-8")
        val card = api.getJson("/api/brain/persons/$encoded")
        val memories = api.getJson("/api/brain/persons/$encoded/memories?limit=50")
        return PersonDetailParser.parse(card, memories)
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
