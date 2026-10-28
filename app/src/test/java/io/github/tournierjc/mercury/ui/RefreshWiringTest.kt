package io.github.tournierjc.mercury.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every screen that offers a refresh must also say when it is working.
 *
 * The bot roster's button re-read the fleet and showed nothing at all; the tasks screen had no
 * button; the workspace's did nothing while the terminal tab was open. None of those three would
 * fail to compile, and none of them is visible in a unit test of any single view model — so the
 * guard is at the source level, where the calls are.
 */
class RefreshWiringTest {

    private val uiDir = File("src/main/java/io/github/tournierjc/mercury/ui")

    /** The arguments of each `ScreenHeader(` call, including its nested calls' parentheses. */
    private fun headerCalls(): List<Pair<String, String>> =
        uiDir.listFiles { f -> f.extension == "kt" }.orEmpty().sorted().flatMap { file ->
            val text = file.readText()
            val out = mutableListOf<Pair<String, String>>()
            var from = text.indexOf("ScreenHeader(")
            while (from >= 0) {
                var i = from + "ScreenHeader(".length - 1
                var depth = 0
                var j = i
                while (j < text.length) {
                    when (text[j]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    j++
                }
                out += file.name to text.substring(i, j + 1)
                from = text.indexOf("ScreenHeader(", j)
            }
            out
        }

    @Test
    fun `the header declares a refreshing state`() {
        // Soundness of the guard itself: without it, an empty scan would pass.
        assertTrue("no ScreenHeader found — wrong working directory?", headerCalls().size >= 10)
        val call = headerCalls().first { it.first == "Common.kt" }.second
        assertTrue("ScreenHeader lost its refreshing parameter", call.contains("refreshing"))
    }

    @Test
    fun `no screen wires a refresh without saying when it spins`() {
        val offenders = headerCalls()
            .filter { (_, call) -> call.contains("onRefresh") && !call.contains("refreshing") }
            .map { (name, _) -> name }
            .distinct()

        assertTrue("refresh button without a spinning state: $offenders", offenders.isEmpty())
    }
}
