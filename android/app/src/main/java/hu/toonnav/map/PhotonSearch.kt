package hu.toonnav.map

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

class PhotonSearch {
    private val pool = Executors.newSingleThreadExecutor()

    fun search(query: String, onResult: (Result<List<Place>>) -> Unit) {
        val text = query.trim()
        if (text.length < 2) {
            onResult(Result.success(emptyList()))
            return
        }
        pool.execute {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val connection = (URL("https://photon.komoot.io/api/?limit=6&lang=hu&q=$encoded").openConnection() as HttpURLConnection)
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
                if (code == 429) {
                    onResult(Result.failure(SearchLimit()))
                    return@execute
                }
                if (code !in 200..299) {
                    onResult(Result.failure(SearchNetwork()))
                    return@execute
                }
                onResult(Result.success(parse(body)))
            } catch (err: Exception) {
                onResult(Result.failure(SearchNetwork()))
            }
        }
    }

    private fun parse(body: String): List<Place> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        val places = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            if (!lat.isFinite() || !lon.isFinite()) continue
            val props = feature.optJSONObject("properties") ?: JSONObject()
            val name = placeTitle(props)
            places.add(Place(name, placeDetail(props), lat, lon))
        }
        return places
    }

    private fun placeTitle(props: JSONObject): String {
        val name = props.optString("name")
        if (name.isNotBlank()) return name
        val street = props.optString("street")
        val number = props.optString("housenumber")
        if (street.isNotBlank()) return if (number.isBlank()) street else "$street $number"
        val city = props.optString("city")
        if (city.isNotBlank()) return city
        val country = props.optString("country")
        if (country.isNotBlank()) return country
        return "Hely"
    }

    private fun placeDetail(props: JSONObject): String {
        val parts = ArrayList<String>()
        val street = props.optString("street")
        val number = props.optString("housenumber")
        val streetLine = if (street.isBlank()) "" else if (number.isBlank()) street else "$street $number"
        val name = props.optString("name")
        if (streetLine.isNotBlank() && streetLine != name) parts.add(streetLine)
        val city = props.optString("city")
        val locality = props.optString("locality")
        val state = props.optString("state")
        when {
            city.isNotBlank() && city != name -> parts.add(city)
            locality.isNotBlank() && locality != name -> parts.add(locality)
            state.isNotBlank() && state != name -> parts.add(state)
        }
        val country = props.optString("country")
        if (country.isNotBlank()) parts.add(country)
        return parts.joinToString(", ")
    }
}

class SearchNetwork : Exception()
class SearchLimit : Exception()
