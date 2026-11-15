package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmClientTest {
    @Test
    fun readsDistanceDurationLineAndManeuvers() {
        val ready = OsrmClient.parse(routeJson()) as OsrmResult.Ready
        assertEquals(1200.5, ready.plan.distanceMeters, 0.001)
        assertEquals(180.2, ready.plan.durationSeconds ?: 0.0, 0.001)
        assertEquals(listOf(19.04, 19.05), ready.plan.coordinates.map { it.lon })
        assertEquals(listOf(47.49, 47.50), ready.plan.coordinates.map { it.lat })
        assertEquals("right", ready.plan.steps[1].maneuver.modifier)
        assertEquals("Fő utca", ready.plan.steps[1].name)
        assertEquals(2, ready.plan.steps[2].maneuver.exit)
        assertEquals("A", ready.plan.waypoints[0].name)
        assertEquals(3.5, ready.plan.waypoints[0].snapMeters ?: 0.0, 0.001)
    }

    @Test
    fun keepsARouteWhenDurationIsMissing() {
        val json = routeJson().replace(""""duration":180.2,""", "")
        val ready = OsrmClient.parse(json) as OsrmResult.Ready
        assertNull(ready.plan.durationSeconds)
        assertEquals(1200.5, ready.plan.distanceMeters, 0.001)
    }

    @Test
    fun usesOnlyTheFirstRoute() {
        val ready = OsrmClient.parse(routeJson()) as OsrmResult.Ready
        assertEquals(19.04, ready.plan.coordinates.first().lon, 0.000001)
        assertTrue(ready.plan.distanceMeters < 2000.0)
    }

    @Test
    fun rejectsARouteWithoutADrivableLine() {
        val json = routeJson().replace(""""coordinates":[[19.04,47.49],[19.05,47.5]]""", """"coordinates":[[19.04,47.49]]""")
        assertTrue(OsrmClient.parse(json) is OsrmResult.Malformed)
    }

    @Test
    fun rejectsNoRouteAndEmptyRouteLists() {
        assertTrue(OsrmClient.parse("""{"code":"NoRoute","routes":[]}""") is OsrmResult.NoRoute)
        assertTrue(OsrmClient.parse("""{"code":"Ok","routes":[]}""") is OsrmResult.NoRoute)
    }

    @Test
    fun readsTheNoSegmentCoordinate() {
        val result = OsrmClient.parse("""{"code":"NoSegment","message":"No segment found for coordinate 1"}""")
        assertEquals(1, (result as OsrmResult.NoSegment).coordinateIndex)
    }

    @Test
    fun rejectsMalformedJsonAndARateLimit() {
        assertTrue(OsrmClient.interpret(200, "not-json") is OsrmResult.Malformed)
        assertTrue(OsrmClient.interpret(429, "") is OsrmResult.Limit)
        assertTrue(OsrmClient.interpret(503, "{}") is OsrmResult.Network)
    }

    @Test
    fun keepsOnlyWholeExitsFromOneToTwelve() {
        val textExit = routeJson().replace(""""exit":2""", """"exit":"2"""")
        val fractional = routeJson().replace(""""exit":2""", """"exit":2.5""")
        val tooLarge = routeJson().replace(""""exit":2""", """"exit":13""")
        assertNull(((OsrmClient.parse(textExit) as OsrmResult.Ready).plan.steps[2].maneuver.exit))
        assertNull(((OsrmClient.parse(fractional) as OsrmResult.Ready).plan.steps[2].maneuver.exit))
        assertNull(((OsrmClient.parse(tooLarge) as OsrmResult.Ready).plan.steps[2].maneuver.exit))
        assertEquals(2, ((OsrmClient.parse(routeJson()) as OsrmResult.Ready).plan.steps[2].maneuver.exit))
    }

    @Test
    fun buildsTheDrivingGeoJsonUrl() {
        assertEquals(
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                "19.040235,47.497912;19.050000,47.510000" +
                "?overview=full&geometries=geojson&steps=true&alternatives=false&radiuses=2000;2000",
            OsrmClient.routeUrl(19.040235, 47.497912, 19.05, 47.51)
        )
    }

    private fun routeJson(): String {
        return """
            {
              "code":"Ok",
              "routes":[
                {
                  "distance":1200.5,
                  "duration":180.2,
                  "geometry":{"type":"LineString","coordinates":[[19.04,47.49],[19.05,47.5]]},
                  "legs":[{"steps":[
                    {"distance":10,"duration":5,"name":"","maneuver":{"type":"depart","modifier":"straight","location":[19.04,47.49]}},
                    {"distance":400,"name":"Fő utca","maneuver":{"type":"turn","modifier":"right","location":[19.045,47.495]}},
                    {"distance":790.5,"name":"Körút","maneuver":{"type":"roundabout","modifier":"right","exit":2,"location":[19.05,47.5]}}
                  ]}]
                },
                {"distance":9999,"duration":1,"geometry":{"coordinates":[[0,0],[1,1]]},"legs":[]}
              ],
              "waypoints":[
                {"name":"A","location":[19.04,47.49],"distance":3.5},
                {"name":"B","location":[19.05,47.5]}
              ]
            }
        """.trimIndent()
    }
}
