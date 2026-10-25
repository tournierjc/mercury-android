package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun `parses a single event`() {
        val parser = SseParser()
        val events = parser.feed("event: text_delta\ndata: {\"text\":\"hi\"}\n\n")
        assertEquals(1, events.size)
        assertEquals("text_delta", events[0].type)
        assertEquals("{\"text\":\"hi\"}", events[0].data)
    }

    @Test
    fun `does not emit until the blank line arrives`() {
        val parser = SseParser()
        assertTrue(parser.feed("event: text_delta\ndata: {\"text\":\"hi\"}\n").isEmpty())
        assertEquals(1, parser.feed("\n").size)
    }

    @Test
    fun `handles chunks split inside a line`() {
        val parser = SseParser()
        assertTrue(parser.feed("event: text_del").isEmpty())
        assertTrue(parser.feed("ta\ndata: {\"te").isEmpty())
        val events = parser.feed("xt\":\"ok\"}\n\n")
        assertEquals(1, events.size)
        assertEquals("{\"text\":\"ok\"}", events[0].data)
    }

    @Test
    fun `joins multiple data lines`() {
        val parser = SseParser()
        val events = parser.feed("event: x\ndata: one\ndata: two\n\n")
        assertEquals("one\ntwo", events[0].data)
    }

    @Test
    fun `drops comment keepalives`() {
        val parser = SseParser()
        assertTrue(parser.feed(": keepalive\n\n").isEmpty())
    }

    @Test
    fun `accepts CRLF and CR terminators`() {
        val parser = SseParser()
        val events = parser.feed("event: a\r\ndata: 1\r\n\r\nevent: b\rdata: 2\r\r")
        assertEquals(2, events.size)
        assertEquals("1", events[0].data)
        assertEquals("b", events[1].type)
    }

    @Test
    fun `emits events for several frames in one chunk`() {
        val parser = SseParser()
        val events = parser.feed(
            "event: text_delta\ndata: {\"text\":\"a\"}\n\n" +
                "event: text_done\ndata: {\"fullText\":\"a\"}\n\n",
        )
        assertEquals(listOf("text_delta", "text_done"), events.map { it.type })
    }
}
