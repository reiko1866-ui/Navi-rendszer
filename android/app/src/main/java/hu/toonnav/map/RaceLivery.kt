package hu.toonnav.map

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.gltfio.FilamentAsset

/**
 * Paints the formula mesh that is already on screen.
 *
 * The body shell is mostly `paint_primary`, whose authored color is nearly
 * black and whose clearcoat is a perfect mirror. From the chase camera that
 * mirror hides the selected color. The front and rear wings are mostly a
 * `carbon` texture, which a base-color factor cannot recolor. Those carbon
 * primitives are therefore swapped onto a copy of the paint material, and
 * every painted surface gets a dull clearcoat so the livery stays visible.
 * Tires, rims and the wheel covers are left alone. No new texture is loaded.
 */
object RaceLivery {
    fun slot(materialName: String): String? = part("body", materialName)

    fun part(entityName: String, materialName: String): String? {
        val entity = entityName.lowercase().substringBefore('@')
        val name = materialName.lowercase().substringBefore('@')
        val wing = when (entity) {
            "front_wing", "front_wing_shell" -> "front"
            "rear_wing", "rear_wing_shell", "drs_flap", "drs_flap_shell" -> "rear"
            else -> null
        }
        return when {
            entity.startsWith("wheel_") && name == "rim" -> "rim"
            entity.startsWith("wheel_") && name == "wheel_nut" -> "nut"
            entity.startsWith("wheel_") -> null
            wing == "front" && name.startsWith("paint_accent") -> "frontDetail"
            wing == "front" && (name.startsWith("paint_primary") || name == "carbon") -> "front"
            wing == "rear" && name.startsWith("paint_accent") -> "rearDetail"
            wing == "rear" && (name.startsWith("paint_") || name == "carbon") -> "rear"
            entity == "steering_wheel_shell" && name.startsWith("paint_") -> "accent"
            entity == "body" && name.startsWith("paint_primary") -> "body"
            entity == "body" && (name.startsWith("paint_secondary") || name == "carbon_body") -> "side"
            entity == "body" && (name.startsWith("paint_accent") || name.startsWith("helmet")) -> "accent"
            entity == "body" && name.startsWith("suit") -> "side"
            else -> null
        }
    }

    fun color(slot: String, car: GarageFleet.Car): Int {
        return when (slot) {
            "body" -> car.body
            "side" -> car.side
            "accent" -> car.accent
            "front" -> car.frontWing
            "frontDetail" -> car.frontDetail
            "rear" -> car.rearWing
            "rearDetail" -> car.rearDetail
            "rim" -> car.rim
            "nut" -> car.nut
            "wing" -> car.wing
            else -> car.body
        }
    }
}

/**
 * Holds the material copies created so the wings can differ and so a carbon
 * texture can be replaced by paint. Destroy it with the Filament engine.
 */
class LiveryPainter(private val engine: Engine) {
    private val owned = ArrayList<MaterialInstance>()
    private var split = false
    private var shelled = false

    fun apply(asset: FilamentAsset, car: GarageFleet.Car) {
        try {
            if (!split) {
                splitSharedPaint(asset)
                split = true
            }
            if (!shelled) shelled = replaceCarbon(asset)
            val painted = paintEntities(asset, car)
            if (painted == 0) paintShared(asset, car)
        } catch (failed: Throwable) {
            paintShared(asset, car)
        }
    }

    fun destroy() {
        for (instance in owned) engine.destroyMaterialInstance(instance)
        owned.clear()
        split = false
        shelled = false
    }

    private fun splitSharedPaint(asset: FilamentAsset) {
        val renderables = engine.renderableManager
        val seen = HashMap<MaterialInstance, String>()
        for (name in shellNames) {
            val renderable = renderable(renderables, asset, name) ?: continue
            val count = renderables.getPrimitiveCount(renderable)
            for (primitive in 0 until count) {
                val material = renderables.getMaterialInstanceAt(renderable, primitive) ?: continue
                if (!material.name.lowercase().substringBefore('@').startsWith("paint_")) continue
                val owner = seen[material]
                if (owner == null) {
                    seen[material] = name
                } else if (owner != name) {
                    val copy = MaterialInstance.duplicate(material, material.name + "@" + name)
                    owned.add(copy)
                    renderables.setMaterialInstanceAt(renderable, primitive, copy)
                }
            }
        }
    }

