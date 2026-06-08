package io.dmcc.citywall

import android.content.Context
import java.io.File

/**
 * Builds the [WallpaperGenerator] for the current settings. Centralised so the UI and
 * the background worker stay in step — in particular the on-device-only choice, which
 * bypasses the CityWall server entirely (see [Settings.renderOnDevice]).
 */
object WallpaperFactory {
    fun forSettings(context: Context, settings: Settings): WallpaperGenerator {
        val onDevice = MapWallpaperGenerator(
            palette = settings.palette,
            halfHeightMetres = settings.zoomMetres.toDouble(),
            geometryCacheDir = File(context.filesDir, "geometry"),
        )
        // On-device-only: draw locally and never contact our server. Otherwise prefer
        // the server (instant capitals, rivers, embassies) with on-device as fallback.
        return if (settings.renderOnDevice) {
            onDevice
        } else {
            RemoteWallpaperGenerator(
                settings.palette, onDevice, settings.embassyCountry,
                settings.zoomMetres, settings.riverStyle,
            )
        }
    }
}
