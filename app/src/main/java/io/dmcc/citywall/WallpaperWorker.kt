package io.dmcc.citywall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Periodic work: resolve the city, get-or-create its wallpaper, apply it. Any failure
 * (no location yet, Overpass hiccup, no network) returns [Result.retry] so the
 * existing wallpaper is left untouched.
 */
class WallpaperWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val settings = Settings(applicationContext)
            val fix = CityResolver(applicationContext).currentCity(settings.useCapital)
                ?: return Result.retry()
            val (w, h) = screenSize(applicationContext)
            val generator = MapWallpaperGenerator(palette = settings.palette)
            val bmp = WallpaperRepository(applicationContext, generator)
                .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
            applyWallpaper(applicationContext, bmp)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Applies [bitmap] to both the home and lock screens. Reused by the activity. */
        fun applyWallpaper(ctx: Context, bitmap: Bitmap) {
            WallpaperManager.getInstance(ctx).setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
            )
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
