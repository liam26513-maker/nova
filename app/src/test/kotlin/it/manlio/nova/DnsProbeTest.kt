package it.manlio.nova

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class DnsProbeTest {
    @Test
    fun `a server that answers is reachable, a closed port is not`() {
        val port =
            ServerSocket(0).use { server ->
                assertTrue(dnsReachable("127.0.0.1", server.localPort))
                server.localPort
            }
        assertFalse(dnsReachable("127.0.0.1", port))
    }

    @Test
    fun `a host that does not exist is not reachable`() {
        assertFalse(dnsReachable("nova.invalid"))
    }
}
