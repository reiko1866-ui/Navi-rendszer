package hu.toonnav.map

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

class GarageFleetTest {
    @Test
    fun offersTwelveLiveriesAndKeepsTheCurrentMarkerUntilOneIsChosen() {
        assertEquals(12, GarageFleet.cars.size)
        assertEquals(12, GarageFleet.cars.map { it.id }.toSet().size)
        assertEquals(12, GarageFleet.cars.map { it.marker }.toSet().size)
        assertEquals(12, GarageFleet.cars.map { it.name }.toSet().size)
        assertEquals("kek", GarageFleet.shown(null).id)
        assertEquals("kek", GarageFleet.shown("alap").id)
        assertEquals("piros", GarageFleet.shown("parazs").id)
        assertEquals("narancs", GarageFleet.shown("lang").id)
        assertTrue(GarageFleet.cars.all { it.defaultNumber in GarageFleet.numberMin..GarageFleet.numberMax })
        assertTrue(GarageFleet.cars.all { it.frontWing != it.rearWing })
        assertEquals(12, GarageFleet.cars.map { it.body to it.frontWing to it.rearWing }.toSet().size)
        assertEquals(R.drawable.ic_race_car, GarageFleet.markerRes(null))
        assertEquals(R.drawable.ic_race_car, GarageFleet.markerRes("ismeretlen"))
        assertEquals(R.drawable.ic_garage_piros, GarageFleet.markerRes("parazs"))
        assertEquals(R.drawable.ic_garage_narancs, GarageFleet.markerRes("lang"))
        assertEquals(1, GarageFleet.clamp(0))
        assertEquals(99, GarageFleet.clamp(120))
        assertEquals("body", RaceLivery.part("body", "paint_primary_body"))
        assertEquals("side", RaceLivery.part("body", "paint_secondary_body"))
        assertEquals("accent", RaceLivery.part("body", "paint_accent_body"))
        assertEquals("front", RaceLivery.part("front_wing_shell", "paint_primary"))
        assertEquals("rear", RaceLivery.part("rear_wing_shell", "paint_primary"))
        assertEquals("rear", RaceLivery.part("drs_flap_shell", "paint_primary@drs_flap_shell"))
        assertEquals("rim", RaceLivery.part("wheel_FL_mesh", "rim"))
        assertEquals(null, RaceLivery.part("wheel_FL_mesh", "tire"))
        assertEquals("side", RaceLivery.part("body", "carbon_body"))
        assertEquals("front", RaceLivery.part("front_wing_shell", "carbon"))
        assertEquals("front", RaceLivery.part("front_wing_shell", "carbon@shell"))
        assertEquals("rear", RaceLivery.part("rear_wing_shell", "carbon"))
        assertEquals(null, RaceLivery.part("body", "dark_body"))
        assertEquals(null, RaceLivery.part("body", "rain_light_body"))
        assertEquals(null, RaceLivery.part("wheel_FL_mesh", "wheel_cover"))
        val silver = GarageFleet.car("ezust")!!
        assertTrue(channel(silver.body) > 180)
        assertTrue((silver.side and 0xFF) > ((silver.side ushr 16) and 0xFF))
        assertTrue(silver.frontWing != silver.rearWing)
        val red = GarageFleet.car("piros")!!
        assertEquals(red.frontWing, RaceLivery.color("front", red))
        assertEquals(red.rearWing, RaceLivery.color("rear", red))
        assertTrue(channel(red.body) >= 180 || channel(red.accent) >= 180 || channel(red.frontWing) >= 180)
        for (car in GarageFleet.cars) {
            val bright = maxOf(channel(car.body), channel(car.accent), channel(car.frontWing), channel(car.rearWing))
            assertTrue("${car.id} $bright", bright >= 180)
        }
    }

    private fun channel(color: Int): Int {
        return maxOf((color ushr 16) and 0xFF, (color ushr 8) and 0xFF, color and 0xFF)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GarageStoreTest {
    @Test
    fun remembersTheCarAndNumberWithoutTouchingTheHeading() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(GarageStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val store = GarageStore(context)
        assertNull(store.carId())
        assertNull(store.number())
        assertEquals(R.drawable.ic_race_car, store.markerRes())
        store.write("nincs", 4)
        assertNull(store.carId())
        store.write("lang", 136)
        val again = GarageStore(context)
        assertEquals("narancs", again.carId())
        assertEquals(99, again.number())
        assertEquals(R.drawable.ic_garage_narancs, again.markerRes())
        assertNotEquals(R.drawable.ic_race_car, again.markerRes())
        store.write("lila", 44)
        val kept = GarageStore(context)
        assertEquals("lila", kept.carId())
        assertEquals(44, kept.number())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h731dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GarageShotTest {
    @Test
    fun rendersTheGarageAndKeepsTheChoice() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(GarageStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val activity = Robolectric.buildActivity(GarageActivity::class.java).setup().get()
        shadowOf(activity.mainLooper).idleFor(200, TimeUnit.MILLISECONDS)
        val hero = activity.findViewById<android.widget.ImageView>(R.id.garageHero)
        val number = activity.findViewById<android.widget.TextView>(R.id.garageNumber)
        val row = activity.findViewById<android.widget.LinearLayout>(R.id.garageRow)
        assertEquals(GarageFleet.cars.first().defaultNumber.toString(), number.text.toString())
        assertEquals(GarageFleet.cars.size, row.childCount)
        save(activity, "piros")
        row.getChildAt(1).performClick()
        assertEquals("11", number.text.toString())
        save(activity, "kek")
        row.getChildAt(3).performClick()
        assertEquals("44", number.text.toString())
        save(activity, "lila")
        activity.findViewById<android.widget.Button>(R.id.garagePlus).performClick()
        assertEquals("45", number.text.toString())
        row.getChildAt(0).performClick()
        assertEquals("45", number.text.toString())
        row.getChildAt(6).performClick()
        activity.findViewById<android.widget.Button>(R.id.garageUse).performClick()
        val stored = GarageStore(context)
        assertEquals("narancs", stored.carId())
        assertEquals(45, stored.number())
        assertEquals(R.drawable.ic_garage_narancs, stored.markerRes())
        assertTrue(hero.drawable != null)
    }

    private fun save(activity: GarageActivity, name: String) {
        val view = activity.window.decorView
        val width = 1080
        val height = 1920
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = java.io.File("/tmp/garage-shots")
        dir.mkdirs()
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
