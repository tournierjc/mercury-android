package io.github.tournierjc.mercury.util

/**
 * What counts as "the same model".
 *
 * The daemon stores a model as `{ provider, model }`, and a provider is not a key: the two Qwen
 * entries both live under `openaiCompat`. Comparing on the provider alone ticked both rows in the
 * picker, so the pair is the identity here — and it is one function because the picker's tick and
 * the row's label have to agree.
 */
object ModelRefs {

    /** True when both halves match. Blank on either side means "no model chosen", not a match. */
    fun same(provider: String?, model: String?, otherProvider: String?, otherModel: String?): Boolean {
        val p = provider?.trim().orEmpty()
        val m = model?.trim().orEmpty()
        val op = otherProvider?.trim().orEmpty()
        val om = otherModel?.trim().orEmpty()
        if (p.isEmpty() && m.isEmpty()) return false
        return p.equals(op, ignoreCase = false) && m.equals(om, ignoreCase = false)
    }

    /** `provider · model`, or the model alone when one half is missing. Empty when there is none. */
    fun label(provider: String?, model: String?): String {
        val p = provider?.trim().orEmpty()
        val m = model?.trim().orEmpty()
        return when {
            p.isEmpty() -> m
            m.isEmpty() -> p
            else -> "$p · $m"
        }
    }
}
