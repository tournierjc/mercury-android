package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests: the payloads below were captured from a live Mercury 1.4.2
 * instance, so a decoding regression shows up here instead of on a phone.
 */
class ContractTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `models response decodes the current model and the picker`() {
        val payload = """
            {"current":{"name":"deepseek","model":"deepseek-flash","label":"DeepSeek Flash","autoLabel":"DeepSeek Flash","customLabel":null,"group":"DeepSeek","reasoning":"auto","reasoningLevels":[]},"providers":[{"name":"deepseek","model":"deepseek-flash","available":true,"isCurrent":true,"label":"DeepSeek Flash","autoLabel":"DeepSeek Flash","customLabel":null,"group":"DeepSeek","reasoning":"auto","reasoningLevels":[]},{"name":"openaiCompat","model":"qwen3.8-flash-next","available":true,"isCurrent":false,"label":"Qwen 3.8 Flash Next","autoLabel":"Qwen 3.8 Flash Next","customLabel":null,"group":"OpenAI-compatible","reasoning":"auto","reasoningLevels":[]}]}
        """.trimIndent()

        val response = json.decodeFromString(ModelsResponse.serializer(), payload)

        assertEquals("deepseek", response.current?.name)
        assertEquals("DeepSeek Flash", response.current?.displayLabel)
        assertEquals(2, response.providers.size)
        assertEquals("qwen3.8-flash-next", response.providers[1].model)
        assertTrue(response.providers[0].isCurrent)
        assertFalse(response.providers[1].isCurrent)
    }

    @Test
    fun `settings expose the derived permission mode`() {
        val payload = """{"bypassPermissions":false,"restrictUser":false,"workspace":"","permissionMode":"ask-me"}"""

        val settings = json.decodeFromString(ChatSettings.serializer(), payload)

        assertFalse(settings.bypassPermissions)
        assertEquals("ask-me", settings.permissionMode)
        assertFalse(settings.autoApproves)
        assertTrue(settings.copy(permissionMode = "allow-all").autoApproves)
    }

    @Test
    fun `usage keeps per-provider and per-channel breakdowns`() {
        val payload = """
            {"dailyUsed":12209,"dailyBudget":200000,"lastResetDate":"2026-10-25","remaining":187791,"requestLog":[{"provider":"deepseek","model":"deepseek-flash","inputTokens":1502,"outputTokens":624,"totalTokens":2126,"channelType":"internal","timestamp":1792946261080}],"byProvider":{"deepseek":12209},"byChannel":{"web":8876,"internal":3333}}
        """.trimIndent()

        val usage = json.decodeFromString(UsageDto.serializer(), payload)

        assertEquals(12209L, usage.dailyUsed)
        assertEquals(187791L, usage.remaining)
        assertEquals(1, usage.requestLog.size)
        assertEquals(2126L, usage.requestLog[0].totalTokens)
        assertEquals(12209L, usage.byProvider["deepseek"])
        assertEquals(0.061f, usage.ratio, 0.001f)
    }

    @Test
    fun `usage with a zero budget never divides by zero`() {
        val usage = UsageDto(dailyUsed = 10, dailyBudget = 0)
        assertEquals(0f, usage.ratio, 0f)
    }

    @Test
    fun `status decodes providers tokens and memory`() {
        val payload = """
            {"running":true,"state":"idle","uptime":3378,"providers":{"deepseek":{"enabled":true,"hasKey":true}},"tokens":{"dailyUsed":12209,"dailyBudget":200000},"memory":{"total":1,"byType":{"preference":1}}}
        """.trimIndent()

        val status = json.decodeFromString(StatusDto.serializer(), payload)

        assertTrue(status.running)
        assertEquals(3378L, status.uptime)
        assertTrue(status.providers.getValue("deepseek").hasKey)
        assertEquals(200000L, status.tokens.dailyBudget)
        assertEquals(1, status.memory.byType["preference"])
    }

    @Test
    fun `health carries loop lag and plugin state`() {
        val payload = """
            {"ok":true,"pid":8,"version":"","state":"idle","booting":false,"loopLag":{"p99Ms":21,"maxMs":21},"plugins":{"safeMode":false,"running":0,"tools":0}}
        """.trimIndent()

        val health = json.decodeFromString(HealthDto.serializer(), payload)

        assertTrue(health.ok)
        assertEquals(21L, health.loopLag.p99Ms)
        assertFalse(health.plugins.safeMode)
    }

    @Test
    fun `skills list keeps the bridge skill and its allowed tools`() {
        val payload = """
            {"skills":[{"name":"phone-bridge","description":"Lire le téléphone Android.","active":true,"version":"0.1.0","allowedTools":["fetch_url"],"hasScripts":false,"hasReferences":false},{"name":"web-search","description":"Search the web.","active":true,"version":"1.3.0","allowedTools":["web_search"],"hasScripts":false,"hasReferences":false}],"total":2}
        """.trimIndent()

        val skills = json.decodeFromString(SkillsResponse.serializer(), payload)

        assertEquals(2, skills.total)
        assertEquals("phone-bridge", skills.skills[0].name)
        assertEquals(listOf("fetch_url"), skills.skills[0].allowedTools)
        assertTrue(skills.skills[0].active)
    }

    @Test
    fun `unknown fields from a newer Mercury are ignored`() {
        val payload = """{"bypassPermissions":true,"permissionMode":"allow-all","futureFlag":"x"}"""
        val settings = json.decodeFromString(ChatSettings.serializer(), payload)
        assertTrue(settings.autoApproves)
    }

    @Test
    fun `code status decodes the programming mode`() {
        val payload = """{"available":true,"state":"off","active":false,"statusText":"Programming mode: Off"}"""
        val code = json.decodeFromString(CodeStatusDto.serializer(), payload)
        assertTrue(code.available)
        assertEquals("off", code.state)
    }

    @Test
    fun `workspace info decodes git metadata`() {
        val payload = """{"cwd":"/work","projectName":"work","projectType":"unknown","isGit":false,"branch":"","remoteUrl":""}"""
        val info = json.decodeFromString(WorkspaceInfoDto.serializer(), payload)
        assertEquals("/work", info.cwd)
        assertFalse(info.isGit)
        assertNull(info.branch.ifBlank { null })
    }

    @Test
    fun `brain status keeps the learned summaries`() {
        val payload = """
            {"total":1,"subconsciousTotal":0,"byType":{"preference":1},"learningPaused":false,"profileSummary":"JC writes in French.","activeSummary":"Reply in French.","available":true,"shareLearning":false,"shareableCount":0}
        """.trimIndent()

        val brain = json.decodeFromString(BrainStatusDto.serializer(), payload)

        assertEquals(1, brain.total)
        assertEquals("Reply in French.", brain.activeSummary)
        assertFalse(brain.learningPaused)
    }

    @Test
    fun `simple response treats ok success and resolved as success`() {
        assertEquals(true, json.decodeFromString(SimpleResponse.serializer(), """{"ok":true,"message":"done"}""").isOk)
        assertEquals(true, json.decodeFromString(SimpleResponse.serializer(), """{"success":true}""").isOk)
        assertEquals(true, json.decodeFromString(SimpleResponse.serializer(), """{"resolved":true}""").isOk)
        assertFalse(json.decodeFromString(SimpleResponse.serializer(), """{"error":"nope"}""").isOk)
    }
}
