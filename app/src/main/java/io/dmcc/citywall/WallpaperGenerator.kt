package io.dmcc.citywall

import android.graphics.Bitmap

/**
 * Produces a wallpaper bitmap for a place.
 *
 * The cache (see [WallpaperRepository]) keys on [cityName]; the map itself centres
 * on [lat]/[lon]. Implementations may do network I/O and MUST be safe to call off
 * the main thread.
 *
 * Keeping the data source behind this interface means a future static-map or
 * self-hosted variant is a one-file swap.
 */
interface WallpaperGenerator {
    fun generate(cityName: String, lat: Double, lon: Double, widthPx: Int, heightPx: Int): Bitmap
}
