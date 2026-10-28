package io.github.tournierjc.mercury.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.content.ContentUris
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lecture seule des données de l'appareil, uniquement via les ContentProviders
 * publics d'Android. Aucune donnée n'est mise en cache ni envoyée : chaque
 * requête HTTP refait la lecture au moment où l'agent la demande.
 */
class DeviceReaders(private val context: Context) {

    fun grantedProviders(): List<String> {
        val providers = mutableListOf("status")
        if (has(Manifest.permission.READ_CALENDAR)) providers += "calendar"
        if (has(Manifest.permission.READ_CONTACTS)) providers += "contacts"
        if (has(Manifest.permission.READ_SMS)) providers += "sms"
        if (NotificationStore.isAccessGranted(context)) providers += "notifications"
        return providers
    }

    fun status(): DeviceStatus {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = battery?.isCharging ?: false
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = connectivity?.activeNetwork
        val caps = active?.let { connectivity.getNetworkCapabilities(it) }
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return DeviceStatus(
            batteryPercent = percent,
            charging = charging,
            network = network,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = ZoneId.systemDefault().id,
            now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    fun calendar(fromIso: String?, toIso: String?, limit: Int): CalendarResponse {
        if (!has(Manifest.permission.READ_CALENDAR)) {
            throw BridgeException("READ_CALENDAR: permission not granted")
        }
        val from = parseInstant(fromIso) ?: startOfToday()
        val to = parseInstant(toIso) ?: from.plusSeconds(7 * 24 * 3600)
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from.toEpochMilli())
        ContentUris.appendId(builder, to.toEpochMilli())
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && events.size < limit) {
                events += CalendarEvent(
                    id = cursor.getLong(0).toString(),
                    title = cursor.getString(1).orEmpty(),
                    start = isoFromMillis(cursor.getLong(2)),
                    end = isoFromMillis(cursor.getLong(3)),
                    allDay = cursor.getInt(4) == 1,
                    location = cursor.getString(5)?.takeIf { it.isNotBlank() },
                    calendar = cursor.getString(6)?.takeIf { it.isNotBlank() },
                )
            }
        }
        return CalendarResponse(
            from = isoFromInstant(from),
            to = isoFromInstant(to),
            events = events,
        )
    }

    fun contacts(query: String?, limit: Int): ContactsResponse {
        if (!has(Manifest.permission.READ_CONTACTS)) {
            throw BridgeException("READ_CONTACTS: permission not granted")
        }
        val search = query?.trim().orEmpty()
        val selection = if (search.isEmpty()) null else "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val args = if (search.isEmpty()) null else arrayOf("%$search%")
        val contacts = mutableListOf<Contact>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            selection,
            args,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && contacts.size < limit) {
                val id = cursor.getString(0)
                val name = cursor.getString(1).orEmpty()
                contacts += Contact(
                    id = id,
                    name = name,
                    phones = phoneNumbers(id),
                    emails = emailAddresses(id),
                )
            }
        }
        return ContactsResponse(query = search, contacts = contacts)
    }

    fun sms(limit: Int, address: String?): List<PhoneMessage> {
        if (!has(Manifest.permission.READ_SMS)) {
            throw BridgeException("READ_SMS: permission not granted")
        }
        val messages = mutableListOf<PhoneMessage>()
        val selection = address?.takeIf { it.isNotBlank() }?.let { "${Telephony.Sms.ADDRESS} = ?" }
        val args = address?.takeIf { it.isNotBlank() }?.let { arrayOf(it) }
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            selection,
            args,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && messages.size < limit) {
                messages += PhoneMessage(
                    id = cursor.getLong(0).toString(),
                    source = "sms",
                    app = "sms",
                    sender = cursor.getString(1).orEmpty(),
                    text = cursor.getString(2).orEmpty(),
                    timestamp = isoFromMillis(cursor.getLong(3)),
                )
            }
        }
        return messages
    }

    /** SMS + messages captés par le listener de notifications, du plus récent au plus ancien. */
    fun messages(limit: Int): MessagesResponse {
        val sms = runCatching { sms(limit, null) }.getOrDefault(emptyList())
        val notifications = NotificationStore.snapshot(limit)
        return MessagesResponse(
            messages = (sms + notifications)
                .sortedByDescending { it.timestamp }
                .take(limit),
        )
    }

    private fun phoneNumbers(contactId: String): List<String> {
        val numbers = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { numbers += it }
            }
        }
        return numbers
    }

    private fun emailAddresses(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { emails += it }
            }
        }
        return emails
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun parseInstant(raw: String?): Instant? =
        raw?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { OffsetDateTime.parse(value).toInstant() }
                .recoverCatching { Instant.parse(value) }
                .getOrNull()
        }

    private fun startOfToday(): Instant =
        OffsetDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun isoFromMillis(millis: Long): String = isoFromInstant(Instant.ofEpochMilli(millis))

    private fun isoFromInstant(instant: Instant): String =
        OffsetDateTime.ofInstant(instant, ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

class BridgeException(message: String) : Exception(message)
