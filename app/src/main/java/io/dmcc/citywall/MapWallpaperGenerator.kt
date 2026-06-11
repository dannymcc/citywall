package io.dmcc.citywall

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONObject
import java.io.File
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
 * @param palette the colour theme (background + per-class road colours).
 */
class MapWallpaperGenerator(
    private val overpassUrl: String = "https://overpass-api.de/api/interpreter",
    private val halfHeightMetres: Double = 2200.0,
    private val palette: Palette = Palette.DEFAULT,
    // When set, the raw Overpass geometry is cached here. Re-rendering the same view
    // in a different palette then skips the network — the look changes straight away.
    // Geometry is palette-independent but bbox-dependent (centre, zoom, screen aspect),
    // so entries are keyed by city + bounding box (see loadOrFetchGeometry).
    private val geometryCacheDir: File? = null,
) : WallpaperGenerator {

    private companion object {
        // Bump whenever the rendered look changes (widths, colours, classification)
        // so previously cached bitmaps regenerate. v2: Pathfinder restyle (#4).
        // v3: single ink tone for CityWall roads.
        const val STYLE_VERSION = 3
    }

    // Cache entries are namespaced by palette + zoom + style version, so a palette
    // or zoom switch regenerates once and a renderer style change invalidates old
    // bitmaps (bump STYLE_VERSION whenever the look changes).
    override val variantKey: String =
        palette.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') +
            "-z${halfHeightMetres.toInt()}-s$STYLE_VERSION"

    /**
     * Road classes. `rank` controls draw order (low rank drawn first, so major roads
     * sit on top); `widthDp` controls thickness. Colours come from the [Palette],
     * indexed by `rank`. Tune freely.
     */
    private enum class RoadClass(val rank: Int, val widthDp: Float) {
        MINOR(0, 1.3f),
        SERVICE(1, 0.9f),
        TERTIARY(2, 2.0f),
        SECONDARY(3, 3.0f),
        PRIMARY(4, 4.6f),
        // Wide enough that the two halves of a dual carriageway merge into one
        // bold stroke at default zoom.
        MAJOR(5, 8.6f),
    }

    /**
     * Maps an OSM way to a [RoadClass], or null to skip it.
     * footway/path/cycleway/pedestrian/steps are deliberately skipped for a clean
     * result — add cases here to bring them back. Link roads (slip roads) drop to
     * tertiary width — at full class width they turn motorway junctions into
     * spaghetti blobs — and roundabouts are capped at secondary width so they stay
     * crisp rings rather than filling into blobs under the bold major stroke.
     */
    private fun classify(highway: String, junction: String): RoadClass? {
        val base = when (highway) {
            "residential", "unclassified", "living_street" -> RoadClass.MINOR
            "service" -> RoadClass.SERVICE
            "tertiary", "tertiary_link" -> RoadClass.TERTIARY
            "secondary" -> RoadClass.SECONDARY
            "primary" -> RoadClass.PRIMARY
            "motorway", "trunk" -> RoadClass.MAJOR
            "secondary_link", "primary_link", "motorway_link", "trunk_link" -> RoadClass.TERTIARY
            else -> null
        } ?: return null
        return if (junction == "roundabout" || junction == "circular") {
            if (base.rank > RoadClass.SECONDARY.rank) RoadClass.SECONDARY else base
        } else {
            base
        }
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

        // 2. Fetch highways from Overpass (or reuse cached geometry).
        val json = loadOrFetchGeometry(cityName, south, west, north, east)

        // 3 + 4. Parse and render.
        return render(json, widthPx, heightPx, south, north, west, east)
    }

    private fun citySlug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    // Cached geometry is only valid for the exact bounding box it was fetched for, so
    // the key encodes the bbox itself (4 dp ≈ 11 m) — a zoom change or a same-named
    // city at different coordinates gets its own entry rather than stale roads.
    private fun bboxKey(south: Double, west: Double, north: Double, east: Double): String =
        listOf(south, west, north, east).joinToString("_") {
            String.format(java.util.Locale.US, "%.4f", it).replace("-", "m").replace(".", "p")
        }

    private fun loadOrFetchGeometry(
        cityName: String, south: Double, west: Double, north: Double, east: Double,
    ): String {
        val cacheFile = geometryCacheDir?.let {
            it.mkdirs()
            // Drop the legacy slug-only entry; it predates bbox keys and can't be trusted.
            File(it, "${citySlug(cityName)}.json").delete()
            File(it, "${citySlug(cityName)}-${bboxKey(south, west, north, east)}.json")
        }
        if (cacheFile != null && cacheFile.exists()) {
            try {
                return cacheFile.readText()
            } catch (_: Exception) {
                // fall through to a fresh fetch
            }
        }
        val json = fetchOverpass(south, west, north, east)
        if (cacheFile != null) {
            try {
                cacheFile.writeText(json)
            } catch (_: Exception) {
            }
        }
        return json
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
        canvas.drawColor(palette.background)

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
            val roadClass = classify(
                tags.optString("highway", ""), tags.optString("junction", ""),
            ) ?: continue
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

        // Treat a road-less response as a failure rather than caching a blank map.
        if (buckets.values.sumOf { it.size } == 0) {
            throw IOException("Overpass returned no roads")
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (roadClass in RoadClass.values().sortedBy { it.rank }) {
            val paths = buckets[roadClass] ?: continue
            paint.color = palette.roadColours[roadClass.rank]
            paint.strokeWidth = roadClass.widthDp * strokeScale
            for (p in paths) canvas.drawPath(p, paint)
        }

        return bitmap
    }

    /**
     * A colour theme. [roadColours] is indexed by road rank 0..5
     * (MINOR, SERVICE, TERTIARY, SECONDARY, PRIMARY, MAJOR) — minor/dim first,
     * major/bright last. Add presets to [ALL] to offer more options in the UI.
     */
    class Palette(val name: String, val background: Int, val roadColours: IntArray) {
        companion object {
            // Inverted polarity: light slate-teal blocks with a single near-black
            // ink tone for every road — hierarchy comes entirely from stroke width,
            // so thin and thick roads never read as different colours. This is the
            // standard CityWall scheme.
            val CITYWALL = Palette(
                "CityWall", 0xFF252E37.toInt(),
                intArrayOf(
                    0xFF01060D.toInt(), 0xFF01060D.toInt(), 0xFF01060D.toInt(),
                    0xFF01060D.toInt(), 0xFF01060D.toInt(), 0xFF01060D.toInt(),
                ),
            )
            val MIDNIGHT_SLATE = Palette(
                "Midnight Slate", 0xFF1A1E27.toInt(),
                intArrayOf(
                    0xFF333B49.toInt(), 0xFF2C3340.toInt(), 0xFF444E5F.toInt(),
                    0xFF566073.toInt(), 0xFF6B7689.toInt(), 0xFF7E8AA0.toInt(),
                ),
            )
            val CARBON = Palette(
                "Carbon", 0xFF0D0D0D.toInt(),
                intArrayOf(
                    0xFF2A2A2A.toInt(), 0xFF242424.toInt(), 0xFF3A3A3A.toInt(),
                    0xFF4D4D4D.toInt(), 0xFF6A6A6A.toInt(), 0xFF8C8C8C.toInt(),
                ),
            )
            val BLUEPRINT = Palette(
                "Blueprint", 0xFF0A1A2F.toInt(),
                intArrayOf(
                    0xFF16324F.toInt(), 0xFF13283F.toInt(), 0xFF1E4B73.toInt(),
                    0xFF2C6491.toInt(), 0xFF3E86BE.toInt(), 0xFF5BA7E0.toInt(),
                ),
            )
            val AMBER = Palette(
                "Amber", 0xFF1A1408.toInt(),
                intArrayOf(
                    0xFF3A2E14.toInt(), 0xFF33280F.toInt(), 0xFF4D3E1C.toInt(),
                    0xFF6B5526.toInt(), 0xFF8C6F30.toInt(), 0xFFB8923F.toInt(),
                ),
            )
            val FOREST = Palette(
                "Forest", 0xFF0C1A12.toInt(),
                intArrayOf(
                    0xFF1E3A2A.toInt(), 0xFF193024.toInt(), 0xFF2A5640.toInt(),
                    0xFF357056.toInt(), 0xFF3F8A66.toInt(), 0xFF56B084.toInt(),
                ),
            )

            // Light scheme: dark roads on warm paper (mirrors the server's Daylight).
            val DAYLIGHT = Palette(
                "Daylight", 0xFFECEAE3.toInt(),
                intArrayOf(
                    0xFFCBC7BD.toInt(), 0xFFD0CCC2.toInt(), 0xFFB0AB9F.toInt(),
                    0xFF918C80.toInt(), 0xFF6F6A60.toInt(), 0xFF4E4940.toInt(),
                ),
            )

            val DEFAULT = CITYWALL
            val ALL = listOf(CITYWALL, DAYLIGHT, MIDNIGHT_SLATE, CARBON, BLUEPRINT, AMBER, FOREST)
            fun byName(name: String): Palette = ALL.firstOrNull { it.name == name } ?: DEFAULT
        }
    }
}
