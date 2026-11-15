package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressTextTest {
    @Test
    fun foldsAccentsAndCase() {
        assertEquals("gyomro", AddressText.fold("Gyömrő"))
        assertEquals("gyor", AddressText.fold("GYŐR"))
        assertEquals("hatar utca", AddressText.fold("Határ utca"))
        assertEquals("andrassy ut", AddressText.fold("Andrássy út"))
        assertEquals("szekesfehervar", AddressText.fold("Székesfehérvár"))
    }

    @Test
    fun houseNumbersStayDistinct() {
        assertEquals("1/b", AddressText.houseKey("1/B"))
        assertEquals("12a", AddressText.houseKey("12a"))
        assertEquals("12/a", AddressText.houseKey("12/A"))
        assertEquals("58-60", AddressText.houseKey("58-60"))
        assertEquals("10 a", AddressText.houseKey("10 a."))
        assertEquals("27", AddressText.houseKey("27."))
        assertFalse(AddressText.houseKey("12") == AddressText.houseKey("12a"))
        assertFalse(AddressText.houseKey("58-60") == AddressText.houseKey("58"))
    }

    @Test
    fun splitsSettlementStreetAndHouse() {
        val comma = AddressText.candidates("2230 Gyömrő, Határ utca 27.")
        assertEquals("Gyömrő", comma.first().settlement)
        assertEquals("Határ utca", comma.first().street)
        assertEquals("27.", comma.first().house)

        val plain = AddressText.candidates("gyomro hatar utca 27")
        assertTrue(plain.any { it.settlement == "gyomro" && it.street == "hatar utca" && it.house == "27" })

        val reversed = AddressText.candidates("Andrássy út 60, Budapest")
        assertTrue(reversed.any { it.settlement == "Budapest" && it.street == "Andrássy út" && it.house == "60" })

        val spaced = AddressText.candidates("Gyömrő, Csokonai utca 10 a")
        assertTrue(spaced.any { it.house == "10 a" })
    }

    @Test
    fun exactLocalHitSkipsPhotonAndAMissKeepsIt() {
        val hit = LocalLookup.Hit(LocalAddress("Gyömrő", "Kossuth Ferenc utca", "27", "2230", 1, emptyList()))
        assertFalse(HungaryAddresses.shouldUsePhoton(hit))
        assertTrue(HungaryAddresses.shouldUsePhoton(LocalLookup.Miss))
        assertTrue(HungaryAddresses.shouldUsePhoton(LocalLookup.Unavailable))
    }
}
