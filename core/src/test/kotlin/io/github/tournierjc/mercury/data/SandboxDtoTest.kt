package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.ConnectFlowDto
import io.github.tournierjc.mercury.data.dto.InstallRecordDto
import io.github.tournierjc.mercury.data.dto.SandboxToolsPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sandbox tools payloads, captured from `src/web/api/sandbox.ts`: a shipped tool with no
 * sign-in, an installable one, one whose install is blocked, and a device-flow answer.
 */
class SandboxDtoTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val toolsPayload = """
        {"tools":[
          {"id":"gh","label":"GitHub CLI","description":"Issues, PRs, Actions.","present":true,
           "path":"/home/node/.mercury/tools/bin/gh","version":"gh version 2.62.0 (2024-11-14)",
           "auth":"no","authDetail":"You are not logged into any GitHub hosts.","installable":false,"installBlocked":""},
          {"id":"az","label":"Azure CLI","description":"Ressources Azure.","present":false,"path":"","version":"",
           "auth":"no","authDetail":"","installable":true,"installBlocked":""},
          {"id":"ffmpeg","label":"ffmpeg","description":"Requiert root.","present":false,"path":"","version":"",
           "auth":"unknown","authDetail":"","installable":false,
           "installBlocked":"Ce conteneur tourne en uid 1000 sans sudo : apt est impossible ici."}
        ]}
    """

    @Test
    fun `the catalogue keeps presence, version and sign-in state apart`() {
        val page = json.decodeFromString(SandboxToolsPage.serializer(), toolsPayload)
        val gh = page.tools[0]
        val az = page.tools[1]

        assertTrue(gh.present)
        assertFalse(gh.installable)
        assertEquals("no", gh.auth)
        assertTrue(gh.version.startsWith("gh version"))

        assertFalse(az.present)
        assertTrue(az.installable)
    }

    @Test
    fun `a blocked tool carries the reason the screen shows`() {
        val page = json.decodeFromString(SandboxToolsPage.serializer(), toolsPayload)
        val ffmpeg = page.tools[2]

        assertFalse(ffmpeg.installable)
        assertTrue(ffmpeg.installBlocked.contains("uid 1000"))
    }

    @Test
    fun `a device flow keeps the code and the page`() {
        val payload = """{"id":"gh","state":"pending","code":"F5ED-CED0","url":"https://github.com/login/device","startedAt":1793000000000,"detail":""}"""

        val flow = json.decodeFromString(ConnectFlowDto.serializer(), payload)

        assertEquals("F5ED-CED0", flow.code)
        assertEquals("https://github.com/login/device", flow.url)
        assertEquals("pending", flow.state)
    }

    @Test
    fun `an install record without a finish is still running`() {
        val payload = """{"id":"az","startedAt":1793000000000,"state":"running","log":"Collecting azure-cli"}"""

        val record = json.decodeFromString(InstallRecordDto.serializer(), payload)

        assertEquals("running", record.state)
        assertEquals(0, record.finishedAt)
        assertTrue(record.log.isNotBlank())
    }
}
