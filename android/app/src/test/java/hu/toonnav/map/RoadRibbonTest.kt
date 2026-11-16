package hu.toonnav.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos

class RoadRibbonTest {
    private val lat = 47.0
    private val lon = 19.0

    @Test
    fun aStraightRouteStaysCenteredAndDoesNotInventACircuit() {
        val strip = RoadRibbon.project(lon, lat, 90.0, listOf(point(0.0), point(40.0), point(120.0), point(220.0)))
        assertTrue(strip.ready)
        assertTrue(strip.kerbSide.all { it == 0 })
        assertTrue(strip.center.all { kotlin.math.abs(it.x - 0.5f) < 0.08f })
        assertTrue(strip.center.first().y > strip.center.last().y)
        assertFalse(RoadRibbon.project(lon, lat, 90.0, emptyList()).ready)
        assertFalse(RoadRibbon.project(lon, lat, 90.0, listOf(point(0.0))).ready)
    }

    @Test
    fun aRealRightBendMovesTheRacingLineRight() {
        val route = listOf(point(0.0), point(30.0), point(70.0), bend(110.0, -40.0), bend(150.0, -90.0))
        val straight = RoadRibbon.project(lon, lat, 90.0, listOf(point(0.0), point(80.0), point(180.0)))
        val bend = RoadRibbon.project(lon, lat, 90.0, route)
        assertTrue(bend.center.last().x > straight.center.last().x + 0.04f)
        assertTrue(bend.kerbSide.any { it == 1 })
    }

    @Test
    fun approachingABendPullsItDownTheWindshield() {
        val route = listOf(point(0.0), point(70.0), bend(120.0, -45.0), bend(160.0, -95.0))
        val distant = RoadRibbon.project(lon, lat, 90.0, route)
        val closerOrigin = shift(60.0, 0.0)
        val closer = RoadRibbon.project(closerOrigin.lon, closerOrigin.lat, 90.0, route)
        assertTrue(distant.ready && closer.ready)
        assertTrue(closer.center.maxOf { it.x } > distant.center.maxOf { it.x } + 0.04f)
    }

    @Test
    fun theRoadFollowsTheTravelDirectionAndIgnoresADistantRoute() {
        val route = listOf(point(0.0), point(40.0), point(90.0))
        val east = RoadRibbon.project(lon, lat, 90.0, route)
        val north = RoadRibbon.project(lon, lat, 0.0, route)
        assertTrue(kotlin.math.abs(east.center.last().x - 0.5f) < 0.08f)
        assertTrue(north.center.last().x > east.center.last().x + 0.08f)
        val farAway = shift(0.0, 400.0)
        assertFalse(RoadRibbon.project(farAway.lon, farAway.lat, 90.0, route).ready)
    }

    @Test
    fun headingJitterDoesNotShakeTheCockpit() {
        val settled = CockpitHeading.smooth(90.0, 90.8)
        assertEquals(90.0, settled!!, 0.01)
        val turning = CockpitHeading.smooth(90.0, 130.0)
        assertTrue(turning!! > 90.0 && turning < 112.0)
        assertEquals(40.0, CockpitHeading.smooth(null, 40.0)!!, 0.01)
    }

    @Test
    fun aLeftBendMovesTheLineLeftAndTheInstructionUsesTheExistingArrow() {
        val route = listOf(point(0.0), point(40.0), bend(90.0, 50.0), bend(130.0, 110.0))
        val strip = RoadRibbon.project(lon, lat, 90.0, route)
        assertTrue(strip.center.last().x < 0.46f)
        assertTrue(strip.kerbSide.any { it == -1 })
        assertEquals("FORDULJ JOBBRA", CockpitInstruction.line("Jobbra, Fő utca", 90f))
        assertEquals("FORDULJ BALRA", CockpitInstruction.line("Balra", -90f))
        assertEquals("EGYENESEN", CockpitInstruction.line("Egyenesen", 0f))
        assertEquals("CÉL", CockpitInstruction.line("Cél", 0f))
        assertEquals("KÖRFORGALOM, 2. KIJÁRAT", CockpitInstruction.line("Körforgalom, 2. kijárat", 0f))
    }

    private fun point(eastMeters: Double): OsrmPoint = bend(eastMeters, 0.0)

    private fun shift(eastMeters: Double, northMeters: Double): OsrmPoint = bend(eastMeters, northMeters)

    private fun bend(eastMeters: Double, northMeters: Double): OsrmPoint {
        val lat0 = Math.toRadians(lat)
        val dLon = Math.toDegrees(eastMeters / (GeoMeters.radiusMeters * cos(lat0)))
        val dLat = Math.toDegrees(northMeters / GeoMeters.radiusMeters)
        return OsrmPoint(lon + dLon, lat + dLat)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h731dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PilotCockpitShotTest {
    @Test
    fun drawsTheApprovedFormulaWheelCockpitFromTheRouteGeometry() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_ToonNav)
        val view = PilotCockpitView(context)
        val route = route()
        val strip = RoadRibbon.project(19.0, 47.0, 90.0, route)
        view.show(87, 300.0, CockpitInstruction.line("Jobbra", 90f), 90f, strip, 0.0, "parazs", true, 14)
        val width = 1080
        val height = 1920
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("/tmp/cockpit-pilot")
        dir.mkdirs()
        FileOutputStream(File(dir, "pilot.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals("FORDULJ JOBBRA", CockpitInstruction.line("Jobbra", 90f))
        assertTrue(strip.ready)
        val road = bitmap.getPixel(540, 900)
        assertTrue(android.graphics.Color.red(road) in 30..80)
        val livery = bitmap.getPixel(540, 1408)
        assertTrue(android.graphics.Color.red(livery) > 150)
        assertTrue(android.graphics.Color.red(livery) > android.graphics.Color.green(livery) + 40)
    }

    private fun route(): List<OsrmPoint> {
        val lat = 47.0
        val lon = 19.0
        val lat0 = Math.toRadians(lat)
        fun at(east: Double, north: Double): OsrmPoint {
            val dLon = Math.toDegrees(east / (GeoMeters.radiusMeters * cos(lat0)))
            val dLat = Math.toDegrees(north / GeoMeters.radiusMeters)
            return OsrmPoint(lon + dLon, lat + dLat)
        }
        return listOf(at(0.0, 0.0), at(25.0, 0.0), at(55.0, -8.0), at(90.0, -28.0), at(130.0, -70.0), at(160.0, -120.0))
    }
}
