package hu.toonnav.map

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Chooses a livery and a start number. It does not read GPS or plan a route.
 */
class GarageActivity : ComponentActivity() {
    private val store by lazy { GarageStore(this) }
    private lateinit var hero: ImageView
    private lateinit var nameView: TextView
    private lateinit var liveryView: TextView
    private lateinit var numberView: TextView
    private lateinit var badge: TextView
    private lateinit var row: LinearLayout
    private var carId: String = GarageFleet.cars.first().id
    private var number: Int = GarageFleet.cars.first().defaultNumber
    private var stageThread: HandlerThread? = null
    private var stageHandler: Handler? = null
    private var stage: RaceGarageStage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_garage)
        val savedId = store.carId()
        carId = savedId ?: GarageFleet.cars.first().id
        number = store.number() ?: GarageFleet.car(carId)!!.defaultNumber
        hero = findViewById(R.id.garageHero)
        nameView = findViewById(R.id.garageName)
        liveryView = findViewById(R.id.garageLivery)
        numberView = findViewById(R.id.garageNumber)
        badge = findViewById(R.id.garageNumberBadge)
        row = findViewById(R.id.garageRow)
        findViewById<FrameLayout>(R.id.garageStage).background = CockpitPanelDrawable().also { it.night = true }
        findViewById<Button>(R.id.garageBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.garageMinus).setOnClickListener { setNumber(number - 1) }
        findViewById<Button>(R.id.garagePlus).setOnClickListener { setNumber(number + 1) }
        findViewById<Button>(R.id.garageUse).setOnClickListener {
            store.write(carId, number)
            finish()
        }
        findViewById<Button>(R.id.garagePrototype).setOnClickListener {
            when (NavigationRoute.garageAction(
                NavigationOwner.aliveRaces,
                DriveSessionMemory.routePoints.size,
                DriveSessionMemory.plan != null
            )) {
                NavigationRoute.GarageAction.RETURN -> finish()
                NavigationRoute.GarageAction.OPEN_LIVE -> startActivity(
                    Intent(this, RacePrototypeActivity::class.java)
                        .putExtra(RacePrototypeActivity.EXTRA_CAR, carId)
                        .putExtra(RacePrototypeActivity.EXTRA_NAVIGATION, true)
                        .putExtra(RacePrototypeActivity.EXTRA_ANNOUNCE, false)
                )
                NavigationRoute.GarageAction.OPEN_SAMPLE -> startActivity(
                    Intent(this, RacePrototypeActivity::class.java).putExtra(RacePrototypeActivity.EXTRA_CAR, carId)
                )
            }
        }
        buildRow()
        render()
        startStage()
    }

    override fun onDestroy() {
        findViewById<android.view.SurfaceView>(R.id.garageSurface).let { view ->
            stage?.surfaceHelper?.detach()
        }
        val handler = stageHandler
        val thread = stageThread
        if (handler != null && thread != null) {
            handler.post {
                stage?.destroy()
                stage = null
            }
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private fun startStage() {
        try {
            val thread = HandlerThread("toonnav-garage", Process.THREAD_PRIORITY_DISPLAY)
            thread.start()
            stageThread = thread
            val handler = Handler(thread.looper)
            stageHandler = handler
            val surface = findViewById<android.view.SurfaceView>(R.id.garageSurface)
            val car = GarageFleet.car(carId) ?: GarageFleet.cars.first()
            handler.post {
                try {
                    val carBytes = assets.open("models/formula-car.glb").use { it.readBytes() }
                    val skyBytes = assets.open("models/noon-sky.hdr").use { it.readBytes() }
                    stage = RaceGarageStage.start(surface, carBytes, skyBytes, car, this, handler)
                    if (stage == null) runOnUiThread { surface.visibility = android.view.View.GONE }
                } catch (failed: Throwable) {
                    stage = null
                    runOnUiThread { surface.visibility = android.view.View.GONE }
                }
            }
        } catch (failed: Throwable) {
            stageThread?.quitSafely()
            stageThread = null
        }
    }

    private fun buildRow() {
        val density = resources.displayMetrics.density
        val gap = (8 * density).toInt()
        val width = (76 * density).toInt()
        for (car in GarageFleet.cars) {
            val plate = LinearLayout(this)
            plate.orientation = LinearLayout.VERTICAL
            plate.gravity = android.view.Gravity.CENTER_HORIZONTAL
            val pad = (6 * density).toInt()
            plate.setPadding(pad, pad, pad, pad)
            val params = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.marginEnd = gap
            plate.layoutParams = params
            plate.setOnClickListener { select(car) }
            plate.tag = car.id
            val thumb = ImageView(this)
            thumb.setImageResource(car.marker)
            thumb.contentDescription = car.name
            thumb.scaleType = ImageView.ScaleType.FIT_CENTER
            plate.addView(thumb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (78 * density).toInt()))
            val label = TextView(this)
            label.text = car.name
            label.gravity = android.view.Gravity.CENTER
            label.setTextColor(0xFFF6F3EE.toInt())
            label.textSize = 12f
            plate.addView(label)
            row.addView(plate)
        }
    }

    private fun select(car: GarageFleet.Car) {
        val previous = GarageFleet.car(carId)
        carId = car.id
        if (previous != null && number == previous.defaultNumber) number = car.defaultNumber
        render()
    }

    private fun setNumber(value: Int) {
        number = GarageFleet.clamp(value)
        render()
    }

    private fun render() {
        val car = GarageFleet.car(carId) ?: GarageFleet.cars.first()
        hero.setImageResource(car.marker)
        hero.contentDescription = car.name
        nameView.text = car.name
        liveryView.text = car.livery
        numberView.text = number.toString()
        badge.text = number.toString()
        val shown = car
        stageHandler?.post { stage?.applyLivery(shown) }
        val density = resources.displayMetrics.density
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            val chosen = child.tag == car.id
            val plate = GradientDrawable()
            plate.cornerRadius = 12f * density
            plate.setColor(0xF0101216.toInt())
            plate.setStroke(((if (chosen) 2.5f else 1f) * density).toInt().coerceAtLeast(1), if (chosen) 0xFFFF4A1A.toInt() else 0x33FFFFFF)
            child.background = plate
        }
        findViewById<Button>(R.id.garageMinus).isEnabled = number > GarageFleet.numberMin
        findViewById<Button>(R.id.garagePlus).isEnabled = number < GarageFleet.numberMax
    }
}
