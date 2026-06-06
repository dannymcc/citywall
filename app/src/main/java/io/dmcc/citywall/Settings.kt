package io.dmcc.citywall

import android.content.Context

/**
 * Lightweight user preferences backed by SharedPreferences (no extra dependency).
 * All settings are opt-in; the defaults reproduce the original behaviour:
 * hourly updates, the Midnight Slate palette, and the real local town.
 */
class Settings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("citywall", Context.MODE_PRIVATE)

    /** How often the periodic worker runs, in minutes. 15 is the platform floor. */
    var updateMinutes: Long
        get() = prefs.getLong(KEY_MINUTES, DEFAULT_MINUTES)
        set(v) = prefs.edit().putLong(KEY_MINUTES, v).apply()

    var paletteName: String
        get() = prefs.getString(KEY_PALETTE, MapWallpaperGenerator.Palette.DEFAULT.name)!!
        set(v) = prefs.edit().putString(KEY_PALETTE, v).apply()

    /** When true, map the country's capital instead of the user's actual town. */
    var useCapital: Boolean
        get() = prefs.getBoolean(KEY_CAPITAL, false)
        set(v) = prefs.edit().putBoolean(KEY_CAPITAL, v).apply()

    val palette: MapWallpaperGenerator.Palette
        get() = MapWallpaperGenerator.Palette.byName(paletteName)

    companion object {
        const val DEFAULT_MINUTES = 60L
        private const val KEY_MINUTES = "update_minutes"
        private const val KEY_PALETTE = "palette"
        private const val KEY_CAPITAL = "use_capital"
    }
}
