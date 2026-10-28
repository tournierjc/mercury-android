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

    private fun strings(locale: String, file: String = "strings_notices.xml"): Map<String, String> {
        val resource = File(root, "$locale/$file")
        assertTrue("missing ${resource.path}", resource.exists())
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(resource.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    /**
     * Every `notice_*` string of a locale, wherever it is filed.
     *
     * Notices used to live in one file only. A section that ships its own strings file must not have
     * to duplicate a notice into `strings_notices.xml` to be found here — that is how the same
     * sentence ends up translated twice.
     */
    private fun notices(locale: String): Map<String, String> {
        val dir = File(root, locale)
        val files = dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
        assertTrue("no $locale directory", files != null)
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return files.orEmpty().filter { it.isFile }
            .flatMap { file -> pattern.findAll(file.readText()).map { m -> m.groupValues[1] to m.groupValues[2] } }
            .filter { (key, _) -> key.startsWith("notice_") }
            .toMap()
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+)\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `every key is spelled in both locales`() {
        val fr = notices("values")
        val en = notices("values-en")
        val missing = NoticeKey.entries
            .map { "notice_" + it.name.lowercase() }
            .filterNot { it in fr && it in en }

        assertEquals("keys without a string: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `both locales take the same arguments`() {
        val fr = notices("values")
        val en = notices("values-en")
        val mismatched = fr.keys.filter { key ->
            val other = en[key] ?: return@filter true
            placeholders(fr.getValue(key)) != placeholders(other)
        }

        assertEquals("arguments differ between FR and EN: $mismatched", emptyList<String>(), mismatched)
    }

    @Test
    fun `no notice asks for a number`() {
        // `Notice.args` is a List<String>, so a numeric placeholder throws at format time instead of
        // printing. An oversized attachment hit exactly that — String.format("%2$d", "10") raises
        // IllegalFormatConversionException — before this test existed. The two specifiers are built
        // from characters because a literal "$d" (or "%1$d") in Kotlin is a string template or an
        // escape; here only the resource text is under test.
        val dollar = 36.toChar()
        val numeric = listOf("%" + "d", "%1" + dollar + "d", "%2" + dollar + "d", "%3" + dollar + "d")

        val offenders = listOf("values", "values-en").flatMap { locale ->
            notices(locale)
                .filterValues { text -> numeric.any { it in text } }
                .keys
                .map { "$locale:$it" }
        }

        assertEquals("placeholders expecting an Int: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `no string outlives its key, in either locale`() {
        val known = NoticeKey.entries.map { "notice_" + it.name.lowercase() }.toSet()
        // Checking `values` alone let an orphan that only existed in `values-en` through.
        for (locale in listOf("values", "values-en")) {
            val orphans = notices(locale).keys - known
            assertEquals("strings in $locale with no NoticeKey: $orphans", emptySet<String>(), orphans)
        }
    }

    @Test
    fun `the notification strings are spelled in both locales too`() {
        val fr = strings("values", "strings_notifications.xml")
        val en = strings("values-en", "strings_notifications.xml")

        assertEquals("notification strings only in one locale", fr.keys, en.keys)
        assertTrue("the watch title lost its French", "notification_watch_title" in fr)
        assertTrue("the crew channel lost its English name", "notification_channel_crew" in en)
    }
}
