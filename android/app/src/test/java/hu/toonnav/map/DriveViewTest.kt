package hu.toonnav.map

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class DriveViewTest {
    @Test
    fun startingNavigationOpensTheDrivingCockpitAndTheFollowCamera() {
        val driving = DriveSession().start()
        assertTrue(driving.instrumentsOpen)
        assertTrue(driving.followCamera)
        assertTrue(driving.canFold)
        assertFalse(driving.searchOpen)
        assertFalse(driving.arrived)
    }

    @Test
    fun cancelAndArrivalBothLeaveTheCockpitAndStayDistinct() {
        val driving = DriveSession().start()
        val cancelled = driving.cancel()
        val arrived = driving.arrive()
        assertFalse(cancelled.guiding)
        assertFalse(cancelled.arrived)
        assertFalse(cancelled.instrumentsOpen)
        assertFalse(cancelled.followCamera)
        assertFalse(arrived.guiding)
        assertTrue(arrived.arrived)
        assertFalse(arrived.instrumentsOpen)
        assertFalse(arrived.followCamera)
        assertFalse(arrived.canFold)
        assertTrue(arrived.start().instrumentsOpen)
        assertFalse(arrived.start().arrived)
    }

    @Test
    fun foldingTheCockpitDoesNotEndTheTripOrTheFollowCamera() {
        val folded = DriveSession().start().toggleCollapse()
        assertTrue(folded.guiding)
        assertFalse(folded.arrived)
        assertTrue(folded.followCamera)
        assertFalse(folded.instrumentsOpen)
        assertTrue(folded.toggleCollapse().instrumentsOpen)
        assertEquals(DriveSession(), DriveSession().toggleCollapse())
        assertEquals(DriveSession(arrived = true), DriveSession(arrived = true).toggleCollapse())
    }

    @Test
    fun searchHidesTheInstrumentsWithoutStoppingTheTrip() {
        val searching = DriveSession().start().toggleSearch()
        assertTrue(searching.guiding)
        assertTrue(searching.followCamera)
        assertTrue(searching.searchOpen)
        assertFalse(searching.instrumentsOpen)
        assertTrue(searching.closeSearch().instrumentsOpen)
        assertTrue(searching.toggleSearch().instrumentsOpen)
    }

    @Test
    fun reopeningAnActiveTripShowsTheDrivingView() {
        val folded = DriveSession().start().toggleCollapse().toggleSearch()
        val opened = DriveSession.restore(folded, hasPlan = true)
        assertTrue(opened.guiding)
        assertTrue(opened.instrumentsOpen)
        assertTrue(opened.followCamera)
        assertFalse(opened.collapsed)
        assertFalse(opened.searchOpen)
        assertEquals(DriveSession(), DriveSession.restore(folded, hasPlan = false))
        assertEquals(DriveSession(), DriveSession.restore(DriveSession().start().arrive(), hasPlan = true))
        assertEquals(DriveSession(), DriveSession.restore(DriveSession(), hasPlan = true))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DriveSessionStoreTest {
    @Test
    fun remembersAnActiveTripAcrossANewStore() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(DriveSessionStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val store = DriveSessionStore(context)
        assertEquals(DriveSession(), store.read())
        store.write(DriveSession(guiding = true, collapsed = true))
        val again = DriveSessionStore(context).read()
        assertTrue(again.guiding)
        assertTrue(again.collapsed)
        assertFalse(again.arrived)
        store.write(DriveSession(arrived = true))
        assertTrue(DriveSessionStore(context).read().arrived)
        assertFalse(DriveSessionStore(context).read().guiding)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DriveTripFileTest {
    @Test
    fun aSavedTripReopensInTheDrivingViewAndAFinishedTripDoesNot() {
        val context = RuntimeEnvironment.getApplication()
        DriveSessionMemory.clear()
        DriveTripFile.delete(context)
        val plan = OsrmRoutePlan(
            1000.0,
            180.0,
            listOf(OsrmPoint(19.0, 47.0), OsrmPoint(19.1, 47.0)),
            emptyList(),
            listOf(OsrmWaypoint("Cél", OsrmPoint(19.1, 47.0), null))
        )
        DriveSessionMemory.hold(plan, null, plan.coordinates, 120.0, null, Place("Cél", "", 47.0, 19.1))
        DriveTripFile.save(context)
        DriveSessionMemory.clear()
        assertTrue(DriveTripFile.load(context))
        val restored = DriveSessionMemory.plan
        assertEquals(1000.0, restored!!.distanceMeters, 0.01)
        assertEquals(120.0, DriveSessionMemory.traveledMeters, 0.01)
        assertEquals("Cél", DriveSessionMemory.destination!!.name)
        val opened = DriveSession.restore(DriveSession(guiding = true, collapsed = true, searchOpen = true), true)
        assertTrue(opened.instrumentsOpen)
        assertTrue(opened.followCamera)
        assertFalse(opened.searchOpen)
        DriveTripFile.delete(context)
        DriveSessionMemory.clear()
        assertFalse(DriveTripFile.load(context))
        assertEquals(DriveSession(), DriveSession.restore(DriveSession(guiding = true), false))
        assertEquals(DriveSession(), DriveSession.restore(DriveSession(arrived = true), true))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h731dp-xxhdpi")
class DriveHudVisibilityTest {
    @Test
    fun theDrivingInstrumentsOpenAndTheMapToolsRemain() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_ToonNav)
        val host = FrameLayout(context)
        android.view.LayoutInflater.from(context).inflate(R.layout.activity_hud, host, true)
        val hud = DriveHud(context)
        hud.bind(host)
        hud.showGuidance(false, false)
        assertEquals(View.GONE, host.findViewById<View>(R.id.maneuverCard).visibility)
        assertEquals(View.GONE, host.findViewById<View>(R.id.driveCard).visibility)
        assertEquals(View.GONE, host.findViewById<View>(R.id.leftColumn).visibility)
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.cameraTools).visibility)
        hud.showGuidance(true, true)
        hud.renderSpeed(36)
        hud.renderManeuver("120 m", "Jobbra", 90f)
        hud.renderTrip("Fő utca", "4,2 km a célig", "14:10")
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.maneuverCard).visibility)
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.driveCard).visibility)
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.speedValue).visibility)
        assertEquals("36", host.findViewById<android.widget.TextView>(R.id.speedValue).text.toString())
        assertEquals("120 m", host.findViewById<android.widget.TextView>(R.id.stepDistance).text.toString())
        assertEquals("Jobbra", host.findViewById<android.widget.TextView>(R.id.maneuverText).text.toString())
        assertEquals("4,2 km a célig", host.findViewById<android.widget.TextView>(R.id.remainText).text.toString())
        assertEquals("14:10", host.findViewById<android.widget.TextView>(R.id.etaText).text.toString())
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.cameraTools).visibility)
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.gpsLamp).visibility)
        hud.showFold(true, false)
        val fold = host.findViewById<android.widget.TextView>(R.id.cockpitToggle)
        assertEquals(View.VISIBLE, fold.visibility)
        assertEquals(context.getString(R.string.cockpit_fold), fold.text.toString())
        hud.showGuidance(false, true)
        hud.showFold(true, true)
        assertEquals(View.GONE, host.findViewById<View>(R.id.driveCard).visibility)
        assertEquals(View.GONE, host.findViewById<View>(R.id.leftColumn).visibility)
        assertEquals(context.getString(R.string.cockpit_unfold), fold.text.toString())
    }
}
