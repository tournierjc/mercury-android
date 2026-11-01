package io.github.tournierjc.mercury.device

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Adresse à donner à l'agent : de préférence l'IP Tailscale (100.64.0.0/10),
 * sinon la première IPv4 privée de l'appareil.
 */
object BridgeAddress {

    fun localAddress(): String? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrNull()
            ?: return null
        val addresses = candidates
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
        return addresses.firstOrNull(::isTailnet) ?: addresses.firstOrNull()
    }

    fun url(port: Int, unknownHost: String = "0.0.0.0"): String {
        val host = localAddress() ?: unknownHost
        return "http://$host:$port"
    }

    /** Le tailnet est-il joignable ? Décide du mode du pont (rebind au démarrage du service). */
    fun hasTailnet(): Boolean {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrNull()
            ?: return false
        return candidates
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .any { isTailnetAddress(it.hostAddress ?: "") }
    }

    private fun isTailnet(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    /** Tailscale en IPv4 (100.64.0.0/10, CGNAT) ou en IPv6 (ULA fd7a:115c:a1e0::/48). */
    internal fun isTailnetAddress(address: String): Boolean {
        if (address.contains(':')) {
            val hextets = address.split(':').filter { it.isNotBlank() }
            if (hextets.size < 3) return false
            val prefix = hextets.take(3).joinToString(":")
            return prefix.equals("fd7a:115c:a1e0", ignoreCase = true)
        }
        return isTailnet(address)
    }
}

/**
 * Décide si le paramètre `?token=` est acceptable pour une requête donnée.
 *
 * Un token dans une URL finit dans les journaux de n'importe quel relais : il n'est
 * toléré que parce que `fetch_url` de Mercury n'envoie pas d'en-têtes. Dès qu'un
 * tailnet existe, seules ses adresses (et la boucle locale) peuvent l'utiliser ;
 * sans tailnet, le pont écoute sur le réseau local et le paramètre reste accepté
 * pour ne pas casser les fetches de l'agent — mode annoncé non pris en charge.
 */
object BridgeAccess {

    fun queryTokenAllowed(remoteAddress: String?, tailnetPresent: Boolean): Boolean {
        if (remoteAddress.isNullOrBlank()) return false
        if (!tailnetPresent) return true
        return remoteAddress == "127.0.0.1" ||
            remoteAddress == "::1" ||
            remoteAddress == "0:0:0:0:0:0:0:1" ||
            BridgeAddress.isTailnetAddress(remoteAddress)
    }
}
