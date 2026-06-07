package io.dmcc.citywall

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the CityWall Pathfinder backend (citywall.dmcc.io): the claims leaderboard
 * and city claiming. All calls are blocking and must run off the main thread; they
 * return null / empty on any failure so the UI degrades gracefully.
 */
object PathfinderApi {
    const val SERVER_URL = "https://citywall.dmcc.io"

    data class Leader(val rank: Int, val explorerId: String, val claims: Int)

    fun leaderboard(limit: Int = 50): List<Leader> {
        return try {
            val conn = (URL("$SERVER_URL/leaderboard?limit=$limit").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 15000; readTimeout = 20000 }
            try {
                if (conn.responseCode !in 200..299) return emptyList()
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(json).optJSONArray("leaders") ?: return emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    Leader(o.optInt("rank"), o.optString("explorerId"), o.optInt("claims"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Delete all server-side data for this explorer (claims + record). */
    fun forget(explorerId: String): Boolean {
        return try {
            val conn = (URL("$SERVER_URL/forget").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use {
                    it.write(JSONObject().put("explorerId", explorerId).toString().toByteArray(Charsets.UTF_8))
                }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Nearest city/town to coordinates (for rural spots), or null. */
    fun nearest(lat: Double, lon: Double): CityFix? {
        return try {
            val conn = (URL("$SERVER_URL/nearest?lat=$lat&lon=$lon").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 15000; readTimeout = 60000 }
            try {
                if (conn.responseCode !in 200..299) return null
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (!o.optBoolean("found", false)) return null
                CityFix(o.optString("name"), o.optDouble("lat"), o.optDouble("lon"))
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Claim a city. Returns the status (claimed/yours/taken/ineligible/...) or null on error. */
    fun claim(explorerId: String, cityName: String, lat: Double, lon: Double, manual: Boolean): String? {
        return try {
            val conn = (URL("$SERVER_URL/claims").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject()
                .put("explorerId", explorerId)
                .put("cityName", cityName)
                .put("lat", lat)
                .put("lon", lon)
                .put("manual", manual)
                .toString()
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader()?.use { it.readText() } ?: return null
                JSONObject(resp).optString("status").ifEmpty { null }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
