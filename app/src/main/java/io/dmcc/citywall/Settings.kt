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

    /** ISO 3166-1 alpha-2 code of a country whose embassies to mark on the map, or
     *  null for none. */
    var embassyCountry: String?
        get() = prefs.getString(KEY_EMBASSY, null)
        set(v) = prefs.edit().putString(KEY_EMBASSY, v).apply()

    /** Zoom: half the view height in metres (smaller = closer). */
    var zoomMetres: Int
        get() = prefs.getInt(KEY_ZOOM, 2200)
        set(v) = prefs.edit().putInt(KEY_ZOOM, v).apply()

    /** How rivers/water are drawn: "off", "subtle" (default), or "bold". */
    var riverStyle: String
        get() = prefs.getString(KEY_RIVER, "subtle")!!
        set(v) = prefs.edit().putString(KEY_RIVER, v).apply()

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

    /** A manually chosen place name (any location on earth), or null to use GPS.
     *  Manual locations are preview-only and can never claim a city (anti-cheat). */
    val manualLocation: String?
        get() = prefs.getString(KEY_MANUAL, null)

    fun setManual(name: String, lat: Double, lon: Double) {
        prefs.edit()
            .putString(KEY_MANUAL, name)
            .putString(KEY_MANUAL_LAT, lat.toString())
            .putString(KEY_MANUAL_LON, lon.toString())
            .apply()
    }

    fun clearManual() {
        prefs.edit().remove(KEY_MANUAL).remove(KEY_MANUAL_LAT).remove(KEY_MANUAL_LON).apply()
    }

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

    /** A stable, anonymous explorer ID for this install — the identity shown on the
     *  Pathfinder map. Generated once on first read and persisted. */
    val explorerId: String
        get() {
            prefs.getString(KEY_EXPLORER, null)?.let { return it }
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val id = "Explorer-" + (1..5).map { alphabet.random() }.joinToString("")
            prefs.edit().putString(KEY_EXPLORER, id).apply()
            return id
        }

    /** Whether the first-launch onboarding has been completed. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    /** Background updates only change the wallpaper when the city changes ("travel"). */
    var travelOnly: Boolean
        get() = prefs.getBoolean(KEY_TRAVEL_ONLY, true)
        set(v) = prefs.edit().putBoolean(KEY_TRAVEL_ONLY, v).apply()

    /** The last city the background worker set (for travel-only updates). */
    var lastSetCity: String?
        get() = prefs.getString(KEY_LAST_CITY, null)
        set(v) = prefs.edit().putString(KEY_LAST_CITY, v).apply()

    fun recordCity(name: String) {
        val s = prefs.getStringSet(KEY_VISITED, emptySet())!!.toMutableSet()
        if (s.add(name)) prefs.edit().putStringSet(KEY_VISITED, s).apply()
    }

    fun visitedCities(): List<String> =
        prefs.getStringSet(KEY_VISITED, emptySet())!!.sorted()

    val palette: MapWallpaperGenerator.Palette
        get() = MapWallpaperGenerator.Palette.byName(paletteName)

    /** The manual fix if one is set, else null. */
    fun manualFix(): CityFix? {
        val name = prefs.getString(KEY_MANUAL, null) ?: return null
        val lat = prefs.getString(KEY_MANUAL_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_MANUAL_LON, null)?.toDoubleOrNull() ?: return null
        return CityFix(name, lat, lon)
    }

    companion object {
        const val DEFAULT_MINUTES = 60L
        private const val KEY_MINUTES = "update_minutes"
        private const val KEY_PALETTE = "palette"
        private const val KEY_CAPITAL = "use_capital"
        private const val KEY_AUTO = "auto_update"
        private const val KEY_WORLD_MAP = "join_world_map"
        private const val KEY_EMBASSY = "embassy_country"
        private const val KEY_ZOOM = "zoom_metres"
        private const val KEY_RIVER = "river_style"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_TRAVEL_ONLY = "travel_only"
        private const val KEY_LAST_CITY = "last_set_city"
        // v2: reset — earlier builds recorded manual locations here by mistake.
        private const val KEY_VISITED = "visited_cities_v2"
        private const val KEY_MANUAL = "manual_location"
        private const val KEY_MANUAL_LAT = "manual_lat"
        private const val KEY_MANUAL_LON = "manual_lon"
        private const val KEY_DISMISS_BG = "dismiss_background_prompt"
        private const val KEY_DISMISS_NOTIF = "dismiss_notifications_prompt"
        private const val KEY_EXPLORER = "explorer_id"
        private const val KEY_TARGET = "wallpaper_target"
        const val TARGET_BOTH = "both"
        const val TARGET_HOME = "home"
        const val TARGET_LOCK = "lock"
    }
}
