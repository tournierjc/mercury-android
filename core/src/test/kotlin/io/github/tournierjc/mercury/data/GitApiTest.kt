package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The eleven git routes of `workspace-ide.ts`.
 *
 * What the client owes them: the exact paths and bodies the routes read (`{files:[…]}`,
 * `{message}`, `{branch, create}`, `{branch, setUpstream}`), and the daemon's own failure
 * text — git's stderr arrives as `{error}` and must reach the owner untouched.
 */
class GitApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MercuryApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MercuryApi(OkHttpClient.Builder().build())
        api.restoreBaseUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String): MockResponse = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun `status decodes branch, distance and both sides of the index`() = runBlocking {
        server.enqueue(
            ok(
                """{"branch":"feat/git-spotify","upstream":"origin/feat/git-spotify",
                   "ahead":2,"behind":1,
                   "files":[
                     {"path":"core/MercuryApi.kt","status":"modified","staged":true},
                     {"path":"core/MercuryApi.kt","status":"modified","staged":false},
                     {"path":"docs/notes.md","status":"untracked","staged":false}
                   ],
                   "cwd":"/home/mercury/work"}""",
            ),
        )

        val status = api.gitStatus()

        assertEquals("feat/git-spotify", status.branch)
        assertEquals("origin/feat/git-spotify", status.upstream)
        assertEquals(2, status.ahead)
        assertEquals(1, status.behind)
        assertEquals(3, status.files.size)
        assertEquals(listOf("core/MercuryApi.kt", "docs/notes.md"), GitJson.stageable(status))
        assertEquals(listOf("core/MercuryApi.kt"), GitJson.unstageable(status))
        assertEquals("/api/git/status", server.takeRequest().path)
    }

    @Test
    fun `stage posts the paths as an array under files`() = runBlocking {
        server.enqueue(ok("""{"success":true}"""))

        api.gitStage(listOf("app/Console.kt", "core/GitJson.kt"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/git/stage", request.path)
        val files = (Json.parseToJsonElement(request.body.readUtf8()) as JsonObject)["files"]!!.jsonArray
        assertEquals(
            listOf("app/Console.kt", "core/GitJson.kt"),
            files.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `unstage posts the same shape to its own route`() = runBlocking {
        server.enqueue(ok("""{"success":true}"""))

        api.gitUnstage(listOf("core/GitJson.kt"))

        val request = server.takeRequest()
        assertEquals("/api/git/unstage", request.path)
        val files = (Json.parseToJsonElement(request.body.readUtf8()) as JsonObject)["files"]!!.jsonArray
        assertEquals("core/GitJson.kt", files[0].jsonPrimitive.content)
    }

    @Test
    fun `a refused commit shows git's own words, not an HTTP code`() {
        // The route answers 500 `{error: <err.message>}`, which for execSync is
        // "Command failed: git commit ..." followed by git's stderr.
        val message = "Command failed: git commit -m \"rien\"\nnothing to commit, working tree clean"
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("""{"error":${JsonPrimitive(message)}}"""),
        )

        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.gitCommit("rien") }
        }
        assertEquals(message, error.message)
    }

    @Test
    fun `commit sends only the owner's message`() = runBlocking {
        server.enqueue(ok("""{"success":true,"message":"[main 1a2b3c4] feat: x"}"""))

        val response = api.gitCommit("feat(git): carte de dépôt")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("feat(git): carte de dépôt", body["message"]!!.jsonPrimitive.content)
        // The Co-authored-by trailer is the daemon's business, not the client's.
        assertFalse(body.toString().contains("Co-authored-by"))
        assertTrue(response.success)
    }

    @Test
    fun `push leaves remote and branch to the daemon by default`() = runBlocking {
        server.enqueue(ok("""{"success":true,"message":"Pushed successfully"}"""))

        api.gitPush()

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertTrue(body.isEmpty())
    }

    @Test
    fun `push can name a branch and ask for an upstream`() = runBlocking {
        server.enqueue(ok("""{"success":true,"message":"ok"}"""))

        api.gitPush(branch = "feat/git-spotify", setUpstream = true)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("feat/git-spotify", body["branch"]!!.jsonPrimitive.content)
        assertTrue(body["setUpstream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `pull posts an empty object`() = runBlocking {
        server.enqueue(ok("""{"success":true,"message":"Already up to date."}"""))

        val response = api.gitPull()

        assertEquals("Already up to date.", response.message)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun `checkout sends the branch and only asks for creation when the user does`() = runBlocking {
        server.enqueue(ok("""{"success":true,"message":"Switched to branch 'main'"}"""))

        api.gitCheckout("main")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("main", body["branch"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("create"))
    }

    @Test
    fun `log decodes the pipe-separated commits`() = runBlocking {
        server.enqueue(
            ok(
                """{"commits":[
                     {"hash":"453310f0","short":"453310f","author":"Jean","email":"j@x.fr",
                      "date":"2 hours ago","message":"feat(chat): draw an openui block | while streaming"}
                   ]}""",
            ),
        )

        val commits = api.gitLog(5)

        assertEquals(1, commits.size)
        assertEquals("453310f", commits[0].short)
        assertEquals("Jean", commits[0].author)
        assertEquals("2 hours ago", commits[0].date)
        assertEquals("feat(chat): draw an openui block | while streaming", commits[0].message)
        assertEquals("/api/git/log?count=5", server.takeRequest().path)
    }

    @Test
    fun `diff asks for the side and the file the card clicked`() = runBlocking {
        server.enqueue(ok("""{"diff":"diff --git a/x b/x\n+une ligne"}"""))

        val patch = api.gitDiff(path = "core/GitJson.kt", staged = true)

        assertEquals("diff --git a/x b/x\n+une ligne", patch)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/git/diff", url.encodedPath)
        assertEquals("true", url.queryParameter("staged"))
        assertEquals("core/GitJson.kt", url.queryParameter("file"))
    }

    @Test
    fun `branches decode the current flag and the upstream`() = runBlocking {
        server.enqueue(
            ok(
                """{"branches":[
                     {"name":"main","isCurrent":false,"upstream":"origin/main"},
                     {"name":"feat/git-spotify","isCurrent":true,"upstream":""}
                   ]}""",
            ),
        )

        val branches = api.gitBranches()

        assertEquals(2, branches.size)
        assertEquals("feat/git-spotify", branches.first { it.isCurrent }.name)
        assertEquals("origin/main", branches[0].upstream)
    }

    @Test
    fun `generate-commit-message returns the model's text`() = runBlocking {
        server.enqueue(ok("""{"message":"feat(git): carte Git et musique"}"""))

        val message = api.generateCommitMessage()

        assertEquals("feat(git): carte Git et musique", message)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun `an empty generation surfaces the route's refusal`() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"No changes to generate message for"}"""),
        )

        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.generateCommitMessage() }
        }
        assertEquals("No changes to generate message for", error.message)
    }
}
