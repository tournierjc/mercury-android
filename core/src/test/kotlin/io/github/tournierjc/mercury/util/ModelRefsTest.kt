package io.github.tournierjc.mercury.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker ticks exactly one row, and the row above it names exactly one model: both read the
 * pair, so this is the rule they share.
 */
class ModelRefsTest {

    @Test
    fun `provider alone does not identify a model`() {
        assertFalse(
            ModelRefs.same("openaiCompat", "qwen3.8-flash-next", "openaiCompat", "qwen38-flash-next-tf405"),
        )
    }

    @Test
    fun `both halves make the match`() {
        assertTrue(
            ModelRefs.same("deepseek", "deepseek-flash", "deepseek", "deepseek-flash"),
        )
    }

    @Test
    fun `two different providers with the same model id are different choices`() {
        assertFalse(ModelRefs.same("deepseek", "flash", "openaiCompat", "flash"))
    }

    @Test
    fun `nothing chosen matches nothing`() {
        assertFalse(ModelRefs.same(null, null, "deepseek", "deepseek-flash"))
        assertFalse(ModelRefs.same("", "", "", ""))
    }

    @Test
    fun `whitespace is trimmed, not compared`() {
        assertTrue(ModelRefs.same(" deepseek ", " deepseek-flash ", "deepseek", "deepseek-flash"))
    }

    @Test
    fun `label joins the two halves`() {
        assertEquals("deepseek · deepseek-flash", ModelRefs.label("deepseek", "deepseek-flash"))
        assertEquals("deepseek-flash", ModelRefs.label("", "deepseek-flash"))
        assertEquals("deepseek", ModelRefs.label("deepseek", null))
        assertEquals("", ModelRefs.label(null, null))
    }
}
