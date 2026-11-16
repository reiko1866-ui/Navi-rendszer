package hu.toonnav.map

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.maps.Style

/**
 * Recolors the live OpenFreeMap vector style. Streets, buildings and labels stay
 * on the real tile source. Missing layers are skipped, so a style update cannot
 * take the map down.
 */
object RaceMapPaint {
    fun apply(style: Style, night: Boolean) {
        val tone = if (night) nightTone else dayTone
        for (layer in style.layers) {
            if (layer.id.startsWith("toonnav-")) continue
            try {
                paint(layer, tone)
            } catch (err: RuntimeException) {
                continue
            }
        }
        addBuildingsIfMissing(style, tone)
    }

    fun roadColor(layerId: String, night: Boolean): Int? {
        val tone = if (night) nightTone else dayTone
        val name = layerId.lowercase()
        if (name.startsWith("toonnav-")) return null
        if (name.contains("rail")) return tone.rail
        if (name.contains("water")) return tone.water
        val motorway = name.contains("motorway")
        val casing = name.contains("casing")
        if (motorway && casing) return tone.motorwayCasing
        if (motorway) return tone.motorway
        if (name.contains("path") || name.contains("pedestrian") || name.contains("track") || name.contains("service")) {
            return if (casing) tone.casing else tone.path
        }
        if (casing) return tone.casing
        if (name.contains("trunk") || name.contains("primary") || name.contains("major_inner") || name.contains("major")) {
            return tone.primary
        }
        if (name.contains("secondary") || name.contains("tertiary") || name.contains("minor") ||
            name.contains("street") || name.contains("link") || name.contains("highway") ||
            name.contains("road_") || name.contains("bridge_") || name.contains("tunnel_")
        ) {
            return tone.minor
        }
        if (name.contains("park")) return tone.green
        if (name.contains("aeroway") || name.contains("boundary")) return tone.rail
        return null
    }

    private fun paint(layer: Layer, tone: Tone) {
        when (layer) {
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(if (tone.night) 0xFF0C0E12.toInt() else 0xFF2A3038.toInt()))
            is FillLayer -> {
                if (layer.id == "road_area_pattern") {
                    layer.setProperties(PropertyFactory.fillOpacity(0f))
                    return
                }
                val color = fillColor(layer.id, tone) ?: return
                layer.setProperties(
                    PropertyFactory.fillColor(color),
                    PropertyFactory.fillOutlineColor(color),
                    PropertyFactory.fillOpacity(if (layer.id == "building") 0.92f else 1f)
                )
            }
            is LineLayer -> {
                val color = roadColor(layer.id, tone.night) ?: return
                layer.setProperties(PropertyFactory.lineColor(color))
            }
            is FillExtrusionLayer -> layer.setProperties(
                PropertyFactory.fillExtrusionColor(tone.building),
                PropertyFactory.fillExtrusionOpacity(tone.extrusion)
            )
            is RasterLayer -> layer.setProperties(PropertyFactory.rasterOpacity(tone.relief))
            is SymbolLayer -> layer.setProperties(
                PropertyFactory.textColor(tone.ink),
                PropertyFactory.textHaloColor(tone.halo),
                PropertyFactory.textHaloWidth(1.2f)
            )
        }
    }

    private fun fillColor(id: String, tone: Tone): Int? {
        val name = id.lowercase()
        return when {
            name == "background" -> null
            name.contains("water") -> tone.water
            name.contains("sand") || name.contains("ice") -> tone.land
            name.contains("wood") || name.contains("grass") || name.contains("park") || name.contains("landcover") -> tone.green
            name.contains("building") -> tone.building
            name.contains("residential") || name.contains("landuse") || name.contains("sand") || name.contains("pitch") -> tone.land
            else -> null
        }
    }

    private fun addBuildingsIfMissing(style: Style, tone: Tone) {
        if (style.getLayer("building-3d") != null || style.getSource("openmaptiles") == null) return
        val extrusion = FillExtrusionLayer("building-3d", "openmaptiles")
        extrusion.sourceLayer = "building"
        extrusion.minZoom = 14f
        extrusion.setFilter(Expression.not(Expression.has("hide_3d")))
        extrusion.setProperties(
            PropertyFactory.fillExtrusionColor(tone.building),
            PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
            PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
            PropertyFactory.fillExtrusionOpacity(tone.extrusion)
        )
        val before = style.layers.firstOrNull { it is SymbolLayer }?.id
        if (before != null) style.addLayerBelow(extrusion, before) else style.addLayer(extrusion)
    }

    private data class Tone(
        val night: Boolean,
        val water: Int,
        val green: Int,
        val land: Int,
        val building: Int,
        val extrusion: Float,
        val relief: Float,
        val casing: Int,
        val minor: Int,
        val primary: Int,
        val motorway: Int,
        val motorwayCasing: Int,
        val path: Int,
        val rail: Int,
        val ink: Int,
        val halo: Int
    )

    private val dayTone = Tone(
        night = false,
        water = 0xFF1A4554.toInt(),
        green = 0xFF24362E.toInt(),
        land = 0xFF323840.toInt(),
        building = 0xFF4A545F.toInt(),
        extrusion = 0.58f,
        relief = 0.18f,
        casing = 0xFF14181E.toInt(),
        minor = 0xFFD5DEE6.toInt(),
        primary = 0xFFF4F7FA.toInt(),
        motorway = 0xFFFF8A4A.toInt(),
        motorwayCasing = 0xFF4A2018.toInt(),
        path = 0xFF8A8178.toInt(),
        rail = 0xFF6A6158.toInt(),
        ink = 0xFFF7F4EF.toInt(),
        halo = 0xFF1A1E24.toInt()
    )

    private val nightTone = Tone(
        night = true,
        water = 0xFF0C2834.toInt(),
        green = 0xFF14201A.toInt(),
        land = 0xFF14181E.toInt(),
        building = 0xFF222830.toInt(),
        extrusion = 0.72f,
        relief = 0.08f,
        casing = 0xFF07080A.toInt(),
        minor = 0xFF8E98A3.toInt(),
        primary = 0xFFD5DDE4.toInt(),
        motorway = 0xFFFF6A2A.toInt(),
        motorwayCasing = 0xFF3A140E.toInt(),
        path = 0xFF4E4944.toInt(),
        rail = 0xFF3C3834.toInt(),
        ink = 0xFFF0EBE4.toInt(),
        halo = 0xFF050608.toInt()
    )
}
