package io.dmcc.citywall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** The most recently shown/set wallpaper, persisted so it appears instantly on open
 *  (no location lookup or network). The accompanying city/source live in [Settings]. */
object LastPreview {
    private fun file(ctx: Context): File = File(ctx.filesDir, "last-preview.png")

    fun save(ctx: Context, bitmap: Bitmap) {
        try {
            FileOutputStream(file(ctx)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context): Bitmap? {
        return try {
            val f = file(ctx)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }
}
