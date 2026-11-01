package io.github.tournierjc.mercury.data

/**
 * Test double for [CookieStore]: lines in memory, nothing on disk.
 *
 * Production builds their store from encrypted prefs (see the app's `MercuryApp`); only the tests
 * need a jar that starts empty and forgets on process exit, so this lives in the test source set.
 */
class InMemoryCookieStore : CookieStore {
    private var lines: List<String> = emptyList()
    override fun load(): List<String> = lines
    override fun save(lines: List<String>) {
        this.lines = lines
    }
}
