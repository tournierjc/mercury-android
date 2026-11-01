package io.github.tournierjc.mercury.vm

import io.github.tournierjc.mercury.data.MercuryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two glyphs on the Fournisseurs row.
 *
 * That screen drew them greyed with empty lambdas: a pencil and a bolt that answered nothing. They
 * now write and probe, and the two rules that make the answer readable are here — a refusal keeps
 * its banner (the reload that follows a write clears it), and a probe lands under the row it was
 * asked for, never under another.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigureProviderActionsTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MercuryApi
    private val dispatcher = StandardTestDispatcher()

    /** Paths of every request the view model sent, in order. */
    private val seen = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                seen += path
                return when {
                    path.endsWith("/test") -> MockResponse().setBody("""{"success":true,"models":["deepseek-v4.1-flash"]}""")
                    // A key the server refuses is spelled in the body: `mauvaise` is the whole
                    // difference, so each test states its own case instead of counting calls.
                    path.startsWith("/api/providers/") && request.method == "POST" ->
                        if (request.body.readUtf8().contains("mauvaise")) {
                            MockResponse().setResponseCode(400).setBody("""{"error":"clé refusée par le fournisseur"}""")
                        } else {
                            MockResponse().setBody("""{"ok":true,"restartRequired":false}""")
                        }
                    path.startsWith("/api/providers") -> MockResponse().setBody("[]")
                    path.startsWith("/api/schedules") -> MockResponse().setBody("""{"schedules":[],"available":true}""")
                    path.startsWith("/api/usage") -> MockResponse().setBody(
                        """{"dailyUsed":0,"dailyBudget":0,"byProvider":{},"byChannel":{},"requestLog":[]}""",
                    )
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
        api = MercuryApi(OkHttpClient.Builder().build())
        api.restoreBaseUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    /**
     * The view model talks to a real server: the launch itself is queued on the test dispatcher, and
     * the HTTP call runs on OkHttp's own threads, which virtual time cannot wait for. So drain the
     * queue, look at the state, and give the socket a moment — bounded, never an open-ended sleep.
     */
    private fun awaitState(vm: ConfigureViewModel, what: String, predicate: (ConfigureUiState) -> Boolean): ConfigureUiState {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.advanceUntilIdle()
            val state = vm.state.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("$what : état jamais atteint — ${vm.state.value}, requêtes vues $seen")
    }

    @Test
    fun `a probe answers under its own provider`() = runBlocking {
        val vm = ConfigureViewModel(api)
        awaitState(vm, "premier chargement") { !it.loading }

        vm.testProvider("deepseek")
        val state = awaitState(vm, "sonde") { it.providerTest != null }

        assertEquals("deepseek", state.providerTestFor)
        assertEquals(true, state.providerTest?.success)
        assertEquals(listOf("deepseek-v4.1-flash"), state.providerTest?.models)
    }

    @Test
    fun `a saved key reloads the list once`() = runBlocking {
        val vm = ConfigureViewModel(api)
        awaitState(vm, "premier chargement") { !it.loading }
        val before = seen.count { it == "/api/providers" }

        vm.saveProvider("openrouter", apiKey = "sk-or-test", model = "deepseek/deepseek-v4.1-flash")
        // The reload starts after the write returns, so `busy = false` alone would read the count
        // one request too early: wait for the reloaded list itself.
        val state = awaitState(vm, "écriture") {
            !it.busy && !it.loading && seen.count { path -> path == "/api/providers" } == before + 1
        }

        assertEquals(before + 1, seen.count { it == "/api/providers" })
        assertNull(state.error)
    }

    @Test
    fun `a refused key keeps its banner instead of reloading`() = runBlocking {
        val vm = ConfigureViewModel(api)
        awaitState(vm, "premier chargement") { !it.loading }
        val before = seen.count { it == "/api/providers" }

        vm.saveProvider("deepseek", apiKey = "mauvaise")
        val state = awaitState(vm, "refus") { !it.busy }

        assertNotNull(state.error)
        assertEquals(before, seen.count { it == "/api/providers" })
    }
}
