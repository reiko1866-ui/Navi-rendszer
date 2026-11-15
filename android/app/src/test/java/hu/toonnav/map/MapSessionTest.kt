package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSessionTest {
    @Test
    fun selectsOpenFreeMapDayAndNightStyles() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty", MapStyles.uri(false))
        assertEquals("https://tiles.openfreemap.org/styles/dark", MapStyles.uri(true))
        assertFalse(MapStyles.uri(true).contains("mapbox"))
    }

    @Test
    fun distinguishesPermissionDenialFromADisabledProvider() {
        assertEquals(LocationAccess.DENIED, LocationAccessGate.decide(false, false, true))
        assertEquals(LocationAccess.PRECISE_REQUIRED, LocationAccessGate.decide(false, true, true))
        assertEquals(LocationAccess.PROVIDER_OFF, LocationAccessGate.decide(true, true, false))
        assertEquals(LocationAccess.READY, LocationAccessGate.decide(true, false, true))
    }

    @Test
    fun acceptsOnlyAFreshNonMockFix() {
        assertEquals(GpsSampleVerdict.LIVE, GpsFixGate.verdict(47.5, 19.0, 1_000L, false))
        assertEquals(GpsSampleVerdict.STALE, GpsFixGate.verdict(47.5, 19.0, GpsTrustGate.lostAfterMs + 1, false))
        assertEquals(GpsSampleVerdict.MOCK, GpsFixGate.verdict(47.5, 19.0, 0L, true))
        assertEquals(GpsSampleVerdict.INVALID, GpsFixGate.verdict(91.0, 19.0, 0L, false))
    }

    @Test
    fun measuresFixAgeFromElapsedTimeAndRejectsAnUnknownClock() {
        assertEquals(1_500L, GpsFixGate.ageMs(5_000_000_000L, 6_500_000_000L, 0L, 0L))
        assertEquals(Long.MAX_VALUE, GpsFixGate.ageMs(0L, 6_500_000_000L, 0L, 1_700_000_000_000L))
        assertEquals(2_000L, GpsFixGate.ageMs(0L, 0L, 1_000L, 3_000L))
    }

    @Test
    fun asksTheFusedProviderOnAModernPhoneAndSkipsGpsWithoutPrecisePermission() {
        val modern = LocationListenPlan.targets(31, true, true, true, true)
        assertEquals(LocationListenPlan.fused, modern[0].provider)
        assertTrue(modern.any { it.provider == LocationListenPlan.gps && it.highAccuracy })
        val approximate = LocationListenPlan.targets(31, false, true, true, true)
        assertFalse(approximate.any { it.provider == LocationListenPlan.gps })
        assertTrue(approximate.any { it.provider == LocationListenPlan.fused })
        assertTrue(LocationListenPlan.targets(21, true, true, true, false).none { it.provider == LocationListenPlan.fused })
        assertTrue(LocationListenPlan.targets(31, true, false, true, true).isEmpty())
    }

    @Test
    fun usesOnlyARealGpsFixAsTheRouteOrigin() {
        assertNull(GpsOrigin.place("Jelenlegi helyzet", false, 47.5, 19.0))
        assertNull(GpsOrigin.place("Jelenlegi helyzet", true, 91.0, 19.0))
        val origin = GpsOrigin.place("Jelenlegi helyzet", true, 47.497912, 19.040235)
        assertEquals(47.497912, origin!!.lat, 0.0000001)
        assertEquals(19.040235, origin.lon, 0.0000001)
        assertEquals("Jelenlegi helyzet", origin.name)
    }

    @Test
    fun nightRoadsStayDarkerAndTheRouteLayerIsNotRecolored() {
        val dayMinor = RaceMapPaint.roadColor("road_minor", false)
        val nightMinor = RaceMapPaint.roadColor("road_minor", true)
        assertTrue(dayMinor != null && nightMinor != null)
        assertTrue((dayMinor!! and 0xFF) > (nightMinor!! and 0xFF))
        assertTrue(RaceMapPaint.roadColor("road_motorway", false) != dayMinor)
        assertNull(RaceMapPaint.roadColor("toonnav-route-line", true))
        assertEquals(RaceMapPaint.roadColor("highway_path", true), RaceMapPaint.roadColor("road_path_pedestrian", true))
        assertEquals(RaceMapPaint.roadColor("road_minor_casing", false), RaceMapPaint.roadColor("road_service_track_casing", false))
        assertTrue(RaceMapPaint.roadColor("road_service_track", false) != RaceMapPaint.roadColor("road_service_track_casing", false))
        assertNotNull(RaceMapPaint.roadColor("park_outline", false))
        assertNotNull(RaceMapPaint.roadColor("aeroway_runway", true))
    }

    @Test
    fun showsCoordinatesAndFreshSpeed() {
        assertEquals("47.49791, 19.04024", GpsReadout.coordinates(47.497912, 19.040235))
        assertEquals(36, GpsReadout.speedKmh(10f, true, 0L))
        assertEquals("47.49791, 19.04024 · 36 km/h", GpsReadout.text(47.497912, 19.040235, 36))
    }

    @Test
    fun hidesImpossibleCoordinatesAndStaleSpeed() {
        assertNull(GpsReadout.coordinates(91.0, 19.0))
        assertNull(GpsReadout.speedKmh(10f, true, GpsSpeed.freshMs + 1))
        assertEquals(0, GpsReadout.speedKmh(0f, true, 0L))
    }

    @Test
    fun buildsALonLatLineString() {
        val json = RouteLineJson.feature(listOf(OsrmPoint(19.040235, 47.497912), OsrmPoint(19.05, 47.51)))
        assertEquals(
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[[19.040235,47.497912],[19.050000,47.510000]]}}""",
            json
        )
        assertNull(RouteLineJson.feature(listOf(OsrmPoint(19.0, 47.0))))
    }

    @Test
    fun keepsTheWholeLineAheadUntilTheDriveStarts() {
        val line = listOf(OsrmPoint(19.0, 47.0), OsrmPoint(19.02, 47.0))
        val parts = RouteLineSplit.cut(line, 0.0)
        assertTrue(parts.traveled.size < 2)
        assertEquals(line, parts.remaining)
    }

    @Test
    fun marksTheWholeLineDoneAfterTheDestination() {
        val line = listOf(OsrmPoint(19.0, 47.0), OsrmPoint(19.02, 47.0))
        val parts = RouteLineSplit.cut(line, 1_000_000.0)
        assertEquals(line, parts.traveled)
        assertTrue(parts.remaining.size < 2)
    }

    @Test
    fun splitsAStraightSegmentWithoutLeavingAGap() {
        val start = OsrmPoint(19.0, 47.0)
        val end = OsrmPoint(19.02, 47.0)
        val half = segmentMeters(start, end) / 2.0
        val parts = RouteLineSplit.cut(listOf(start, end), half)
        assertEquals(start, parts.traveled.first())
        assertEquals(end, parts.remaining.last())
        assertEquals(parts.traveled.last().lon, parts.remaining.first().lon, 0.0000001)
        assertEquals(parts.traveled.last().lat, parts.remaining.first().lat, 0.0000001)
        assertEquals(19.01, parts.traveled.last().lon, 0.00001)
    }

    @Test
    fun keepsTheCornerWhenTheCutFallsOnTheNextSegment() {
        val start = OsrmPoint(19.0, 47.0)
        val corner = OsrmPoint(19.02, 47.0)
        val end = OsrmPoint(19.02, 47.01)
        val along = segmentMeters(start, corner) + segmentMeters(corner, end) / 2.0
        val parts = RouteLineSplit.cut(listOf(start, corner, end), along)
        assertEquals(start, parts.traveled.first())
        assertEquals(corner, parts.traveled[parts.traveled.lastIndex - 1])
        assertEquals(end, parts.remaining.last())
        assertEquals(parts.traveled.last().lon, parts.remaining.first().lon, 0.0000001)
        assertEquals(parts.traveled.last().lat, parts.remaining.first().lat, 0.0000001)
        assertTrue(parts.remaining.first().lat > corner.lat)
        assertTrue(parts.remaining.first().lat < end.lat)
    }

    private fun segmentMeters(start: OsrmPoint, end: OsrmPoint): Double {
        val local = GeoMeters.local(start, end)
        return kotlin.math.hypot(local.first, local.second)
    }
}
