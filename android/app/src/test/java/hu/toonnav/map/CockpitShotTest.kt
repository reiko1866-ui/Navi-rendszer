package hu.toonnav.map

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h731dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CockpitShotTest {
    @Test
    fun rendersTheCockpitScenes() {
        val scenes = listOf("idle", "active", "turn", "gps", "lost", "offroute", "night")
        val dir = File("/tmp/cockpit-shots")
        dir.mkdirs()
        for (scene in scenes) {
            val intent = Intent(RuntimeEnvironment.getApplication(), CockpitPreviewActivity::class.java)
            intent.putExtra("scene", scene)
            val activity = Robolectric.buildActivity(CockpitPreviewActivity::class.java, intent).setup().get()
            shadowOf(activity.mainLooper).idleFor(400, TimeUnit.MILLISECONDS)
            val view = activity.window.decorView
            val width = 1080
            val height = 1920
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            FileOutputStream(File(dir, "$scene.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            activity.finish()
        }
    }
}
