package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPipelineTest {
    @Test
    fun parsedOsrmRouteDrivesProgressManeuverAndArrival() {
        val plan = (OsrmClient.parse(routeJson()) as OsrmResult.Ready).plan
        val start = plan.coordinates.first()
        val progress = RouteProgress.project(plan, start.lon, start.lat)
        assertFalse(progress.offRoute)
        assertEquals("turn", progress.maneuver.type)
        assertTrue(progress.remainingMeters > progress.maneuverDistanceMeters)

        val session = GuidanceSession()
        session.setPlan(plan)
        val cue = session.onFix(start.lon, start.lat, 0L, 0L, 5.0)!!
        assertEquals(progress.remainingMeters, cue.remainingMeters, 0.05)
        assertEquals(progress.maneuverDistanceMeters, cue.maneuverDistanceMeters, 0.05)
        assertEquals("Jobbra, Kossuth utca", cue.maneuverLabel)
        assertFalse(cue.arrived)
        assertFalse(cue.offRouteConfirmed)
        assertEquals(GpsTrust.USABLE, cue.trust)

        val end = plan.coordinates.last()
        val arrived = session.onFix(end.lon, end.lat, 1_000L, 0L, 5.0)!!
        assertTrue(arrived.arrived)
        assertEquals(0.0, arrived.remainingMeters, 0.001)
        assertEquals("Cél", arrived.maneuverLabel)
    }

    private fun routeJson(): String {
        return """
            {"code":"Ok","routes":[{
              "distance":1000,
              "duration":120,
              "geometry":{"type":"LineString","coordinates":[[19.0,47.0],[19.02,47.0]]},
              "legs":[{"steps":[
                {"distance":0,"name":"","maneuver":{"type":"depart","modifier":"straight","location":[19.0,47.0]}},
                {"distance":400,"name":"Kossuth utca","maneuver":{"type":"turn","modifier":"right","location":[19.008,47.0]}},
                {"distance":600,"name":"Otthon","maneuver":{"type":"arrive","location":[19.02,47.0]}}
              ]}]
            }],"waypoints":[
              {"name":"Indulás","location":[19.0,47.0]},
              {"name":"Otthon","location":[19.02,47.0]}
            ]}
        """.trimIndent()
    }
}
