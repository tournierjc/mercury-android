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

    private fun isTailnet(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }
}
