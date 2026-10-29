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

    @Test
    fun `a written link keeps its label and carries its target`() {
        // The shape a digest writes, from the report that reported it: the label is not the URL,
        // and both the text before and after the link survive.
        val spans = MiniMarkdown.inline("Idealo [idealo.fr](https://www.idealo.fr/x) monte")
        assertEquals(listOf("Idealo ", "idealo.fr", " monte"), spans.map { it.text })
        assertEquals("https://www.idealo.fr/x", spans[1].url)
        assertEquals(null, spans[0].url)
        assertEquals(null, spans[2].url)
    }

    @Test
    fun `a bare url becomes a link and the sentence keeps its full stop`() {
        val spans = MiniMarkdown.inline("Voir https://idealo.fr/offre, merci.")
        assertEquals("Voir ", spans[0].text)
        assertEquals("https://idealo.fr/offre", spans[1].text)
        assertEquals("https://idealo.fr/offre", spans[1].url)
        assertEquals(", merci.", spans.drop(2).joinToString("") { it.text })
    }

    @Test
    fun `a url in backticks stays code`() {
        val spans = MiniMarkdown.inline("`https://idealo.fr/x`")
        assertEquals("https://idealo.fr/x", spans.single().text)
        assertTrue(spans.single().code)
        assertEquals(null, spans.single().url)
    }

    @Test
    fun `a target the browser cannot open stays literal text`() {
        val spans = MiniMarkdown.inline("[document](intent://open#Intent;end)")
        assertEquals("[document](intent://open#Intent;end)", spans.single().text)
        assertEquals(null, spans.single().url)
    }

    @Test
    fun `a link in a bullet is linked like one in a paragraph`() {
        val blocks = MiniMarkdown.parse("- [neuf FE](https://idealo.fr/neuf) ~4 800 €")
        val bullets = blocks.single() as MdBlock.Bullets
        val spans = bullets.items.single()
        // The link opens the line, so it is the first span: no empty plain span before it.
        assertEquals("neuf FE", spans[0].text)
        assertEquals("https://idealo.fr/neuf", spans[0].url)
        assertEquals(" ~4 800 €", spans[1].text)
    }
}
