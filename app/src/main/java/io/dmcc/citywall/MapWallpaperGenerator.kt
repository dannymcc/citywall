package io.dmcc.citywall

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos

/**
 * Renders a roads-only dark street-map by querying OpenStreetMap via the Overpass
 * API and drawing the ways onto a [Canvas] by hand. No tiles, no labels, no API key.
 *
 * @param overpassUrl the Overpass interpreter endpoint. Defaults to the shared public
 *   instance — fine for the lazy, one-city-at-a-time path, NOT for warming a long
 *   list. Point this at a self-hosted instance to warm aggressively.
 * @param halfHeightMetres the zoom control: half the real-world height of the view.
 *   Default 2200 m gives a ~4.4 km tall view matching the reference density.
 */
class MapWallpaperGenerator(
    private val overpassUrl: String = "https://overpass-api.de/api/interpreter",
    private val halfHeightMetres: Double = 2200.0,
) : WallpaperGenerator {

    private val backgroundColour = 0xFF1A1E27.toInt()

    /**
     * Road classes. `rank` controls draw order (low rank drawn first, so major roads
     * sit on top); `widthDp` and `colour` control the look. Tune freely.
     */
    private enum class RoadClass(val rank: Int, val widthDp: Float, val colour: Int) {
        MINOR(0, 1.4f, 0xFF333B49.toInt()),
        SERVICE(1, 1.1f, 0xFF2C3340.toInt()),
        TERTIARY(2, 2.2f, 0xFF444E5F.toInt()),
        SECONDARY(3, 3.0f, 0xFF566073.toInt()),
        PRIMARY(4, 4.0f, 0xFF6B7689.toInt()),
        MAJOR(5, 5.2f, 0xFF7E8AA0.toInt()),
    }

    /**
     * Maps an OSM `highway` tag value to a [RoadClass], or null to skip the way.
     * footway/path/cycleway/pedestrian/steps are deliberately skipped for a clean
     * result — add cases here to bring them back.
     */
    private fun classify(highway: String): RoadClass? = when (highway) {
        "residential", "unclassified", "living_street" -> RoadClass.MINOR
        "service" -> RoadClass.SERVICE
        "tertiary", "tertiary_link" -> RoadClass.TERTIARY
        "secondary", "secondary_link" -> RoadClass.SECONDARY
        "primary", "primary_link" -> RoadClass.PRIMARY
        "motorway", "motorway_link", "trunk", "trunk_link" -> RoadClass.MAJOR
        else -> null
    }

    override fun generate(
        cityName: String, lat: Double, lon: Double, widthPx: Int, heightPx: Int,
    ): Bitmap {
        // 1. Bounding box whose real-world aspect matches the screen, so roads aren't
        //    stretched. Equirectangular metres-per-degree approximation.
        val halfWidthMetres = halfHeightMetres * (widthPx.toDouble() / heightPx.toDouble())
        val latHalf = halfHeightMetres / 111320.0
        val lonHalf = halfWidthMetres / (111320.0 * cos(Math.toRadians(lat)))

        val south = lat - latHalf
        val north = lat + latHalf
        val west = lon - lonHalf
        val east = lon + lonHalf

        // 2. Fetch highways from Overpass.
        val json = fetchOverpass(south, west, north, east)

        // 3 + 4. Parse and render.
        return render(json, widthPx, heightPx, south, north, west, east)
    }

    private fun fetchOverpass(south: Double, west: Double, north: Double, east: Double): String {
        // `out geom;` returns each way's node coordinates inline — no separate node lookup.
        val query = """
            [out:json][timeout:25];
            (way["highway"]($south,$west,$north,$east););
            out geom;
        """.trimIndent()
        val body = "data=" + URLEncoder.encode(query, "UTF-8")

        val conn = (URL(overpassUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "CityWall/1.0 (personal sideload app)")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                // Throw so the worker can retry and leave the existing wallpaper alone.
                throw IOException("Overpass returned HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun render(
        json: String, widthPx: Int, heightPx: Int,
        south: Double, north: Double, west: Double, east: Double,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColour)

        val strokeScale = widthPx / 1080f
        val lonSpan = east - west
        val latSpan = north - south

        // Bucket ways by class so we can draw lowest rank first, highest last.
        val buckets = HashMap<RoadClass, MutableList<Path>>()

        val elements = JSONObject(json).optJSONArray("elements") ?: return bitmap
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            if (el.optString("type") != "way") continue
            val tags = el.optJSONObject("tags") ?: continue
            val roadClass = classify(tags.optString("highway", "")) ?: continue
            val geometry = el.optJSONArray("geometry") ?: continue
            if (geometry.length() < 2) continue

            val path = Path()
            for (j in 0 until geometry.length()) {
                val node = geometry.optJSONObject(j) ?: continue
                val nlat = node.optDouble("lat")
                val nlon = node.optDouble("lon")
                // Plain equirectangular projection within the bbox (note the y flip).
                val x = ((nlon - west) / lonSpan * widthPx).toFloat()
                val y = ((north - nlat) / latSpan * heightPx).toFloat()
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            buckets.getOrPut(roadClass) { mutableListOf() }.add(path)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (roadClass in RoadClass.values().sortedBy { it.rank }) {
            val paths = buckets[roadClass] ?: continue
            paint.color = roadClass.colour
            paint.strokeWidth = roadClass.widthDp * strokeScale
            for (p in paths) canvas.drawPath(p, paint)
        }

        return bitmap
    }
}
