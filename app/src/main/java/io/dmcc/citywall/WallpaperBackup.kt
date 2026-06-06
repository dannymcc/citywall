package io.dmcc.citywall

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Best-effort backup of the wallpaper that was set before CityWall first changed it,
 * so the user can restore it. Keeps an internal copy (used by [restore]) and also
 * exports a visible copy to a "CityWall" album in the gallery so it isn't lost.
 *
 * Caveat: on Android 14+ the OS restricts reading a wallpaper the calling app didn't
 * set (it returns the default for privacy), so the captured original is only reliable
 * on older devices or for wallpapers CityWall set itself. [available] reflects whether
 * we actually have something to restore.
 */
object WallpaperBackup {
    private const val DIR = "citywall"
    private const val FILE = "original-wallpaper.png"
    private const val ALBUM = "CityWall"

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
            exportToGallery(ctx, bitmap) // visible copy in a CityWall album
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

    /** Write a visible copy into Pictures/CityWall via MediaStore (API 29+, no permission). */
    private fun exportToGallery(ctx: Context, bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "citywall-original-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (_: Exception) {
            // Gallery export is a nicety; internal backup already succeeded.
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
