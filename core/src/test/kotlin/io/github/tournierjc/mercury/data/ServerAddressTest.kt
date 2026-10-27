package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The server address is editable, so every shape an owner can type has an answer: a stored value
 * (normalized) or a reason the dialog can show.
 */
class ServerAddressTest {

    @Test
    fun `a bare host gets https and loses its trailing slash`() {
        assertEquals("https://spark-a286.tail128204.ts.net:8443", ServerAddress.normalize("spark-a286.tail128204.ts.net:8443/"))
        assertEquals("http://192.168.1.10:6181", ServerAddress.normalize(" http://192.168.1.10:6181/ "))
    }

    @Test
    fun `blank input is empty and cannot be applied`() {
        assertEquals(ServerAddress.Problem.Empty, ServerAddress.validate(""))
        assertEquals(ServerAddress.Problem.Empty, ServerAddress.validate("   "))
        assertEquals("", ServerAddress.normalize("  "))
    }

    @Test
    fun `only http and https are accepted as schemes`() {
        assertEquals(ServerAddress.Problem.UnsupportedScheme, ServerAddress.validate("ftp://spark:8443"))
        assertEquals(ServerAddress.Problem.UnsupportedScheme, ServerAddress.validate("ssh://spark"))
        assertNull(ServerAddress.validate("https://spark:8443"))
        assertNull(ServerAddress.validate("http://spark"))
    }

    @Test
    fun `a scheme with no host is refused`() {
        assertEquals(ServerAddress.Problem.MissingHost, ServerAddress.validate("https://"))
        assertEquals(ServerAddress.Problem.MissingHost, ServerAddress.validate("https:///api"))
    }

    @Test
    fun `host with a port, a path or neither is accepted`() {
        assertNull(ServerAddress.validate("spark-a286.tail128204.ts.net:8443"))
        assertNull(ServerAddress.validate("spark"))
        assertNull(ServerAddress.validate("https://spark:8443/mercury"))
        assertNull(ServerAddress.validate("192.168.1.10:6181"))
    }
}
