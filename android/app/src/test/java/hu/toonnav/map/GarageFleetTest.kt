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
    fun offersFourOriginalCarsAndKeepsTheCurrentMarkerUntilOneIsChosen() {
        assertEquals(5, GarageFleet.cars.size)
        assertEquals(5, GarageFleet.cars.map { it.id }.toSet().size)
        assertEquals(5, GarageFleet.cars.map { it.marker }.toSet().size)
        assertEquals(5, GarageFleet.cars.map { it.name }.toSet().size)
        assertEquals(R.drawable.ic_race_car, GarageFleet.car("alap")!!.marker)
        assertEquals("alap", GarageFleet.shown(null).id)
        assertEquals("parazs", GarageFleet.shown("parazs").id)
        assertTrue(GarageFleet.cars.all { it.defaultNumber in GarageFleet.numberMin..GarageFleet.numberMax })
        assertEquals(R.drawable.ic_race_car, GarageFleet.markerRes(null))
        assertEquals(R.drawable.ic_race_car, GarageFleet.markerRes("ismeretlen"))
        assertEquals(R.drawable.ic_garage_parazs, GarageFleet.markerRes("parazs"))
        assertEquals(1, GarageFleet.clamp(0))
        assertEquals(99, GarageFleet.clamp(120))
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
        assertEquals("lang", again.carId())
        assertEquals(99, again.number())
        assertEquals(R.drawable.ic_garage_lang, again.markerRes())
        assertNotEquals(R.drawable.ic_race_car, again.markerRes())
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
        assertEquals("8", number.text.toString())
        save(activity, "eben")
        row.getChildAt(1).performClick()
        assertEquals("14", number.text.toString())
        save(activity, "parazs")
        row.getChildAt(2).performClick()
        assertEquals("21", number.text.toString())
        save(activity, "marvany")
        row.getChildAt(3).performClick()
        assertEquals("36", number.text.toString())
        save(activity, "lang")
        activity.findViewById<android.widget.Button>(R.id.garagePlus).performClick()
        assertEquals("37", number.text.toString())
        row.getChildAt(0).performClick()
        assertEquals("37", number.text.toString())
        row.getChildAt(3).performClick()
        activity.findViewById<android.widget.Button>(R.id.garageUse).performClick()
        val stored = GarageStore(context)
        assertEquals("lang", stored.carId())
        assertEquals(37, stored.number())
        assertEquals(R.drawable.ic_garage_lang, stored.markerRes())
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
