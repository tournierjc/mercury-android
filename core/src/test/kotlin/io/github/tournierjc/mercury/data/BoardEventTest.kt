package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frames of `/api/boards/:id/events`. The daemon emits `board-update` with the whole
 * normalised board (normalizeBoard in `src/web/api/kanban.ts`), and `feedback-request`
 * with the request object; keepalive comments never reach the parser.
 */
class BoardEventTest {

    @Test
    fun `a board update carries the whole board and its cards`() {
        val event = BoardEvent.parse(
            "board-update",
            """{"id":"b1","name":"Refonte","status":"active","active":true,""" +
                """"cards":[{"id":"c1","task":"Écrire le parseur","status":"running","progress":"2/5"}]}""",
        ) as BoardEvent.Update

        assertEquals("b1", BoardJson.id(event.board))
        assertEquals(true, BoardJson.boolField(event.board, "active"))
        assertEquals(1, event.cards.size)
        assertEquals("c1", BoardJson.id(event.cards.single()))
        assertEquals("running", BoardJson.status(event.cards.single()))
    }

    @Test
    fun `a board without cards is still a valid update`() {
        val event = BoardEvent.parse("board-update", """{"id":"b9","cards":[]}""") as BoardEvent.Update

        assertTrue(event.cards.isEmpty())
        assertEquals("b9", BoardJson.id(event.board))
    }

    @Test
    fun `a card status change reaches the client as a new list`() {
        val before = BoardEvent.parse(
            "board-update",
            """{"id":"b1","cards":[{"id":"c1","status":"pending"}]}""",
        ) as BoardEvent.Update
        val after = BoardEvent.parse(
            "board-update",
            """{"id":"b1","cards":[{"id":"c1","status":"done","tokensUsed":1200}]}""",
        ) as BoardEvent.Update

        assertEquals("pending", BoardJson.status(before.cards.single()))
        assertEquals("done", BoardJson.status(after.cards.single()))
        assertEquals(1200, BoardJson.intField(after.cards.single(), "tokensUsed"))
    }

    @Test
    fun `a feedback request is the request object itself`() {
        val event = BoardEvent.parse(
            "feedback-request",
            """{"id":"fb_1","boardId":"b1","cardId":"c1","agentId":"dev",""" +
                """"question":"Quel format ?","options":["md","pdf"],"resolved":false}""",
        ) as BoardEvent.FeedbackRequest

        assertEquals("fb_1", BoardJson.id(event.request))
        assertEquals("Quel format ?", (event.request["question"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("c1", (event.request["cardId"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `unknown types and broken payloads never throw`() {
        assertEquals(BoardEvent.Unknown("board-deleted"), BoardEvent.parse("board-deleted", """{"id":"b1"}"""))
        assertEquals(BoardEvent.Unknown("board-update"), BoardEvent.parse("board-update", "not json"))
        assertEquals(BoardEvent.Unknown(""), BoardEvent.parse(null, "{}"))
    }
}
