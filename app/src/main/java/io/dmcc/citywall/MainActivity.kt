package io.dmcc.citywall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Thin control surface built programmatically (no AppCompat/Material). Shows
 * permission status with guidance, drives a chained permission request that deep-links
 * to Settings when a dialog can't grant the permission, and exposes the opt-in
 * settings: update frequency, colour palette, and capital-city mode.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var preview: ImageView
    private lateinit var settings: Settings

    private val freqLabels = listOf(
        "15 minutes", "30 minutes", "Hourly", "Every 3 hours",
        "Every 6 hours", "Every 12 hours", "Daily",
    )
    private val freqMinutes = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)
    private val paletteNames = MapWallpaperGenerator.Palette.ALL.map { it.name }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        content.addView(TextView(this).apply {
            text = "CityWall"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })

        statusView = TextView(this).apply { setPadding(0, 24, 0, 24) }
        content.addView(statusView)

        content.addView(button("Grant permissions") { requestNextPermission() })
        content.addView(button("Open app settings") { openAppSettings() })
        content.addView(button("Update wallpaper now") { updateNow() })

        // --- Update frequency ---
        content.addView(label("Update frequency"))
        content.addView(spinner(freqLabels, freqMinutes.indexOf(settings.updateMinutes)) { pos ->
            settings.updateMinutes = freqMinutes[pos]
        })

        // --- Colour palette ---
        content.addView(label("Colour palette"))
        content.addView(spinner(paletteNames, paletteNames.indexOf(settings.paletteName)) { pos ->
            settings.paletteName = paletteNames[pos]
        })

        // --- Capital-city mode ---
        content.addView(CheckBox(this).apply {
            text = "Use the country's capital instead of my town"
            isChecked = settings.useCapital
            layoutParams = rowParams()
            setOnCheckedChangeListener { _, checked -> settings.useCapital = checked }
        })

        content.addView(button("Enable periodic updates") {
            WallpaperScheduler.enablePeriodic(this)
            toast("Updating ${currentFreqLabel().lowercase()}")
        })
        content.addView(button("Disable periodic updates") {
            WallpaperScheduler.disablePeriodic(this)
            toast("Periodic updates disabled")
        })

        preview = ImageView(this).apply {
            layoutParams = rowParams().apply { topMargin = 32 }
            adjustViewBounds = true
        }
        content.addView(preview)

        setContentView(ScrollView(this).apply { addView(content) })
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // --- Small view builders ---

    private fun rowParams() =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 16 }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            layoutParams = rowParams()
            setOnClickListener { onClick() }
        }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setPadding(0, 24, 0, 4)
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                items,
            )
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            if (selected >= 0) setSelection(selected)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) =
                    onPick(position)

                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

    private fun currentFreqLabel(): String =
        freqLabels[freqMinutes.indexOf(settings.updateMinutes).coerceAtLeast(0)]

    // --- Permissions ---

    private fun granted(perm: String): Boolean =
        checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun updateStatus() {
        val fg = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val bg = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val next = when {
            !fg -> "Next: tap Grant permissions and allow location."
            !bg -> "Next: tap Grant permissions, then choose ‘Allow all the time’ " +
                "on the Settings screen so updates can run in the background."
            else -> "All set — wallpaper updates can run in the background."
        }
        statusView.text = "Foreground location: ${if (fg) "granted" else "not granted"}\n" +
            "Background location: ${if (bg) "granted" else "not granted"}\n\n$next"
    }

    /** Requests the first missing permission, sending the user to Settings when a
     *  dialog can't do the job (background location on Android 11+). */
    private fun requestNextPermission() {
        if (!granted(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_CODE)
            return
        }
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_CODE)
            return
        }
        if (!granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            // Android 11+ won't grant background location from an in-app dialog — the
            // user must pick "Allow all the time" in Settings. Guide them there.
            guideToSettings("Choose ‘Allow all the time’ for Location")
            return
        }
        updateStatus()
        toast("All permissions granted")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
        val perm = permissions.firstOrNull() ?: return
        val grantedNow = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        when {
            // Just granted — advance to the next step in the chain.
            grantedNow -> requestNextPermission()
            // Permanently denied ("Don't allow" twice): a dialog won't reappear, so
            // the only route is Settings.
            !shouldShowRequestPermissionRationale(perm) ->
                guideToSettings("‘${shortName(perm)}’ was denied. Enable it under Permissions.")
            // Soft denial: leave it; the user can tap Grant permissions to retry.
        }
    }

    private fun guideToSettings(message: String) {
        toast(message)
        openAppSettings()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun shortName(perm: String): String = when (perm) {
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
        Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background location"
        else -> perm
    }

    // --- Actions ---

    private fun updateNow() {
        toast("Working…")
        Thread {
            try {
                val fix = CityResolver(this).currentCity(settings.useCapital)
                if (fix == null) {
                    runOnUiThread { toast("No location — grant permission first") }
                    return@Thread
                }
                val (w, h) = WallpaperWorker.screenSize(this)
                val generator = MapWallpaperGenerator(palette = settings.palette)
                val bmp = WallpaperRepository(this, generator)
                    .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
                WallpaperWorker.applyWallpaper(this, bmp)
                runOnUiThread {
                    preview.setImageBitmap(bmp)
                    toast("Wallpaper set: ${fix.name}")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Failed: ${e.message}") }
            }
        }.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_CODE = 1001
    }
}
