package io.github.tournierjc.mercury.device

import android.os.Build
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.KSerializer
import io.github.tournierjc.mercury.BuildConfig

/**
 * Petit serveur HTTP local, **lecture seule**, qui expose les données de
 * l'appareil à l'agent Mercury (qui les appelle depuis le Spark via `fetch_url`).
 *
 * Sécurité :
 *  - tout sauf `/health` (réduit à `{ok:true}`) exige `Authorization: Bearer <token>`
 *    (comparaison à temps constant) ;
 *  - le paramètre `?token=` (nécessaire : `fetch_url` n'envoie pas d'en-têtes) n'est
 *    accepté que depuis une adresse de boucle ou du tailnet ; sans tailnet, le pont
 *    écoute sur le réseau local et le paramètre reste accepté — mode non pris en
 *    charge, annoncé comme tel sur l'écran Pont ;
 *  - aucune écriture, aucune suppression : uniquement des GET ;
 *  - aucune donnée n'est stockée côté serveur, chaque requête relit le provider.
 */
class PhoneBridgeServer(
    private val prefs: BridgePrefs,
    private val readers: DeviceReaders,
    bindHost: String?,
    port: Int,
) : NanoHTTPD(bindHost, port) {

    /** Figé au démarrage : si le tailnet apparaît/disparaît, le pont est relancé (rebind). */
    private val tailnetPresent = BridgeAddress.hasTailnet()

    /** Garde-fou anti-emballement : l'agent ne peut pas marteler le téléphone. */
    private var windowStart = 0L
    private var windowCount = 0
    private val maxRequestsPerMinute = 120

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.trimEnd('/').ifEmpty { "/" }
        return try {
            if (path == "/health" || path == "/device/health") {
                // Sans token, volontairement muet : une sonde ne doit rien apprendre
                // sur l'appareil (version, modèle, providers) à qui scanne le réseau.
                return ok("""{"ok":true}""")
            }
            if (!withinRateLimit()) {
                return json(Response.Status.TOO_MANY_REQUESTS, encode(BridgeError.serializer(), BridgeError("rate limited")))
            }
            if (!isAuthorized(session)) {
                return json(Response.Status.UNAUTHORIZED, encode(BridgeError.serializer(), BridgeError("invalid token")))
            }
            when (path) {
                "/", "/device" -> ok(
                    DeviceJson.json.encodeToString(
                        BridgeInfo.serializer(),
                        BridgeInfo(
                            version = BuildConfig.VERSION_NAME,
                            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                            providers = readers.grantedProviders(),
                        ),
                    ),
                )

                "/device/status" -> ok(encode(DeviceStatus.serializer(), readers.status()))

                "/calendar", "/device/calendar" -> ok(
                    encode(
                        CalendarResponse.serializer(),
                        readers.calendar(
                            fromIso = param(session, "from"),
                            toIso = param(session, "to"),
                            limit = intParam(session, "limit", 50),
                        ),
                    ),
                )

                "/contacts", "/device/contacts" -> ok(
                    encode(
                        ContactsResponse.serializer(),
                        readers.contacts(
                            query = param(session, "q"),
                            limit = intParam(session, "limit", 20),
                        ),
                    ),
                )

                "/sms", "/device/sms" -> ok(
                    encode(
                        MessagesResponse.serializer(),
                        MessagesResponse(
                            readers.sms(
                                limit = intParam(session, "limit", 20),
                                address = param(session, "address"),
                            ),
                        ),
                    ),
                )

                "/messages", "/device/messages" -> ok(
                    encode(
                        MessagesResponse.serializer(),
                        readers.messages(limit = intParam(session, "limit", 20)),
                    ),
                )

                else -> json(Response.Status.NOT_FOUND, encode(BridgeError.serializer(), BridgeError("unknown route")))
            }
        } catch (e: BridgeException) {
            // Corps d'erreur figé : le détail (chemin provider, texte d'exception) reste côté
            // téléphone, il n'y a rien à en faire pour l'agent.
            json(Response.Status.FORBIDDEN, encode(BridgeError.serializer(), BridgeError("refused")))
        } catch (e: Exception) {
            json(
                Response.Status.INTERNAL_ERROR,
                encode(BridgeError.serializer(), BridgeError("internal error")),
            )
        }
    }

    /**
     * Le header `Authorization` est la voie propre. Le paramètre `?token=` existe
     * parce que l'outil `fetch_url` de Mercury n'envoie pas d'en-têtes — mais un
     * token dans une URL finit dans les journaux de n'importe quel relais : il
     * n'est accepté que d'un pair du tailnet (ou de la boucle locale). Sans
     * tailnet, le pont écoute sur le réseau local et le paramètre reste accepté,
     * faute de quoi `fetch_url` cesserait de fonctionner ; ce mode est explicitement
     * non pris en charge (voir l'écran Pont) et le token se révoque en un tap.
     */
    private fun isAuthorized(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: session.headers["x-mercury-token"]
        if (prefs.isAuthorized(header)) return true
        val queryToken = param(session, "token")
        if (queryToken == null || !BridgeAccess.queryTokenAllowed(session.remoteIpAddress, tailnetPresent)) {
            return false
        }
        return prefs.isAuthorized("Bearer $queryToken")
    }

    /** Un seul verrou : les requêtes arrivent sur le pool de threads de NanoHTTPD. */
    @Synchronized
    private fun withinRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - windowStart > 60_000) {
            windowStart = now
            windowCount = 0
        }
        windowCount++
        return windowCount <= maxRequestsPerMinute
    }

    private fun param(session: IHTTPSession, name: String): String? =
        session.parameters[name]?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun intParam(session: IHTTPSession, name: String, fallback: Int): Int {
        val raw = param(session, name) ?: return fallback
        return raw.toIntOrNull()?.coerceIn(1, 200) ?: fallback
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): String =
        DeviceJson.json.encodeToString(serializer, value)

    private fun ok(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)
}
