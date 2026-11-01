package io.github.tournierjc.mercury.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `?token=` escape hatch is the bridge's riskiest path: a token in a URL ends up in
 * any relay's logs. It is only tolerated because `fetch_url` sends no headers, so these
 * tests pin exactly when it is accepted — tailnet/loopback sources once a tailnet exists,
 * anything while the bridge is LAN-bound (no tailnet), and never for a blank source.
 */
class BridgeAccessTest {

    @Test
    fun `query token is accepted from tailnet and loopback sources when a tailnet exists`() {
        assertTrue(BridgeAccess.queryTokenAllowed("100.64.15.9", tailnetPresent = true))
        assertTrue(BridgeAccess.queryTokenAllowed("100.127.255.254", tailnetPresent = true))
        assertTrue(BridgeAccess.queryTokenAllowed("fd7a:115c:a1e0::1234", tailnetPresent = true))
        assertTrue(BridgeAccess.queryTokenAllowed("127.0.0.1", tailnetPresent = true))
        assertTrue(BridgeAccess.queryTokenAllowed("::1", tailnetPresent = true))
        assertTrue(BridgeAccess.queryTokenAllowed("0:0:0:0:0:0:0:1", tailnetPresent = true))
    }

    @Test
    fun `query token is refused from outside the tailnet when a tailnet exists`() {
        assertFalse(BridgeAccess.queryTokenAllowed("192.168.1.50", tailnetPresent = true))
        assertFalse(BridgeAccess.queryTokenAllowed("10.0.0.7", tailnetPresent = true))
        assertFalse(BridgeAccess.queryTokenAllowed("8.8.8.8", tailnetPresent = true))
        assertFalse(BridgeAccess.queryTokenAllowed("100.128.0.1", tailnetPresent = true))
        assertFalse(BridgeAccess.queryTokenAllowed("fd00::1", tailnetPresent = true))
    }

    @Test
    fun `query token stays accepted on a LAN-only install, so fetch_url keeps working`() {
        assertTrue(BridgeAccess.queryTokenAllowed("192.168.1.50", tailnetPresent = false))
        assertTrue(BridgeAccess.queryTokenAllowed("127.0.0.1", tailnetPresent = false))
    }

    @Test
    fun `a request with no readable source address never earns the query token`() {
        assertFalse(BridgeAccess.queryTokenAllowed(null, tailnetPresent = false))
        assertFalse(BridgeAccess.queryTokenAllowed("", tailnetPresent = false))
        assertFalse(BridgeAccess.queryTokenAllowed("  ", tailnetPresent = true))
    }

    @Test
    fun `tailnet detection covers the CGNAT range and the Tailscale IPv6 ULA`() {
        assertTrue(BridgeAddress.isTailnetAddress("100.64.0.1"))
        assertTrue(BridgeAddress.isTailnetAddress("100.127.255.255"))
        assertFalse(BridgeAddress.isTailnetAddress("100.128.0.0"))
        assertFalse(BridgeAddress.isTailnetAddress("100.63.255.255"))
        assertTrue(BridgeAddress.isTailnetAddress("FD7A:115C:A1E0:0::1"))
        assertFalse(BridgeAddress.isTailnetAddress("fd00::1"))
        assertFalse(BridgeAddress.isTailnetAddress("192.168.1.1"))
    }
}
