package io.dmcc.citywall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

/**
 * Periodic work: resolve the city, get-or-create its wallpaper, apply it. Any failure
 * (no location yet, Overpass hiccup, no network) returns [Result.retry] so the
 * existing wallpaper is left untouched.
 */
class WallpaperWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val settings = Settings(applicationContext)
            // Manual location wins if set; otherwise resolve via GPS, falling back to
            // the nearest city when not in a named locality.
            val fix = settings.manualFix() ?: run {
                val resolver = CityResolver(applicationContext)
                resolver.currentCity(settings.useCapital) ?: run {
                    val la = resolver.lastLat
                    val lo = resolver.lastLon
                    if (la != null && lo != null) PathfinderApi.nearest(la, lo) else null
                }
            } ?: return Result.retry()
            // Travel-only: skip when we're still in the same city as last time.
            if (settings.travelOnly && fix.name == settings.lastSetCity) {
                return Result.success()
            }
            val (w, h) = screenSize(applicationContext)
            val local = MapWallpaperGenerator(
                palette = settings.palette,
                halfHeightMetres = settings.zoomMetres.toDouble(),
                geometryCacheDir = File(applicationContext.filesDir, "geometry"),
            )
            val generator = RemoteWallpaperGenerator(
                settings.palette, local, settings.embassyCountry,
                settings.zoomMetres, settings.riverStyle,
            )
            val bmp = WallpaperRepository(applicationContext, generator)
                .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
            WallpaperBackup.backupOnce(applicationContext) // preserve the original first
            applyWallpaper(applicationContext, bmp, settings.wallpaperFlags())
            settings.lastSetCity = fix.name
            settings.recordCity(fix.name)
            // Claim the city on the Pathfinder leaderboard if opted in and this was a
            // real GPS fix (manual locations are ineligible).
            if (settings.joinWorldMap && settings.manualFix() == null) {
                PathfinderApi.claim(settings.explorerId, fix.name, fix.lat, fix.lon, false)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Applies [bitmap] to the screens selected by [flags] (home/lock/both). */
        fun applyWallpaper(
            ctx: Context,
            bitmap: Bitmap,
            flags: Int = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        ) {
            WallpaperManager.getInstance(ctx).setBitmap(bitmap, null, true, flags)
        }

        /** Screen size in pixels, preferring current window metrics (API 30+). */
        @Suppress("DEPRECATION") // defaultDisplay/getRealMetrics on the API < 30 path
        fun screenSize(ctx: Context): Pair<Int, Int> {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                dm.widthPixels to dm.heightPixels
            }
        }
    }
}
