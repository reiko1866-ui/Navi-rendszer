package hu.toonnav.map

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.gltfio.FilamentAsset

/**
 * Paints the existing formula mesh. Geometry stays as it is. Only the color
 * parameter of the already working paint materials changes, and a shared
 * material is duplicated when the front wing and the rear wing need different
 * colors. No new texture, HDR, or shader is loaded.
 */
object RaceLivery {
    fun slot(materialName: String): String? = part("body", materialName)

    fun part(entityName: String, materialName: String): String? {
        val entity = entityName.lowercase().substringBefore('@')
        val name = materialName.lowercase().substringBefore('@')
        return when {
            entity.startsWith("wheel_") && name == "rim" -> "rim"
            entity.startsWith("wheel_") && name == "wheel_nut" -> "nut"
            entity == "front_wing_shell" && name.startsWith("paint_primary") -> "front"
            entity == "front_wing_shell" && name.startsWith("paint_accent") -> "frontDetail"
            entity == "rear_wing_shell" && name.startsWith("paint_primary") -> "rear"
            entity == "rear_wing_shell" && name.startsWith("paint_accent") -> "rearDetail"
            entity == "drs_flap_shell" && name.startsWith("paint_") -> "rear"
            entity == "steering_wheel_shell" && name.startsWith("paint_") -> "accent"
            entity == "body" && name.startsWith("paint_primary") -> "body"
            entity == "body" && name.startsWith("paint_secondary") -> "side"
            entity == "body" && name.startsWith("paint_accent") -> "accent"
            entity == "body" && name.startsWith("helmet") -> "accent"
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
 * Holds the material copies created so the front and rear wings can differ.
 * Destroy it with the Filament engine that owns the car.
 */
class LiveryPainter(private val engine: Engine) {
    private val owned = ArrayList<MaterialInstance>()
    private var split = false

    fun apply(asset: FilamentAsset, car: GarageFleet.Car) {
        try {
            if (!split) {
                splitSharedPaint(asset)
                split = true
            }
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
    }

    private fun splitSharedPaint(asset: FilamentAsset) {
        val renderables = engine.renderableManager
        val seen = HashMap<MaterialInstance, String>()
        val names = arrayOf(
            "front_wing_shell",
            "rear_wing_shell",
            "drs_flap_shell",
            "steering_wheel_shell",
            "body"
        )
        for (name in names) {
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) continue
            val renderable = renderables.getInstance(entity)
            if (renderable == 0) continue
            val count = renderables.getPrimitiveCount(renderable)
            for (primitive in 0 until count) {
                val material = renderables.getMaterialInstanceAt(renderable, primitive) ?: continue
                val materialName = material.name.lowercase()
                if (!materialName.startsWith("paint_")) continue
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

    private fun paintEntities(asset: FilamentAsset, car: GarageFleet.Car): Int {
        val renderables = engine.renderableManager
        val names = arrayOf(
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
        var painted = 0
        for (name in names) {
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) continue
            val renderable = renderables.getInstance(entity)
            if (renderable == 0) continue
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

    private fun tint(instance: MaterialInstance, color: Int) {
        instance.setParameter(
            "baseColorFactor",
            com.google.android.filament.Colors.RgbaType.SRGB,
            ((color ushr 16) and 0xFF) / 255f,
            ((color ushr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            1f
        )
    }
}
