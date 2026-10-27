package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.data.NoticeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contract behind [noticeResId]: every [NoticeKey] has a string in both locales, with the same
 * positional arguments. A ViewModel cannot see this — it emits keys, not words — so the check lives
 * here, on the resources, and fails before a French sentence can reach an English screen.
 */
class NoticeStringsTest {

    private val root = File("src/main/res")

    private fun strings(locale: String): Map<String, String> {
        val file = File(root, "$locale/strings_notices.xml")
        assertTrue("missing ${file.path}", file.exists())
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+)\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `every key is spelled in both locales`() {
        val fr = strings("values")
        val en = strings("values-en")
        val missing = NoticeKey.entries
            .map { "notice_" + it.name.lowercase() }
            .filterNot { it in fr && it in en }

        assertEquals("keys without a string: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `both locales take the same arguments`() {
        val fr = strings("values")
        val en = strings("values-en")
        val mismatched = fr.keys.filter { key ->
            val other = en[key] ?: return@filter true
            placeholders(fr.getValue(key)) != placeholders(other)
        }

        assertEquals("arguments differ between FR and EN: $mismatched", emptyList<String>(), mismatched)
    }

    @Test
    fun `no string outlives its key`() {
        val known = NoticeKey.entries.map { "notice_" + it.name.lowercase() }.toSet()
        assertEquals("strings with no NoticeKey: ${known}", known, strings("values").keys)
    }
}
