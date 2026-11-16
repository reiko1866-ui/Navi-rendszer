package hu.toonnav.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only nationwide address index. Rows are queried through SQLite indexes.
 * The file is never loaded as a whole. A missing or unreadable file is reported
 * as unavailable so the existing Photon search can continue.
 */
class HungaryAddresses(private val context: Context) {
    @Volatile private var database: SQLiteDatabase? = null
    @Volatile private var unavailable = false

    fun lookup(query: String): LocalLookup {
        val db = database() ?: return LocalLookup.Unavailable
        return try {
            for (candidate in AddressText.candidates(query)) {
                val hit = findExact(db, candidate) ?: continue
                return LocalLookup.Hit(hit)
            }
            LocalLookup.Miss
        } catch (err: Exception) {
            unavailable = true
            database?.close()
            database = null
            LocalLookup.Unavailable
        }
    }

    private fun database(): SQLiteDatabase? {
        database?.let { return it }
        if (unavailable) return null
        return try {
            val file = openCopy()
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { database = it }
        } catch (err: Exception) {
            unavailable = true
            null
        }
    }

    private fun openCopy(): File {
        val destination = File(context.filesDir, "hungary-addresses.sqlite")
        val expected = context.assets.open(ASSET_SIZE).bufferedReader().use { it.readText().trim().toLong() }
        if (destination.isFile && destination.length() == expected) return destination
        val temporary = File(context.filesDir, "hungary-addresses.sqlite.part")
        context.assets.open(ASSET).use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        if (temporary.length() != expected) {
            temporary.delete()
            throw IllegalStateException("A címadatbázis másolata hiányos.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination
    }

    private fun findExact(db: SQLiteDatabase, parts: AddressText.AddressParts): LocalAddress? {
        val group = db.rawQuery(
            """
            SELECT places.name, streets.name, groups.postcode, groups.housenumber,
                   groups.point_count, groups.id
            FROM places
            JOIN streets ON streets.place_id = places.id AND streets.name_fold = ?
            JOIN groups ON groups.place_id = places.id
                       AND groups.street_id = streets.id
                       AND groups.housenumber_key = ?
            WHERE places.name_fold = ?
            """.trimIndent(),
            arrayOf(AddressText.fold(parts.street), AddressText.houseKey(parts.house), AddressText.fold(parts.settlement))
        )
        group.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val groupId = cursor.getLong(5)
            val points = readPoints(db, groupId)
            if (points.isEmpty()) return null
            return LocalAddress(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(3),
                if (cursor.isNull(2)) null else cursor.getString(2),
                cursor.getInt(4),
                points
            )
        }
    }

    private fun readPoints(db: SQLiteDatabase, groupId: Long): List<LocalPoint> {
        val result = ArrayList<LocalPoint>()
        val cursor = db.rawQuery(
            """
            SELECT seq, osm_type, osm_id, district, osm_lat, osm_lon, target_lat, target_lon, target_method
            FROM points WHERE group_id = ? ORDER BY seq
            """.trimIndent(),
            arrayOf(groupId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                result.add(
                    LocalPoint(
                        it.getInt(0),
                        it.getString(1),
                        it.getLong(2),
                        if (it.isNull(3)) null else it.getString(3),
                        it.getInt(4) / 10_000_000.0,
                        it.getInt(5) / 10_000_000.0,
                        it.getInt(6) / 10_000_000.0,
                        it.getInt(7) / 10_000_000.0,
                        it.getString(8)
                    )
                )
            }
        }
        return result
    }

    companion object {
        const val ASSET = "hungary-addresses.sqlite"
        const val ASSET_SIZE = "hungary-addresses.sqlite.size"

        fun shouldUsePhoton(lookup: LocalLookup): Boolean = lookup !is LocalLookup.Hit
    }
}

sealed class LocalLookup {
    data class Hit(val address: LocalAddress) : LocalLookup()
    data object Miss : LocalLookup()
    data object Unavailable : LocalLookup()
}

data class LocalAddress(
    val settlement: String,
    val street: String,
    val housenumber: String,
    val postcode: String?,
    val pointCount: Int,
    val points: List<LocalPoint>
)

data class LocalPoint(
    val seq: Int,
    val osmType: String,
    val osmId: Long,
    val district: String?,
    val osmLat: Double,
    val osmLon: Double,
    val targetLat: Double,
    val targetLon: Double,
    val targetMethod: String
)
