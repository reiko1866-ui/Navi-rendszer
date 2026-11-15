package hu.toonnav.map

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

enum class PlacePrecision {
    HOUSE,
    STREET,
    PLACE
}

data class Place(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val precision: PlacePrecision = PlacePrecision.PLACE
)

class PhotonSearch {
    private val pool = Executors.newSingleThreadExecutor()

    fun search(query: String, onResult: (Result<List<Place>>) -> Unit) {
        val text = query.trim()
        if (text.length < 2) {
            onResult(Result.success(emptyList()))
            return
        }
        pool.execute {
            val url = requestUrl(text)
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection)
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "ToonNav")
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                connection.disconnect()
                logResponse(url, code, body)
                onResult(interpret(code, body, text))
            } catch (err: Exception) {
                Log.i(LOG_TAG, "url=$url http=error ${err.javaClass.simpleName}")
                onResult(Result.failure(SearchNetwork()))
            }
        }
    }

    companion object {
        fun requestUrl(query: String): String {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            return "https://photon.komoot.io/api/?limit=6&lang=default&q=$encoded"
        }

        fun interpret(httpCode: Int, body: String, query: String = ""): Result<List<Place>> {
            if (httpCode == 429) return Result.failure(SearchLimit())
            if (httpCode !in 200..299) return Result.failure(SearchNetwork())
            return try {
                val root = JSONObject(body)
                val features = root.optJSONArray("features") ?: return Result.failure(SearchNetwork())
                Result.success(readPlaces(features, query))
            } catch (err: Exception) {
                Result.failure(SearchNetwork())
            }
        }

        const val streetOnlyNote = "Csak az utca, házszám nélkül."

        fun requestedHouseNumber(query: String): String? {
            val tokens = query.lowercase(Locale.ROOT)
                .replace('.', ' ')
                .split(Regex("[^\\p{L}\\p{N}/]+"))
                .filter { it.isNotBlank() }
            val houses = tokens.filter { it.matches(Regex("\\d{1,3}(?:[a-z]|/[a-z0-9]+)?")) }
            return houses.lastOrNull()
        }

        private fun readPlaces(features: JSONArray, query: String): List<Place> {
            val house = requestedHouseNumber(query)
            val queryFolded = fold(query)
            val places = ArrayList<Place>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lon = coordinate(coords.opt(0)) ?: continue
                val lat = coordinate(coords.opt(1)) ?: continue
                val props = feature.optJSONObject("properties") ?: JSONObject()
                val precision = precisionFor(props, queryFolded, house) ?: continue
                val detail = placeDetail(props)
                val shown = if (precision == PlacePrecision.STREET) {
                    if (detail.isBlank()) streetOnlyNote else "$detail. $streetOnlyNote"
                } else {
                    detail
                }
                places.add(Place(placeTitle(props), shown, lat, lon, precision))
            }
            places.sortBy { if (it.precision == PlacePrecision.HOUSE) 0 else 1 }
            return places
        }

        private fun precisionFor(props: JSONObject, queryFolded: String, house: String?): PlacePrecision? {
            if (house == null) return PlacePrecision.PLACE
            val street = featureStreet(props)
            if (street.isBlank() || !phraseIn(queryFolded, fold(street))) return null
            val leftover = leftover(queryFolded, fold(street), house)
            if (!settlementOk(props, leftover)) return null
            val number = props.optString("housenumber").trim().trimEnd('.')
            if (number.isNotBlank() && number.equals(house, ignoreCase = true)) return PlacePrecision.HOUSE
            if (number.isBlank()) return PlacePrecision.STREET
            return null
        }

        private fun featureStreet(props: JSONObject): String {
            val street = props.optString("street")
            if (street.isNotBlank()) return street
            val name = props.optString("name")
            return if (looksLikeStreet(name)) name else ""
        }

        private fun looksLikeStreet(name: String): Boolean {
            val folded = fold(name)
            return streetSuffixes.any { folded == it || folded.endsWith(" $it") }
        }

        private fun leftover(queryFolded: String, streetFolded: String, house: String): String {
            var text = " ${queryFolded.trim()} "
            if (streetFolded.isNotBlank()) text = text.replace(" $streetFolded ", " ")
            text = text.replace(" $house ", " ")
            text = text.replace(Regex("\\b\\d{4}\\b"), " ")
            return text.trim().replace(Regex(" +"), " ")
        }

        private fun settlementOk(props: JSONObject, leftover: String): Boolean {
            if (leftover.isBlank()) return true
            val city = fold(firstText(props, "city", "locality", "name"))
            val cityOk = city.isNotBlank() && phraseIn(leftover, city)
            if (leftover.any { it.isLetter() }) return cityOk
            val postcode = props.optString("postcode")
            return cityOk || (postcode.length == 4 && leftover.contains(postcode))
        }

        private fun phraseIn(haystack: String, phrase: String): Boolean {
            if (phrase.isBlank()) return false
            return " ${haystack.trim()} ".contains(" ${phrase.trim()} ")
        }

        private fun fold(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex(" +"), " ")
        }

        private fun firstText(props: JSONObject, vararg keys: String): String {
            for (key in keys) {
                val value = props.optString(key)
                if (value.isNotBlank()) return value
            }
            return ""
        }

        private fun logResponse(url: String, code: Int, body: String) {
            Log.i(LOG_TAG, "url=$url http=$code bytes=${body.length}")
            if (code !in 200..299) {
                Log.i(LOG_TAG, "errorBody=${body.take(300)}")
                return
            }
            val features = try {
                JSONObject(body).optJSONArray("features")
            } catch (err: Exception) {
                null
            } ?: return
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: JSONObject()
                val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                Log.i(
                    LOG_TAG,
                    "hit[$i] street=${props.optString("street")} housenumber=${props.optString("housenumber")} " +
                        "name=${props.optString("name")} city=${props.optString("city")} postcode=${props.optString("postcode")} " +
                        "type=${props.optString("type")} coords=$coords"
                )
            }
        }

        private val streetSuffixes = listOf(
            "utca", "ut", "ter", "tere", "koz", "sor", "korut", "krt", "setany", "park",
            "dulo", "lakotelep", "sugarut", "rakpart", "lepcso", "arok", "udvar", "liget"
        )

        private const val LOG_TAG = "ToonNavSearch"

        private fun coordinate(value: Any?): Double? {
            if (value !is Number) return null
            val number = value.toDouble()
            if (!number.isFinite()) return null
            return number
        }

        private fun placeTitle(props: JSONObject): String {
            val name = props.optString("name")
            if (name.isNotBlank()) return name
            val street = streetLine(props)
            if (street.isNotBlank()) return street
            val city = props.optString("city")
            if (city.isNotBlank()) return city
            val country = props.optString("country")
            if (country.isNotBlank()) return country
            return "Hely"
        }

        private fun placeDetail(props: JSONObject): String {
            val parts = ArrayList<String>()
            val name = props.optString("name")
            val street = streetLine(props)
            if (street.isNotBlank() && name.isNotBlank() && street != name) parts.add(street)
            val district = props.optString("district")
            if (district.isNotBlank() && district != name && district != street) parts.add(district)
            val city = props.optString("city")
            val locality = props.optString("locality")
            val state = props.optString("state")
            val county = props.optString("county")
            when {
                city.isNotBlank() && city != name -> parts.add(city)
                locality.isNotBlank() && locality != name -> parts.add(locality)
                state.isNotBlank() && state != name -> parts.add(state)
                county.isNotBlank() && county != name -> parts.add(county)
            }
            val postcode = props.optString("postcode")
            if (postcode.isNotBlank()) parts.add(postcode)
            val country = props.optString("country")
            if (country.isNotBlank() && country != name) parts.add(country)
            return parts.joinToString(", ")
        }

        private fun streetLine(props: JSONObject): String {
            val street = props.optString("street")
            val number = props.optString("housenumber")
            if (street.isBlank()) return ""
            return if (number.isBlank()) street else "$street $number"
        }
    }
}

class SearchNetwork : Exception()
class SearchLimit : Exception()
