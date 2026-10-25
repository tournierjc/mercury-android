package io.github.tournierjc.mercury.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMarkdownTest {

    @Test
    fun `plain text becomes one paragraph`() {
        val blocks = MiniMarkdown.parse("Bonjour tout le monde")
        assertEquals(1, blocks.size)
        val paragraph = blocks[0] as MdBlock.Paragraph
        assertEquals("Bonjour tout le monde", paragraph.spans.single().text)
    }

    @Test
    fun `fenced code keeps language and content`() {
        val blocks = MiniMarkdown.parse("Avant\n```kotlin\nval x = 1\nprintln(x)\n```\nAprès")
        assertEquals(3, blocks.size)
        val code = blocks[1] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nprintln(x)", code.content)
    }

    @Test
    fun `bullet list items are separate`() {
        val blocks = MiniMarkdown.parse("- un\n- deux\n- trois")
        val bullets = blocks.single() as MdBlock.Bullets
        assertEquals(3, bullets.items.size)
        assertEquals("deux", bullets.items[1].single().text)
    }

    @Test
    fun `inline bold code and italic`() {
        val spans = MiniMarkdown.inline("a **gras** et `code` puis *italique*")
        assertEquals(listOf("a ", "gras", " et ", "code", " puis ", "italique"), spans.map { it.text })
        assertTrue(spans[1].bold)
        assertTrue(spans[3].code)
        assertTrue(spans[5].italic)
    }

    @Test
    fun `heading levels are capped`() {
        val blocks = MiniMarkdown.parse("#### Titre")
        val heading = blocks.single() as MdBlock.Heading
        assertEquals(3, heading.level)
        assertEquals("Titre", heading.spans.single().text)
    }

    @Test
    fun `unclosed fence keeps the remaining text as code`() {
        val blocks = MiniMarkdown.parse("```\nencore du code")
        val code = blocks.single() as MdBlock.Code
        assertEquals("encore du code", code.content)
    }

    @Test
    fun `blank lines separate paragraphs`() {
        val blocks = MiniMarkdown.parse("un\n\ndeux")
        assertEquals(2, blocks.size)
    }
}
