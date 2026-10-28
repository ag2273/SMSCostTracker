package com.example.smscosttracker.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MerchantNamesTest {
    @Test
    fun `english variants share one key`() {
        val keys = listOf("AMAZON.SA", "Amazon SA Riyadh", "AMZN Mktp", "amazon", "POS AMAZON KSA")
            .map { MerchantNames.key(it) }
            .toSet()

        assertEquals(setOf("amazon"), keys)
    }

    @Test
    fun `domains branches and store numbers are dropped`() {
        assertEquals("noon", MerchantNames.key("noon.com"))
        assertEquals("jarir bookstore", MerchantNames.key("JARIR BOOKSTORE 0123 JEDDAH"))
        assertEquals("talabat", MerchantNames.key("Talabat.com.sa"))
    }

    @Test
    fun `arabic spelling variants share one key`() {
        assertEquals(MerchantNames.key("صيدلية النهدي"), MerchantNames.key("صيدليه النهدى - الرياض"))
        assertEquals(MerchantNames.key("أسواق العثيم"), MerchantNames.key("اسواق العثيم"))
    }

    @Test
    fun `a single word that looks like noise is kept`() {
        assertEquals("riyadh", MerchantNames.key("Riyadh"))
    }

    @Test
    fun `groups pick the most used name and absorb one-letter typos`() {
        val groups = MerchantGroups(
            listOf("Carrefour", "Carrefour", "CARREFOUR RIYADH", "Carefour", "Panda", "Pandx")
        )

        assertEquals("Carrefour", groups.displayName("CARREFOUR RIYADH"))
        assertEquals("Carrefour", groups.displayName("Carefour"))
        assertEquals("Panda", groups.displayName("Panda"))
        assertNotEquals("Panda", groups.displayName("Pandx"))
    }

    @Test
    fun `user merges join groups and name the result`() {
        val groups = MerchantGroups(
            names = listOf("STC Pay", "STC Pay", "STCPAY Wallet"),
            aliases = mapOf(MerchantNames.key("STCPAY Wallet") to "STC Pay")
        )

        assertEquals("STC Pay", groups.displayName("STCPAY Wallet"))
        assertEquals("STC Pay", groups.displayNameForKey(MerchantNames.key("STCPAY Wallet")))
    }

    @Test
    fun `unknown merchants are never grouped`() {
        val groups = MerchantGroups(listOf("Unknown", "Unknown"))

        assertEquals("Unknown", groups.displayName("Unknown"))
    }

    @Test
    fun `a bill reference before the payee is ignored but short numbers in names are kept`() {
        assertEquals("mobily postpaid", MerchantNames.key("7016213340; Mobily - Postpaid"))
        assertEquals(MerchantNames.key("Zain - Postpaid"), MerchantNames.key("7013489330; Zain - Postpaid"))
        assertEquals("7 eleven", MerchantNames.key("7-Eleven"))
    }
}
