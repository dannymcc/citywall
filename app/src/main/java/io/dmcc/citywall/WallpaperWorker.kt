package io.dmcc.citywall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
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

        /** Screen size in pixels, preferring current window metrics. Reused by the activity. */
        fun screenSize(ctx: Context): Pair<Int, Int> {
            return try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } catch (_: Exception) {
                val dm = ctx.resources.displayMetrics
                dm.widthPixels to dm.heightPixels
            }
        }
    }
}
