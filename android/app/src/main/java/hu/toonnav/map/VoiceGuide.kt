package hu.toonnav.map

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.json.JSONObject

class VoiceGuide(private val assets: AssetManager) {
    private val queues = HashMap<String, MutableList<String>>()
    private val cursor = HashMap<String, Int>()
    private val recognition = HashMap<String, String>()
    private var player: MediaPlayer? = null
    private var lastKey = ""

    fun load() {
        recognition.clear()
        queues.clear()
        try {
            val events = JSONObject(assets.open("hang/esemenyek.json").bufferedReader().use { it.readText() })
            val list = events.optJSONArray("esemenyek")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val row = list.optJSONObject(i) ?: continue
                    recognition[row.optString("id")] = row.optString("felismeres")
                }
            }
        } catch (err: Exception) {
            return
        }
        try {
            val manual = JSONObject(assets.open("hang/hozzarendeles.json").bufferedReader().use { it.readText() })
            val rows = manual.optJSONArray("tetelek") ?: return
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val eventId = row.optString("esemeny")
                val state = row.optString("allapot")
                val path = row.optString("utvonal")
                if (!ManeuverEvents.canPlay(state, eventId, recognition[eventId] ?: "hianyzik")) continue
                if (path.isBlank()) continue
                queues.getOrPut(eventId) { ArrayList() }.add(path)
            }
            for (paths in queues.values) paths.sort()
        } catch (err: Exception) {
            queues.clear()
        }
    }

    fun speak(kind: String, eventId: String, meters: Double?, cue: String) {
        val key = kind + "@" + eventId + "@" + cue
        if (!ManeuverEvents.shouldSpeak(kind, meters, lastKey, key)) return
        val paths = queues[eventId] ?: return
        if (paths.isEmpty()) return
        val index = cursor[eventId] ?: 0
        val path = paths[index % paths.size]
        cursor[eventId] = (index + 1) % paths.size
        lastKey = key
        play(path)
    }

    private fun play(path: String) {
        player?.release()
        player = null
        try {
            val descriptor = assets.openFd(path)
            val next = MediaPlayer()
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            next.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            descriptor.close()
            next.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            next.prepare()
            next.start()
            player = next
        } catch (err: Exception) {
            player?.release()
            player = null
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