    /** Carbon wing and side panels sample a dark texture. Paint has no texture. */
    private fun replaceCarbon(asset: FilamentAsset): Boolean {
        val renderables = engine.renderableManager
        val donor = donorPaint(asset) ?: return false
        var found = 0
        var replaced = 0
        for (name in shellNames) {
            val renderable = renderable(renderables, asset, name) ?: continue
            val count = renderables.getPrimitiveCount(renderable)
            for (primitive in 0 until count) {
                val material = renderables.getMaterialInstanceAt(renderable, primitive) ?: continue
                val key = material.name.lowercase().substringBefore('@')
                if (key != "carbon" && key != "carbon_body") continue
                found++
                try {
                    val copy = MaterialInstance.duplicate(donor, material.name + "@shell")
                    owned.add(copy)
                    renderables.setMaterialInstanceAt(renderable, primitive, copy)
                    replaced++
                } catch (_: Throwable) {
                    // The original carbon stays. The paint around it still changes.
                }
            }
        }
        return found == 0 || replaced > 0
    }

    private fun donorPaint(asset: FilamentAsset): MaterialInstance? {
        val renderables = engine.renderableManager
        val renderable = renderable(renderables, asset, "body") ?: return null
        val count = renderables.getPrimitiveCount(renderable)
        for (primitive in 0 until count) {
            val material = renderables.getMaterialInstanceAt(renderable, primitive) ?: continue
            if (material.name.lowercase().substringBefore('@').startsWith("paint_primary")) return material
        }
        return null
    }

    private fun paintEntities(asset: FilamentAsset, car: GarageFleet.Car): Int {
        val renderables = engine.renderableManager
        var painted = 0
        for (name in paintedNames) {
            val renderable = renderable(renderables, asset, name) ?: continue
            val count = renderables.getPrimitiveCount(renderable)
            for (primitive in 0 until count) {
                val material = renderables.getMaterialInstanceAt(renderable, primitive) ?: continue
                val slot = RaceLivery.part(name, material.name) ?: continue
                tint(material, RaceLivery.color(slot, car))
                painted++
            }
        }
        return painted
    }

    private fun paintShared(asset: FilamentAsset, car: GarageFleet.Car) {
        for (instance in asset.instance.materialInstances) {
            val slot = RaceLivery.slot(instance.name) ?: continue
            tint(instance, RaceLivery.color(slot, car))
        }
    }

    private fun renderable(renderables: RenderableManager, asset: FilamentAsset, name: String): Int? {
        val entity = asset.getFirstEntityByName(name)
        if (entity == 0) return null
        val renderable = renderables.getInstance(entity)
        return if (renderable == 0) null else renderable
    }

    private fun tint(instance: MaterialInstance, color: Int) {
        try {
            instance.setParameter(
                "baseColorFactor",
                com.google.android.filament.Colors.RgbaType.SRGB,
                ((color ushr 16) and 0xFF) / 255f,
                ((color ushr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                1f
            )
        } catch (_: Throwable) {
            return
        }
        // Glossy paint. A clearcoat of 1 with roughness 0 mirrors the dim fill and reads as black.
        setFloat(instance, "roughnessFactor", 0.46f)
        setFloat(instance, "metallicFactor", 0.06f)
        setFloat(instance, "clearCoatFactor", 0.16f)
        setFloat(instance, "clearCoatRoughnessFactor", 0.42f)
    }

    private fun setFloat(instance: MaterialInstance, name: String, value: Float) {
        try {
            instance.setParameter(name, value)
        } catch (_: Throwable) {
            // This material variant has no such uniform. The color above still applies.
        }
    }

    private companion object {
        val shellNames = arrayOf(
            "body",
            "front_wing_shell",
            "rear_wing_shell",
            "drs_flap_shell"
        )
        val paintedNames = arrayOf(
            "body",
            "front_wing_shell",
            "rear_wing_shell",
            "drs_flap_shell",
            "steering_wheel_shell",
            "wheel_FL_mesh",
            "wheel_FR_mesh",
            "wheel_RL_mesh",
            "wheel_RR_mesh"
        )
    }
}
