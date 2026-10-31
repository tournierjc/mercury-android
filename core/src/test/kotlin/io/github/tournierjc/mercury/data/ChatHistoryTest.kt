package io.github.tournierjc.mercury.data

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The stored thread, as the chat list draws it (SPECS/115 §3.4).
 *
 * The point of this file: the hour must survive the trip from the wire (`timestamp`, an ISO
 * string) into the bubble model. Asserting a real instant — not merely "at != null" — is what
 * catches a mapping that drops the field or falls back to the current time.
 */
class ChatHistoryTest {

    @Test
    fun `each stored message keeps the moment the daemon dated it`() {
        val built = historyMessages(
            listOf(
                MessageDto(
                    role = "user",
                    content = "et le graphe ?",
                    timestamp = 1_793_242_800_000L,
                ),
                MessageDto(
                    role = "assistant",
                    content = "503, je lis le motif",
                    timestamp = 1_793_242_842_500L,
                ),
            ),
        )

        assertEquals(2, built.size)
        assertEquals(ChatMessage.Role.User, built[0].role)
        assertEquals(Instant.parse("2026-10-29T03:00:00Z"), built[0].at)
        assertEquals(ChatMessage.Role.Assistant, built[1].role)
        assertEquals(Instant.parse("2026-10-29T03:00:42.500Z"), built[1].at)
        // Distinct ids, so a list keyed on them keeps both bubbles.
        assertEquals("history-0", built[0].id)
        assertEquals("history-1", built[1].id)
    }

    @Test
    fun `an undated stored message stays undated instead of borrowing the clock`() {
        val built = historyMessages(listOf(MessageDto(role = "user", content = "hérité")))
        assertNull(built.single().at)
    }

    @Test
    fun `the wire payload's integer timestamp becomes the bubble's hour`() {
        // The shape `GET /api/chat/history` serves (SPECS/115 §5 bis): `timestamp` is an integer,
        // epoch milliseconds — not an ISO string, not a float. The hour must survive decoding.
        val dto = Json.decodeFromString<MessageDto>(
            """{"role":"user","content":"et le graphe ?","timestamp":1793242800000}""",
        )
        assertEquals(Instant.parse("2026-10-29T03:00:00Z"), historyMessages(listOf(dto)).single().at)
    }

    @Test
    fun `a message whose timestamp key is simply absent keeps no hour`() {
        val dto = Json.decodeFromString<MessageDto>("""{"role":"assistant","content":"pas daté"}""")
        assertNull(historyMessages(listOf(dto)).single().at)
    }

    @Test
    fun `a finished turn takes the date of the reply the daemon stored`() {
        val at = listOf(
            MessageDto(role = "user", content = "et le graphe ?", timestamp = 1_793_242_800_000L),
            MessageDto(role = "assistant", content = "503, je lis le motif", timestamp = 1_793_242_842_500L),
        ).lastReplyTime()

        // A real instant, not merely "not null": a fallback on the phone's clock would fail here.
        assertEquals(Instant.parse("2026-10-29T03:00:42.500Z"), at)
    }

    @Test
    fun `a thread still ending with the owner's message yields no date`() {
        // The daemon writes the turn at its end: the answer is not stored yet, so the previous
        // reply's date must not be borrowed for the bubble being drawn.
        val at = listOf(
            MessageDto(role = "user", content = "premier tour", timestamp = 1_793_242_000_000L),
            MessageDto(role = "assistant", content = "première réponse", timestamp = 1_793_242_060_000L),
            MessageDto(role = "user", content = "second tour", timestamp = 1_793_242_800_000L),
        ).lastReplyTime()

        assertNull(at)
    }

    @Test
    fun `a reply stored without a readable timestamp yields no date`() {
        val at = listOf(
            MessageDto(role = "assistant", content = "première réponse", timestamp = 1_793_242_060_000L),
            MessageDto(role = "assistant", content = "réponse non datée", timestamp = 0L),
        ).lastReplyTime()

        // 0 is the daemon's "no date here": no line under the bubble, and above all not the
        // previous reply's hour.
        assertNull(at)
    }
}
