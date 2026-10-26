package io.github.tournierjc.mercury.openui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client's half of the contract: parse what the plugin allows, refuse everything
 * else. Each refusal is a test because each one is a path to a code block instead of
 * a half-drawn screen.
 */
class OpenUiTest {

    private fun render(spec: String): OpenUiNode =
        (OpenUi.parse(spec) as OpenUi.Result.Rendered).root

    private fun refused(spec: String): List<String> =
        (OpenUi.parse(spec) as OpenUi.Result.Refused).errors

    @Test
    fun `a table, a chart and a snippet build the tree the renderer expects`() {
        val root = render(
            """
            root = Stack([table, chart, code])
            table = Table({"columns": ["Équipier", "Runs"], "rows": [["CEO", "3"]], "caption": "flotte"})
            chart = Chart({"kind": "bar", "labels": ["Lun", "Mar"], "values": [4, 7]})
            code = Code({"language": "bash", "code": "mercury bots status ceo"})
            """.trimIndent(),
        )

        val stack = root as OpenUiNode.Stack
        assertEquals(3, stack.children.size)

        val table = stack.children[0] as OpenUiNode.Table
        assertEquals(listOf("Équipier", "Runs"), table.columns)
        assertEquals("CEO", table.rows[0][0])
        assertEquals("3", table.rows[0][1])
        assertEquals("flotte", table.caption)

        val chart = stack.children[1] as OpenUiNode.Chart
        assertEquals("bar", chart.kind)
        assertEquals(listOf(4.0, 7.0), chart.values)

        val code = stack.children[2] as OpenUiNode.Snippet
        assertEquals("bash", code.language)
        assertEquals("mercury bots status ceo", code.code)
    }

    @Test
    fun `an absent variant is body, not an error`() {
        val text = render("root = Text({\"text\": \"salut\"})") as OpenUiNode.Paragraph
        assertEquals("salut", text.text)
        assertEquals("body", text.variant)
    }

    @Test
    fun `a component outside the contract is refused, never drawn`() {
        assertTrue(refused("root = Form({\"fields\": []})").first().contains("Form"))
    }

    @Test
    fun `a prop that points at a URL is refused`() {
        assertTrue(refused("root = Card({\"title\": \"x\", \"href\": \"https://example.com\"})").first().contains("href"))
    }

    @Test
    fun `a prop that does not exist on the component is refused`() {
        assertTrue(refused("root = Card({\"title\": \"x\", \"shadow\": \"2\"})").first().contains("shadow"))
    }

    @Test
    fun `a reference to something undefined is refused`() {
        assertTrue(refused("root = Stack([ghost])").first().contains("ghost"))
    }

    @Test
    fun `an orphan node is refused`() {
        assertTrue(refused("""
            root = Stack([a])
            a = Text({"text": "ok"})
            b = Text({"text": "cache-moi"})
        """.trimIndent()).any { it.contains("b") })
    }

    @Test
    fun `a cycle is refused instead of recursing forever`() {
        assertTrue(refused("""
            root = Stack([a])
            a = Stack([root])
        """.trimIndent()).isNotEmpty())
    }

    @Test
    fun `a spec without root is refused`() {
        assertTrue(refused("a = Text({\"text\": \"x\"})").any { it.contains("root") })
    }

    @Test
    fun `chart values must be numbers, because the renderer does arithmetic on them`() {
        assertTrue(refused("root = Chart({\"kind\": \"bar\", \"labels\": [\"a\"], \"values\": [\"12\"]})").isNotEmpty())
        assertTrue(refused("root = Chart({\"kind\": \"bar\", \"labels\": [\"a\"], \"values\": [1, null]})").isNotEmpty())
    }

    @Test
    fun `a chart kind outside bar and line is refused`() {
        assertTrue(refused("root = Chart({\"kind\": \"pie\", \"labels\": [\"a\"], \"values\": [1]})").isNotEmpty())
    }

    @Test
    fun `a snippet longer than the limit is refused`() {
        val code = "x".repeat(4001)
        assertTrue(refused("root = Code({\"code\": \"$code\"})").isNotEmpty())
    }

    @Test
    fun `props that are not a JSON object are refused`() {
        assertTrue(refused("root = Text({text: 'x'})").isNotEmpty())
    }

    @Test
    fun `an empty spec is refused rather than drawn blank`() {
        assertTrue(refused("   ").isNotEmpty())
    }

    @Test
    fun `the fence language is the contract and is the marker the renderer keys on`() {
        assertEquals("openui", OpenUi.FENCE)
    }
}
