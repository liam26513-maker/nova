package it.manlio.nova

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsGuardTest {
    @Test
    fun `broken only when AdGuard is in use, the network has internet, and Android cannot validate it`() {
        assertTrue(DnsGuardRules.broken("dns.adguard.com", hasInternet = true, validated = false))
        assertFalse(DnsGuardRules.broken("dns.adguard.com", hasInternet = true, validated = true))
        assertFalse(DnsGuardRules.broken("dns.google", hasInternet = true, validated = false))
        assertFalse(DnsGuardRules.broken(null, hasInternet = true, validated = false))
        assertFalse(DnsGuardRules.broken("dns.adguard.com", hasInternet = false, validated = false))
    }
}
