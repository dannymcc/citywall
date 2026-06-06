package io.dmcc.citywall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the generate-once rule: one PNG per city in internal storage, keyed by a slug
 * of the name. A cache hit skips the network entirely.
 */
class WallpaperRepository(
    private val ctx: Context,
    private val generator: WallpaperGenerator = MapWallpaperGenerator(),
) {
    private val dir: File = File(ctx.filesDir, "wallpapers").apply { mkdirs() }

    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    // Namespace the file by the generator's variant (e.g. palette) so changing the
    // look caches separately rather than serving a stale PNG.
    private fun fileFor(name: String): File {
        val tag = generator.variantKey
        val base = if (tag.isEmpty()) slug(name) else "${slug(name)}-$tag"
        // Cache namespace — bump to drop stale entries (e.g. transient blank renders).
        return File(dir, "$base-c2.png")
    }

    fun isCached(name: String): Boolean = fileFor(name).exists()

    /**
     * Returns the cached PNG if present (no network), otherwise generates it, writes
     * it to disk, and returns it. Generation centres on [lat]/[lon].
     */
    fun getOrCreate(name: String, lat: Double, lon: Double, w: Int, h: Int): Bitmap {
        val f = fileFor(name)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { return it }
        }
        val bmp = generator.generate(name, lat, lon, w, h)
        FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return bmp
    }

    /**
     * Opt-in pre-warm. For each UNCACHED city: forward geocode, generate, then sleep.
     *
     * WARNING: this hits Overpass once per city. Do NOT run it against the public
     * endpoint for a long list, and never call it on launch. Point
     * [MapWallpaperGenerator] at a self-hosted Overpass instance before warming.
     */
    fun warm(cities: List<String>, w: Int, h: Int, perCityDelayMs: Long = 4000L) {
        val resolver = CityResolver(ctx)
        for (city in cities) {
            if (isCached(city)) continue
            val coords = resolver.coordsFor(city) ?: continue
            try {
                getOrCreate(city, coords.first, coords.second, w, h)
            } catch (_: Exception) {
                // skip this city, keep warming the rest
            }
            try {
                Thread.sleep(perCityDelayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    companion object {
        /** Convenience list for [warm]. NOT used automatically anywhere. */
        val MAJOR_CITIES: List<String> = listOf(
            // UK majors
            "London", "Manchester", "Birmingham", "Leeds", "Liverpool", "Sheffield",
            "Bristol", "Newcastle upon Tyne", "Nottingham", "Edinburgh", "Glasgow",
            "Cardiff", "Belfast",
            // Local (Lancashire)
            "Preston", "Chorley", "Wigan", "Lancaster", "Blackpool",
            // EU majors
            "Paris", "Berlin", "Madrid", "Rome", "Amsterdam", "Brussels", "Vienna",
            "Prague", "Lisbon", "Dublin", "Copenhagen", "Stockholm", "Warsaw", "Munich",
        )
    }
}
