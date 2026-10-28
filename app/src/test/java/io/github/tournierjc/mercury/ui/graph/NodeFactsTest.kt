package io.github.tournierjc.mercury.ui.graph

import io.github.tournierjc.mercury.data.dto.MemoryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The node panel showed the description twice: the graph's `fullLabel` is the memory's summary, and
 * the panel printed the label as its title then the summary as its body. These tests pin the two
 * halves of the fix — the body no longer repeats the title, and the facts the web app shows are
 * carried through instead of being reduced to a sentence and a weight.
 */
class NodeFactsTest {

    private val memory = MemoryDto(
        id = "134fa737-0a3e-498a-8ab7-4432a5042075",
        type = "preference",
        summary = "Writes in French and expects replies in French.",
        detail = null,
        scope = "durable",
        source = "conversation",
        confidence = 0.85,
        importance = 0.9,
        evidenceCount = 5,
        createdAt = 1_793_148_728_902,
        updatedAt = 1_793_200_080_099,
        lastSeenAt = 1_793_200_080_099,
    )

    @Test
    fun `a body identical to the title is not printed again`() {
        assertEquals(
            emptyList<String>(),
            nodeBody(memory.summary, memory.summary, memory.detail),
        )
    }

    @Test
    fun `spacing, case and trailing punctuation do not make a second sentence`() {
        assertEquals(
            emptyList<String>(),
            nodeBody("Writes in French and expects replies in French", "  writes in FRENCH and   expects replies in French. ", null),
        )
    }

    @Test
    fun `a detail that says something else is kept, under the summary`() {
        assertEquals(
            listOf("Summary line.", "Detail line."),
            nodeBody("Title", "Summary line.", "Detail line."),
        )
    }

    @Test
    fun `a detail repeating the summary is dropped`() {
        assertEquals(
            listOf("Summary line."),
            nodeBody("Title", "Summary line.", "Summary line."),
        )
    }

    @Test
    fun `weights become percentages, and a zero means the server said nothing`() {
        val facts = factsOf(memory, connections = 3)

        assertEquals(90, facts.importancePercent)
        assertEquals(85, facts.confidencePercent)
        assertEquals(5, facts.evidence)
        assertEquals(3, facts.connections)
        assertEquals("durable", facts.scope)
        assertEquals("conversation", facts.source)
        assertEquals(1_793_148_728_902, facts.created)
        assertEquals(memory.id, facts.id)
    }

    @Test
    fun `missing values stay missing rather than reading as zero`() {
        val empty = MemoryDto(id = "x")

        val facts = factsOf(empty, connections = -1)

        assertNull(facts.importancePercent)
        assertNull(facts.confidencePercent)
        assertNull(facts.evidence)
        assertNull(facts.scope)
        assertNull(facts.source)
        assertNull(facts.created)
        assertNull(facts.updated)
        assertNull(facts.lastSeen)
        // A negative degree is not a fact either.
        assertEquals(0, facts.connections)
    }
}
