package io.dmcc.citywall

import android.app.WallpaperManager
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

    /** Whether the hourly background worker is enabled. */
    var autoUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO, v).apply()

    /**
     * Opt-in to the shared CityWall world map. OFF by default: while off, nothing
     * about the user's location or claimed cities ever leaves the device. Turning it
     * on is the only thing that enables any upload — see docs/gamification.md.
     */
    var joinWorldMap: Boolean
        get() = prefs.getBoolean(KEY_WORLD_MAP, false)
        set(v) = prefs.edit().putBoolean(KEY_WORLD_MAP, v).apply()

    /** A manually chosen city (capital name), or null to use GPS. Manual locations are
     *  preview-only and can never claim a city on the world map (anti-cheat). */
    var manualLocation: String?
        get() = prefs.getString(KEY_MANUAL, null)
        set(v) = prefs.edit().putString(KEY_MANUAL, v).apply()

    /** Dismissed (skipped) optional permission prompts, so they stop nagging. */
    var dismissedBackgroundPrompt: Boolean
        get() = prefs.getBoolean(KEY_DISMISS_BG, false)
        set(v) = prefs.edit().putBoolean(KEY_DISMISS_BG, v).apply()

    var dismissedNotificationsPrompt: Boolean
        get() = prefs.getBoolean(KEY_DISMISS_NOTIF, false)
        set(v) = prefs.edit().putBoolean(KEY_DISMISS_NOTIF, v).apply()

    /** Which screens to apply the wallpaper to: "both" (default), "home", or "lock". */
    var wallpaperTarget: String
        get() = prefs.getString(KEY_TARGET, TARGET_BOTH)!!
        set(v) = prefs.edit().putString(KEY_TARGET, v).apply()

    fun wallpaperFlags(): Int = when (wallpaperTarget) {
        TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
        TARGET_LOCK -> WallpaperManager.FLAG_LOCK
        else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

    val palette: MapWallpaperGenerator.Palette
        get() = MapWallpaperGenerator.Palette.byName(paletteName)

    /** The manual fix if one is set and resolvable, else null. */
    fun manualFix(): CityFix? =
        manualLocation?.let { Capitals.byName(it) }?.let { CityFix(it.name, it.lat, it.lon) }

    companion object {
        const val DEFAULT_MINUTES = 60L
        private const val KEY_MINUTES = "update_minutes"
        private const val KEY_PALETTE = "palette"
        private const val KEY_CAPITAL = "use_capital"
        private const val KEY_AUTO = "auto_update"
        private const val KEY_WORLD_MAP = "join_world_map"
        private const val KEY_MANUAL = "manual_location"
        private const val KEY_DISMISS_BG = "dismiss_background_prompt"
        private const val KEY_DISMISS_NOTIF = "dismiss_notifications_prompt"
        private const val KEY_TARGET = "wallpaper_target"
        const val TARGET_BOTH = "both"
        const val TARGET_HOME = "home"
        const val TARGET_LOCK = "lock"
    }
}
