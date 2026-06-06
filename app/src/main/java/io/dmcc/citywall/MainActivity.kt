package io.dmcc.citywall

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Thin control surface built programmatically (no AppCompat/Material). Shows
 * permission status, drives a chained permission request, and offers manual update
 * plus enable/disable of the hourly worker.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var preview: ImageView

    // Requested one at a time, in this order. Background location MUST come on its
    // own prompt after foreground is already granted.
    private val permissionChain = listOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        content.addView(button("Grant location permissions") { requestNextPermission() })
        content.addView(button("Update wallpaper now") { updateNow() })
        content.addView(button("Enable hourly updates") {
            WallpaperScheduler.enablePeriodic(this)
            toast("Hourly updates enabled")
        })
        content.addView(button("Disable hourly updates") {
            WallpaperScheduler.disablePeriodic(this)
            toast("Hourly updates disabled")
        })

        preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { topMargin = 32 }
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

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { topMargin = 16 }
            setOnClickListener { onClick() }
        }

    private fun granted(perm: String): Boolean =
        checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun updateStatus() {
        val fg = if (granted(Manifest.permission.ACCESS_COARSE_LOCATION)) "granted" else "not granted"
        val bg = if (granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) "granted" else "not granted"
        statusView.text = "Foreground location: $fg\nBackground location: $bg"
    }

    /** Requests the first missing permission in the chain. */
    private fun requestNextPermission() {
        for (perm in permissionChain) {
            if (!granted(perm)) {
                requestPermissions(arrayOf(perm), REQ_CODE)
                return
            }
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
        // Advance the chain only when the just-requested permission was granted, so a
        // denial stops the flow instead of re-prompting in a loop.
        val grantedNow = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (grantedNow) requestNextPermission()
    }

    private fun updateNow() {
        toast("Working…")
        Thread {
            try {
                val fix = CityResolver(this).currentCity()
                if (fix == null) {
                    runOnUiThread { toast("No location — grant permission first") }
                    return@Thread
                }
                val (w, h) = WallpaperWorker.screenSize(this)
                val bmp = WallpaperRepository(this).getOrCreate(fix.name, fix.lat, fix.lon, w, h)
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
