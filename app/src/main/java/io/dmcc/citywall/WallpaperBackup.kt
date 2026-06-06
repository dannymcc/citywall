package io.dmcc.citywall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream

/**
 * Best-effort backup of the wallpaper that was set before CityWall first changed it,
 * so the user can restore it.
 *
 * Caveat: on Android 14+ the OS restricts reading a wallpaper the calling app didn't
 * set (it returns the default for privacy). So [backupOnce] captures the real original
 * reliably only on older devices or for wallpapers CityWall set itself. [available]
 * reflects whether we actually have something to restore.
 */
object WallpaperBackup {
    private const val DIR = "backup"
    private const val FILE = "original-wallpaper.png"

    private fun file(ctx: Context): File =
        File(File(ctx.filesDir, DIR).apply { mkdirs() }, FILE)

    fun available(ctx: Context): Boolean = file(ctx).exists()

    /** Save the current wallpaper once. No-op if a backup already exists, so we never
     *  overwrite the user's true original with a CityWall map. */
    fun backupOnce(ctx: Context) {
        val target = file(ctx)
        if (target.exists()) return
        try {
            val drawable: Drawable = WallpaperManager.getInstance(ctx).drawable ?: return
            val bitmap = drawableToBitmap(drawable) ?: return
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
            // Reading the wallpaper can be denied (Android 14+) — leave no backup.
        }
    }

    /** Restore the backed-up wallpaper to home and lock screens. Returns false if none. */
    fun restore(ctx: Context): Boolean {
        val source = file(ctx)
        if (!source.exists()) return false
        return try {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return false
            WallpaperManager.getInstance(ctx).setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}
