package io.github.tournierjc.mercury.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un garde-fou sur une panne de mise en page que le compilateur ne voit pas.
 *
 * `FilterChip` dessine ses pastilles dans une `Row` qui défile horizontalement, donc mesurée sans
 * largeur maximale. Un enfant pondéré (`Modifier.weight`) y est mesuré contre l'infini et tombe à
 * zéro : les cinq pastilles de filtre se sont retrouvées sans texte, pastilles vides, pendant deux
 * versions, sans qu'aucun test ni build ne bronche. Le poids est donc interdit ici, et un plafond
 * (`widthIn`) fait le travail de troncature.
 *
 * Ce test lit la source parce que la seule preuve possible de cette panne est un rendu, et qu'il n'y
 * a pas d'émulateur sur cette machine : il vérifie au moins que la cause ne revient pas.
 */
class FilterChipLayoutTest {

    private val source: String by lazy {
        val candidates = listOf(
            File("src/main/java/io/github/tournierjc/mercury/ui/Deliverables.kt"),
            File("app/src/main/java/io/github/tournierjc/mercury/ui/Deliverables.kt"),
        )
        candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Deliverables.kt introuvable depuis ${File(".").absolutePath}")
    }

    private val chip: String by lazy {
        val start = source.indexOf("private fun FilterChip(")
        assertTrue("FilterChip a disparu de Deliverables.kt", start >= 0)
        source.substring(start, source.length)
            .let { it.substring(0, it.indexOf("\nprivate fun ", 20).takeIf { end -> end > 0 } ?: it.length) }
    }

    @Test
    fun `le libelle d'une pastille n'est jamais pondere`() {
        assertFalse(
            "Un Modifier.weight dans FilterChip le mesure contre l'infini : la pastille s'affiche vide.",
            Regex("""Modifier\s*\.\s*weight\s*\(""").containsMatchIn(chip),
        )
    }

    @Test
    fun `le libelle est borne pour qu'un nom long se tronque au lieu de pousser la rangee`() {
        assertTrue("FilterChip doit borner son libelle avec widthIn.", "widthIn(" in chip)
        assertTrue("FilterChip doit tronquer avec une ellipse.", "TextOverflow.Ellipsis" in chip)
    }
}
