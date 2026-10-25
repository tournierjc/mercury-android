package io.github.tournierjc.mercury.device

import kotlinx.serialization.Serializable

/**
 * Contrat JSON du pont appareil.
 *
 * Ces DTOs sont dans :core (Kotlin/JVM pur) pour que la forme du contrat soit
 * couverte par des tests unitaires : c'est ce que l'agent Mercury lit à distance,
 * donc le nom des champs est une API, pas un détail d'implémentation.
 */
@Serializable
data class BridgeInfo(
    val ok: Boolean = true,
    val version: String,
    val device: String,
    val providers: List<String> = emptyList(),
)

@Serializable
data class DeviceStatus(
    val batteryPercent: Int,
    val charging: Boolean,
    val network: String,
    val locale: String,
    val timezone: String,
    val now: String,
)

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val allDay: Boolean,
    val location: String? = null,
    val calendar: String? = null,
)

@Serializable
data class CalendarResponse(
    val from: String,
    val to: String,
    val events: List<CalendarEvent> = emptyList(),
)

@Serializable
data class Contact(
    val id: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
)

@Serializable
data class ContactsResponse(
    val query: String,
    val contacts: List<Contact> = emptyList(),
)

/**
 * One message the phone knows about. [source] distinguishes a real SMS from a
 * notification captured for an app without a readable store (WhatsApp, Signal…).
 */
@Serializable
data class PhoneMessage(
    val id: String,
    val source: String,
    val app: String,
    val sender: String,
    val text: String,
    val timestamp: String,
)

@Serializable
data class MessagesResponse(
    val messages: List<PhoneMessage> = emptyList(),
)

@Serializable
data class BridgeError(val error: String)

object DeviceJson {
    val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
}
