package io.dmcc.citywall

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches a server-rendered wallpaper from the CityWall backend for instant results —
 * capitals and previously-claimed places are pre-rendered there, so there's no wait
 * and rivers are drawn. Falls back to on-device rendering if the server is
 * unavailable, so the app still works offline.
 *
 * variantKey matches [MapWallpaperGenerator] (palette slug) so the on-disk cache is
 * shared whether a wallpaper came from the server or was drawn locally.
 */
class RemoteWallpaperGenerator(
    private val palette: MapWallpaperGenerator.Palette,
    private val fallback: WallpaperGenerator,
    private val serverUrl: String = PathfinderApi.SERVER_URL,
) : WallpaperGenerator {

    override val variantKey: String =
        palette.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    override fun generate(cityName: String, lat: Double, lon: Double, widthPx: Int, heightPx: Int): Bitmap {
        try {
            val q = "name=${enc(cityName)}&lat=$lat&lon=$lon&palette=${enc(palette.name)}&w=$widthPx&h=$heightPx"
            val conn = (URL("$serverUrl/wallpaper?$q").openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 90_000
            }
            try {
                if (conn.responseCode in 200..299) {
                    val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) return bmp
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            // fall through to on-device generation
        }
        return fallback.generate(cityName, lat, lon, widthPx, heightPx)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
