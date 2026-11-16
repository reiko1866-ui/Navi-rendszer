package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotonSearchTest {
    @Test
    fun asksForTheSupportedDefaultLanguage() {
        val url = PhotonSearch.requestUrl("Gyömrő Kossuth utca 1")
        assertTrue(url.startsWith("https://photon.komoot.io/api/?"))
        assertTrue(url.contains("lang=default"))
        assertFalse(url.contains("lang=hu"))
        assertTrue(url.contains("q=Gy%C3%B6mr%C5%91+Kossuth+utca+1"))
        assertTrue(url.contains("limit=6"))
    }

    @Test
    fun keepsHungarianPlaceNamesAndAddressLines() {
        val places = PhotonSearch.interpret(200, hungarianBody()).getOrThrow()
        assertEquals("Budapest", places[0].name)
        assertEquals("Magyarország", places[0].detail)
        assertEquals(19.0402383, places[0].lon, 0.0000001)
        assertEquals(47.4978789, places[0].lat, 0.0000001)
        assertEquals("Gyömrő", places[1].name)
        assertEquals("Pest megye, 2230, Magyarország", places[1].detail)
        assertEquals("Kossuth Ferenc utca 1", places[2].name)
        assertEquals("Máriatelep, Gyömrő, 2230, Magyarország", places[2].detail)
    }

    @Test
    fun readsTheHouseNumberWithoutConfusingThePostcode() {
        assertEquals("27", PhotonSearch.requestedHouseNumber("2230 Gyömrő, Határ utca 27."))
        assertEquals("27", PhotonSearch.requestedHouseNumber("Gyömrő Határ utca 27"))
        assertEquals("27", PhotonSearch.requestedHouseNumber("Határ utca 27, Gyömrő"))
        assertEquals(null, PhotonSearch.requestedHouseNumber("Határ utca, Gyömrő"))
    }

    @Test
    fun doesNotTreatAnotherTownOrAnotherStreetAsHatarUtca27() {
        for (query in listOf(
            "Gyömrő Határ utca 27",
            "Határ utca 27, Gyömrő",
            "2230 Gyömrő, Határ utca 27",
            "2230 Gyömrő, Határ utca 27.",
            "Gyomro Hatar utca 27"
        )) {
            val places = PhotonSearch.interpret(200, hatarUtcaBody(), query).getOrThrow()
            assertEquals(query, 2, places.size)
            assertTrue(places.all { it.precision == PlacePrecision.STREET })
            assertTrue(places.all { it.name == "Határ utca" })
            assertTrue(places.all { it.detail.contains(PhotonSearch.streetOnlyNote) })
            assertTrue(places.none { it.detail.contains("Győr") || it.detail.contains("Kossuth") })
            assertEquals(19.3888628, places[0].lon, 0.0000001)
            assertEquals(47.439289, places[0].lat, 0.0000001)
            assertEquals(19.3699805, places[1].lon, 0.0000001)
            assertEquals(47.4382569, places[1].lat, 0.0000001)
        }
    }

    @Test
    fun keepsAnExactHouseWhenTheSourceHasThatNumber() {
        val places = PhotonSearch.interpret(200, hatarUtcaBody(), "Kossuth Ferenc utca 27, Gyömrő").getOrThrow()
        assertEquals(1, places.size)
        assertEquals(PlacePrecision.HOUSE, places[0].precision)
        assertEquals("Kossuth Ferenc utca 27", places[0].name)
        assertFalse(places[0].detail.contains(PhotonSearch.streetOnlyNote))
        assertEquals(19.3912404, places[0].lon, 0.0000001)
        assertEquals(47.4273419, places[0].lat, 0.0000001)
    }

    @Test
    fun leavesAStreetQueryUnlabelledWhenNoHouseNumberWasAsked() {
        val places = PhotonSearch.interpret(200, hatarUtcaBody(), "Határ utca, Gyömrő").getOrThrow()
        assertEquals(4, places.size)
        assertTrue(places.none { it.detail.contains(PhotonSearch.streetOnlyNote) })
    }

    @Test
    fun treatsAnEmptyResultSeparatelyFromANetworkFailure() {
        val empty = PhotonSearch.interpret(200, """{"type":"FeatureCollection","features":[]}""")
        assertTrue(empty.isSuccess)
        assertTrue(empty.getOrThrow().isEmpty())
        assertTrue(PhotonSearch.interpret(400, """{"lang":[{"message":"Language is not supported"}]}""").exceptionOrNull() is SearchNetwork)
        assertTrue(PhotonSearch.interpret(503, "").exceptionOrNull() is SearchNetwork)
        assertTrue(PhotonSearch.interpret(200, "not-json").exceptionOrNull() is SearchNetwork)
        assertTrue(PhotonSearch.interpret(200, """{"type":"FeatureCollection"}""").exceptionOrNull() is SearchNetwork)
        assertTrue(PhotonSearch.interpret(429, "").exceptionOrNull() is SearchLimit)
    }

    private fun hatarUtcaBody(): String {
        return """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[19.3888628,47.439289]},
               "properties":{"name":"Határ utca","city":"Gyömrő","district":"Dózsatanya","postcode":"2230","county":"Pest megye","country":"Magyarország","osm_key":"highway","osm_value":"living_street","type":"street"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[19.3699805,47.4382569]},
               "properties":{"name":"Határ utca","city":"Gyömrő","district":"Petőfitelep","postcode":"2230","county":"Pest megye","country":"Magyarország","osm_key":"highway","osm_value":"residential","type":"street"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[17.600461,47.637234]},
               "properties":{"street":"Határ utca","housenumber":"27","city":"Győr","postcode":"9019","country":"Magyarország","type":"house"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[19.3912404,47.4273419]},
               "properties":{"street":"Kossuth Ferenc utca","housenumber":"27","city":"Gyömrő","district":"Erzsébettelep","postcode":"2230","country":"Magyarország","type":"house"}}
            ]}
        """.trimIndent()
    }

    private fun hungarianBody(): String {
        return """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[19.0402383,47.4978789]},
               "properties":{"name":"Budapest","county":"Budapest","country":"Magyarország","countrycode":"HU"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[19.394503,47.4223375]},
               "properties":{"name":"Gyömrő","county":"Pest megye","country":"Magyarország","postcode":"2230","countrycode":"HU"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[19.3899,47.4261]},
               "properties":{"name":"","street":"Kossuth Ferenc utca","housenumber":"1","district":"Máriatelep","city":"Gyömrő","country":"Magyarország","postcode":"2230"}}
            ]}
        """.trimIndent()
    }
}
