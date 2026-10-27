package io.github.tournierjc.mercury.openui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client's half of the contract: parse what the plugin allows, refuse everything
 * else. Each refusal is a test because each one is a path to a code block instead of
 * a half-drawn screen.
 */
class OpenUiTest {

    private fun render(spec: String, partial: Boolean = false): OpenUiNode =
        (OpenUi.parse(spec, partial) as OpenUi.Result.Rendered).root

    private fun refused(spec: String, partial: Boolean = false): List<String> =
        (OpenUi.parse(spec, partial) as OpenUi.Result.Refused).errors

    private fun pending(spec: String): Boolean = OpenUi.parse(spec, partial = true) is OpenUi.Result.Pending

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

    // ---- streaming (`partial = true`) ------------------------------------------------
    //
    // While the answer is being written the fence is a prefix of the spec, so every
    // intermediate text is either drawable or not-yet-drawable — never an error. These
    // tests pin the same rules `openui-contract`'s parser applies to a live stream.

    private val streamedSpec = listOf(
        "root = Stack([title, kpi, steps])",
        "title = Text({\"text\": \"Spark deploy\", \"variant\": \"h1\"})",
        "kpi = Stat({\"label\": \"p95\", \"value\": \"1.9s\", \"delta\": \"-12%\"})",
        "steps = List({\"items\": [\"build\", \"push\", \"verify\"]})",
    ).joinToString("\n")

    @Test
    fun `no prefix of a streamed spec is ever refused`() {
        streamedSpec.indices.forEach { cut ->
            val result = OpenUi.parse(streamedSpec.take(cut), partial = true)
            assertFalse(
                "prefix of $cut chars refused mid-stream: $result",
                result is OpenUi.Result.Refused,
            )
        }
        assertTrue(OpenUi.parse(streamedSpec, partial = true) is OpenUi.Result.Rendered)
    }

    @Test
    fun `the same prefixes are refused once the fence is closed`() {
        // Streaming is a mode, not a loosening of the contract: the closed spec is strict.
        assertTrue(OpenUi.parse(streamedSpec.take(40), partial = false) is OpenUi.Result.Refused)
        assertTrue(OpenUi.parse(streamedSpec, partial = false) is OpenUi.Result.Rendered)
    }

    @Test
    fun `an open fence with nothing in it is pending, not an error`() {
        assertTrue(pending(""))
        assertTrue(pending("   \n "))
        assertTrue(OpenUi.parse("", partial = false) is OpenUi.Result.Refused)
    }

    @Test
    fun `a half-typed last line is dropped and what is complete still draws`() {
        // `kpi` is mid-word: the block shows the title and leaves room for the stat.
        val root = render(
            "root = Stack([title, kpi])\n" +
                "title = Text({\"text\": \"Spark deploy\"})\n" +
                "kpi = Stat({\"label\": \"p9",
            partial = true,
        ) as OpenUiNode.Stack
        assertEquals(1, root.children.size)
        assertEquals("Spark deploy", (root.children.single() as OpenUiNode.Paragraph).text)
    }

    @Test
    fun `a child that has not arrived yet is skipped, not refused`() {
        val root = render("root = Stack([a, b])\na = Text({\"text\": \"ok\"})\n", partial = true) as OpenUiNode.Stack
        assertEquals(1, root.children.size)
        // the same text, closed, is a dangling reference and must be refused
        assertTrue(refused("root = Stack([a, b])\na = Text({\"text\": \"ok\"})\n").isNotEmpty())
    }

    @Test
    fun `a root that has not arrived yet is pending`() {
        assertTrue(pending("title = Text({\"text\": \"Spark deploy\"})\n"))
    }

    @Test
    fun `a broken statement before the last line refuses even while streaming`() {
        val errors = refused(
            "root = Stack([a])\nnot a statement\na = Text({\"text\": \"x\"})\ntail = Tex",
            partial = true,
        )
        assertTrue(errors.toString(), errors.any { it.contains("ligne 2") })
    }

    @Test
    fun `a spec that ends half-written is refused by the parse that closes it`() {
        // What the app does when the stream ends: the same text, parsed closed, is refused
        // rather than drawn half-way.
        assertTrue(OpenUi.parse("root = Stack([a])\na = Text({\"te", partial = false) is OpenUi.Result.Refused)
    }

    @Test
    fun `the closed parse of a complete spec is identical in both modes`() {
        assertEquals(OpenUi.parse(streamedSpec, partial = false), OpenUi.parse(streamedSpec, partial = true))
    }

    @Test
    fun `the node cap holds while streaming`() {
        val ids = (1..61).joinToString(", ") { "n$it" }
        val spec = buildString {
            append("root = Stack([$ids])")
            (1..61).forEach { append("\nn$it = Text({\"text\": \"x$it\"})") }
        }
        assertTrue(refused(spec, partial = true).isNotEmpty())
        assertTrue(refused(spec).isNotEmpty())
    }
}
