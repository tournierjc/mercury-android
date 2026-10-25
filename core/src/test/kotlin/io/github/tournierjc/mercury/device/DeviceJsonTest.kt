package io.github.tournierjc.mercury.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceJsonTest {

    @Test
    fun `calendar response keeps the contract field names`() {
        val response = CalendarResponse(
            from = "2026-10-25T00:00:00+01:00",
            to = "2026-10-26T00:00:00+01:00",
            events = listOf(
                CalendarEvent(
                    id = "42",
                    title = "Dentiste",
                    start = "2026-10-25T10:00:00+01:00",
                    end = "2026-10-25T10:45:00+01:00",
                    allDay = false,
                    location = "Rue de Rivoli",
                    calendar = "Perso",
                ),
            ),
        )

        val encoded = DeviceJson.json.encodeToString(CalendarResponse.serializer(), response)

        assertTrue(encoded.contains("\"allDay\""))
        assertTrue(encoded.contains("\"Dentiste\""))
        assertTrue(encoded.contains("\"calendar\": \"Perso\""))
        val decoded = DeviceJson.json.decodeFromString(CalendarResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `absent optionals are omitted rather than null`() {
        val event = CalendarEvent(
            id = "1",
            title = "Sans lieu",
            start = "2026-10-25T09:00:00+01:00",
            end = "2026-10-25T09:30:00+01:00",
            allDay = false,
        )
        val encoded = DeviceJson.json.encodeToString(CalendarEvent.serializer(), event)
        assertTrue(!encoded.contains("location"))
    }

    @Test
    fun `messages carry their source`() {
        val message = PhoneMessage(
            id = "n1",
            source = "notification",
            app = "com.whatsapp",
            sender = "Maman",
            text = "Tu viens dimanche ?",
            timestamp = "2026-10-25T11:02:00+01:00",
        )
        val encoded = DeviceJson.json.encodeToString(PhoneMessage.serializer(), message)
        assertTrue(encoded.contains("\"source\": \"notification\""))
        assertEquals(message, DeviceJson.json.decodeFromString(PhoneMessage.serializer(), encoded))
    }

    @Test
    fun `bridge error is a single field`() {
        val encoded = DeviceJson.json.encodeToString(BridgeError.serializer(), BridgeError("token invalide"))
        assertEquals("{\n    \"error\": \"token invalide\"\n}", encoded)
    }
}
