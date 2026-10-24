package hu.toonnav.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadNoticesTest {
    @Test
    fun showsOnlyAFreshPostedSpeedLimit() {
        val now = 10_000L
        val shown = RoadNotices.postedLimit(SpeedLimitSample(50, "KILOMETERS_PER_HOUR"), now, 0L)
        assertEquals(NoticeKind.SPEED_LIMIT, shown?.kind)
        assertEquals(50, shown?.limitKmh)
        assertEquals(RoadNotices.matcherSource, shown?.source)
        assertNull(RoadNotices.postedLimit(SpeedLimitSample(null, "KILOMETERS_PER_HOUR"), now, 0L))
        assertNull(RoadNotices.postedLimit(SpeedLimitSample(50, "KILOMETERS_PER_HOUR"), now, 5_000L))
        assertNull(RoadNotices.postedLimit(SpeedLimitSample(30, null), now, 0L))
        assertEquals(97, RoadNotices.postedLimit(SpeedLimitSample(60, "MILES_PER_HOUR"), now, 0L)?.limitKmh)
    }

    @Test
    fun keepsNearbyOpenRoadworksAndClosures() {
        val now = 1_000_000L
        val notices = RoadNotices.visible(
            RoadNotices.fromRoadObjects(
            listOf(
                RoadObjectSample("w", "CONSTRUCTION", null, false, 400.0, now + 60_000L, "mapbox"),
                RoadObjectSample("c", "ROAD_CLOSURE", null, true, 20.0, null, "mapbox"),
                RoadObjectSample("far", "CONSTRUCTION", null, false, 2_000.0, null, "mapbox"),
                RoadObjectSample("old", "ROAD_CLOSURE", null, true, 100.0, now - 1L, "mapbox"),
                RoadObjectSample("hazard", "ROAD_HAZARD", "unknown", false, 300.0, null, "mapbox")
            ),
            now
            ),
            now
        )
        assertEquals(listOf("c", "hazard", "w"), notices.map { it.id })
        assertEquals(NoticeKind.CLOSURE, notices.first { it.id == "c" }.kind)
        assertEquals(NoticeKind.ROADWORK, notices.first { it.id == "w" }.kind)
        assertEquals(NoticeKind.OTHER, notices.first { it.id == "hazard" }.kind)
        assertTrue(notices.none { it.kind == NoticeKind.SPEED_CAMERA || it.kind == NoticeKind.TRAFFIC_LIGHT })
    }

    @Test
    fun dropsExpiredNoticesAndNeverShowsCameraOrSignalKinds() {
        val now = 5_000L
        val planted = listOf(
            Notice("cam", NoticeKind.SPEED_CAMERA, "guess", 10.0, null, null, "camera"),
            Notice("light", NoticeKind.TRAFFIC_LIGHT, "guess", 10.0, null, null, "light"),
            Notice("gone", NoticeKind.ROADWORK, RoadNotices.incidentSource, 10.0, null, now - 1L, "roadwork"),
            Notice("ok", NoticeKind.OTHER, RoadNotices.railwaySource, 80.0, null, null, "railway")
        )
        val visible = RoadNotices.visible(planted, now)
        assertEquals(listOf("ok"), visible.map { it.id })
    }
}
