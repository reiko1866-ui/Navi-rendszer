package hu.toonnav.map

import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Mapzen terrarium elevation. Height in meters is (red * 256 + green + blue / 256) - 32768.
 * Tiles are real measured elevation. A failed download leaves the road flat.
 */
object TerrainTiles {
    const val zoom = 13
    private const val endpoint = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/"

    fun tileIndex(longitude: Double, latitude: Double, zoom: Int = this.zoom): Pair<Int, Int> {
        val count = 2.0.pow(zoom)
        val x = floor((longitude + 180.0) / 360.0 * count).toInt()
        val latitudeRadians = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val y = floor((1.0 - ln(tan(latitudeRadians) + 1.0 / kotlin.math.cos(latitudeRadians)) / PI) / 2.0 * count).toInt()
        return x to y
    }

    fun decode(red: Int, green: Int, blue: Int): Double = red * 256.0 + green + blue / 256.0 - 32768.0

    /**
     * Height in meters relative to the first point, sampled in local east/north.
     * Null when a tile cannot be read. Points away from a loaded tile use the nearest route height.
     */
    fun load(points: List<OsrmPoint>): ((Double, Double) -> Float)? {
        if (points.size < 2) return null
        val tiles = HashMap<Pair<Int, Int>, IntArray>()
        for (point in points) {
            val key = tileIndex(point.lon, point.lat)
            if (key in tiles) continue
            tiles[key] = fetch(key.first, key.second) ?: return null
        }
        val origin = points.first()
        val datum = sample(origin, tiles) ?: return null
        val local = points.map { GeoMeters.local(origin, it) }
        val routeHeight = FloatArray(points.size) { index ->
            ((sample(points[index], tiles) ?: datum) - datum).toFloat()
        }
        return { east, north ->
            val geo = RaceTrack.geographic(origin, east, north)
            val height = sample(OsrmPoint(geo.lon, geo.lat), tiles)
            if (height != null) {
                (height - datum).toFloat()
            } else {
                var best = 0f
                var bestDistance = Double.POSITIVE_INFINITY
                for (index in local.indices) {
                    val distance = kotlin.math.hypot(local[index].first - east, local[index].second - north)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = routeHeight[index]
                    }
                }
                best
            }
        }
    }

    /** Relative heights for each route point. Null when any required tile cannot be read. */
    fun relative(points: List<OsrmPoint>): FloatArray? {
        if (points.isEmpty()) return null
        val tiles = HashMap<Pair<Int, Int>, IntArray>()
        for (point in points) {
            val key = tileIndex(point.lon, point.lat)
            if (key in tiles) continue
            tiles[key] = fetch(key.first, key.second) ?: return null
        }
        val datum = sample(points.first(), tiles) ?: return null
        return FloatArray(points.size) { index ->
            val height = sample(points[index], tiles) ?: return null
            (height - datum).toFloat()
        }
    }

    fun sample(point: OsrmPoint, tiles: Map<Pair<Int, Int>, IntArray>): Double? {
        val count = 2.0.pow(zoom)
        val globalX = (point.lon + 180.0) / 360.0 * count
        val latitudeRadians = Math.toRadians(point.lat.coerceIn(-85.0, 85.0))
        val globalY = (1.0 - ln(tan(latitudeRadians) + 1.0 / kotlin.math.cos(latitudeRadians)) / PI) / 2.0 * count
        val tileX = floor(globalX).toInt()
        val tileY = floor(globalY).toInt()
        val pixels = tiles[Pair(tileX, tileY)] ?: return null
        val pixelX = ((globalX - tileX) * 256.0).coerceIn(0.0, 255.0)
        val pixelY = ((globalY - tileY) * 256.0).coerceIn(0.0, 255.0)
        return bilinear(pixels, pixelX, pixelY)
    }

    private fun bilinear(pixels: IntArray, x: Double, y: Double): Double {
        val x0 = floor(x).toInt().coerceIn(0, 255)
        val y0 = floor(y).toInt().coerceIn(0, 255)
        val x1 = (x0 + 1).coerceAtMost(255)
        val y1 = (y0 + 1).coerceAtMost(255)
        val tx = (x - x0).coerceIn(0.0, 1.0)
        val ty = (y - y0).coerceIn(0.0, 1.0)
        val h00 = heightAt(pixels, x0, y0)
        val h10 = heightAt(pixels, x1, y0)
        val h01 = heightAt(pixels, x0, y1)
        val h11 = heightAt(pixels, x1, y1)
        val top = h00 + (h10 - h00) * tx
        val bottom = h01 + (h11 - h01) * tx
        return top + (bottom - top) * ty
    }

    private fun heightAt(pixels: IntArray, x: Int, y: Int): Double {
        val color = pixels[y * 256 + x]
        return decode((color shr 16) and 255, (color shr 8) and 255, color and 255)
    }

    private fun fetch(x: Int, y: Int): IntArray? {
        val connection = (URL("$endpoint$zoom/$x/$y.png").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 8000
            setRequestProperty("User-Agent", "ToonNav/1.0 (Android navigation)")
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            if (bitmap.width < 256 || bitmap.height < 256) return null
            val pixels = IntArray(256 * 256)
            bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
            pixels
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Local-meter sampler. Outside the route the nearest route height is used; there is no invented hill. */
    fun field(origin: OsrmPoint, route: List<OsrmPoint>, relative: FloatArray): (Double, Double) -> Float {
        val local = route.map { GeoMeters.local(origin, it) }
        return { east, north ->
            var best = 0f
            var bestDistance = Double.POSITIVE_INFINITY
            for (index in local.indices) {
                val distance = kotlin.math.hypot(local[index].first - east, local[index].second - north)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = relative.getOrElse(index) { 0f }
                }
            }
            best
        }
    }
}

/** Inverse helpers kept for tests that check a tile maps back near its center. */
fun terrainTileCenter(x: Int, y: Int, zoom: Int = TerrainTiles.zoom): Pair<Double, Double> {
    val count = 2.0.pow(zoom)
    val longitude = (x + 0.5) / count * 360.0 - 180.0
    val latitude = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * (y + 0.5) / count))))
    return longitude to latitude
}
