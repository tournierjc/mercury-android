package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures follow `getStatusSummaries()` in mercury 1.4.2, not observed traffic. */
class BotJsonTest {

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    private val lead = obj(
        """{"id":"lead-1","name":"Quill","enabled":true,"state":"running","activity":"Drafting chapter 2",
           "needsYou":false,"fleetRole":"lead","crewWorking":2,"lastRunState":"running"}""",
    )
    private val crew = obj("""{"id":"w1","name":"Scribe","enabled":true,"state":"idle","parent":"lead-1","fleetRole":"crew"}""")

    @Test
    fun `roster reads the bots array and ignores the envelope`() {
        val root = obj("""{"bots":[$lead],"queue":{"pending":2,"claimed":1,"dlq":0},"available":true}""")
        val bots = BotJson.roster(root)

        assertEquals(1, bots.size)
        assertEquals("lead-1", BotJson.id(bots[0]))
        assertEquals("Quill", BotJson.name(bots[0]))
        assertEquals("running", BotJson.state(bots[0]))
        assertEquals("Drafting chapter 2", BotJson.activity(bots[0]))
        assertTrue(BotJson.isLead(bots[0]))
        assertEquals(2, BotJson.crewWorking(bots[0]))
    }

    @Test
    fun `missing fields degrade to safe defaults`() {
        val bare = obj("""{"id":"x"}""")
        assertEquals("x", BotJson.name(bare))
        assertEquals("idle", BotJson.state(bare))
        assertEquals("", BotJson.activity(bare))
        assertFalse(BotJson.needsYou(bare))
        assertTrue(BotJson.enabled(bare))
        assertFalse(BotJson.isLead(bare))
    }

    @Test
    fun `state labels are french`() {
        assertEquals(NoticeKey.RUN_RUNNING_STATE, BotJson.stateNotice("running").key)
        assertEquals(NoticeKey.RUN_QUEUED_STATE, BotJson.stateNotice("queued").key)
        assertEquals(NoticeKey.BOT_STATE_PAUSED, BotJson.stateNotice("paused").key)
        assertEquals(NoticeKey.BOT_STATE_DISABLED, BotJson.stateNotice("disabled").key)
        assertEquals(NoticeKey.BOT_STATE_RESTING, BotJson.stateNotice("idle").key)
        // An empty state is the resting one, not an error.
        assertEquals(NoticeKey.BOT_STATE_RESTING, BotJson.stateNotice("").key)
    }

    @Test
    fun `monogram takes initials then falls back to the id`() {
        assertEquals("QU", BotJson.monogram(obj("""{"id":"lead-1","name":"Quill"}""")))
        assertEquals("DT", BotJson.monogram(obj("""{"id":"x","name":"Data Team"}""")))
        assertEquals("XL", BotJson.monogram(obj("""{"id":"x-long-name"}""")))
        assertEquals("?", BotJson.monogram(obj("""{}""")))
        assertEquals("AB", BotJson.monogram(obj("""{"name":"Ab"}""")))
    }

    @Test
    fun `hue is stable and differs between bots`() {
        assertEquals(BotJson.hue(lead), BotJson.hue(lead))
        assertTrue(BotJson.hue(lead) in 0..359)
        assertNotEquals(BotJson.hue(lead), BotJson.hue(crew))
        assertEquals(210, BotJson.hue(obj("""{}""")))
    }

    @Test
    fun `attention raises once for needsYou`() {
        val before = listOf(lead)
        val after = listOf(obj(lead.toString().replace("\"needsYou\":false", "\"needsYou\":true")))

        val raised = BotJson.attention(before, after)
        assertEquals(1, raised.size)
        assertEquals(BotAttention.Kind.NeedsYou, raised[0].kind)
        assertEquals("Quill", raised[0].botName)
        assertTrue(BotJson.attention(after, after).isEmpty())
    }

    @Test
    fun `attention reports finished and failed runs`() {
        val done = BotJson.attention(listOf(lead), listOf(obj(lead.toString().replace("\"running", "\"done"))))
        val failed = BotJson.attention(
            listOf(lead),
            listOf(obj(lead.toString().replace("\"lastRunState\":\"running\"", "\"lastRunState\":\"failed\""))),
        )

        assertEquals(BotAttention.Kind.RunFinished, done.single().kind)
        assertEquals(BotAttention.Kind.RunFailed, failed.single().kind)
    }

    @Test
    fun `a new bot with needsYou is reported, a quiet new bot is not`() {
        val noisy = obj("""{"id":"n1","name":"New","needsYou":true}""")
        val quiet = obj("""{"id":"n2","name":"Quiet"}""")

        assertEquals(1, BotJson.attention(emptyList(), listOf(noisy, quiet)).size)
    }

    // ─── attention sans historique (tour de fond) ───

    @Test
    fun `currentAttention only reports a bot waiting on the owner`() {
        val roster = rosterOf(
            """{"id":"ceo","name":"CEO","needsYou":true,"activity":"Choisir le plan A ou B"}""",
            """{"id":"dev","name":"Dev","needsYou":false,"lastRunState":"done"}""",
            """{"id":"ops","name":"Ops","needsYou":false,"lastRunState":"failed"}""",
        )

        val alerts = BotJson.currentAttention(roster)

        // Un run terminé la veille ne doit pas être ré-annoncé à chaque vérification.
        assertEquals(1, alerts.size)
        assertEquals("ceo", alerts[0].botId)
        assertEquals(BotAttention.Kind.NeedsYou, alerts[0].kind)
        assertEquals("Choisir le plan A ou B", alerts[0].detail)
    }

    @Test
    fun `attentionKey is stable for a pending question and changes with a new one`() {
        val first = BotAttention("ceo", "CEO", BotAttention.Kind.NeedsYou, "Plan A ou B ?")
        val sameAgain = BotAttention("ceo", "CEO", BotAttention.Kind.NeedsYou, "Plan A ou B ?")
        val newQuestion = BotAttention("ceo", "CEO", BotAttention.Kind.NeedsYou, "Quel budget ?")

        assertEquals(BotJson.attentionKey(first), BotJson.attentionKey(sameAgain))
        assertNotEquals(BotJson.attentionKey(first), BotJson.attentionKey(newQuestion))
    }

    @Test
    fun `model is read from the {provider, model} object the daemon stores`() {
        // Le manifeste garde un objet, pas une chaîne : la lecture précédente rendait "" sur
        // cette forme, donc chaque bot s'affichait « défaut » même avec un modèle épinglé.
        val pinned = obj("""{"id":"a","model":{"provider":"deepseek","model":"deepseek-flash"}}""")
        assertEquals("deepseek", BotJson.modelProvider(pinned))
        assertEquals("deepseek-flash", BotJson.modelName(pinned))
        assertEquals("deepseek · deepseek-flash", BotJson.model(pinned))

        val providerOnly = obj("""{"id":"b","model":{"provider":"openaiCompat"}}""")
        assertEquals("openaiCompat", BotJson.modelProvider(providerOnly))
        assertEquals("openaiCompat", BotJson.model(providerOnly))

        // Absent ou null : le bot suit le défaut de l'instance, et rien ne s'affiche.
        assertEquals("", BotJson.modelProvider(obj("""{"id":"c","model":null}""")))
        assertEquals("", BotJson.model(obj("""{"id":"d"}""")))
    }

    private fun rosterOf(vararg bots: String) = bots.map { Json.parseToJsonElement(it).jsonObject }
}
