package io.dmcc.citywall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-hosted in-app updates: checks the backend for the latest sideload build and,
 * if newer, downloads the APK and launches the system installer. All network calls
 * block and must run off the main thread.
 */
object Updater {
    data class Latest(val versionName: String, val notes: String)

    /** The latest published build, or null if unavailable / server unreachable. */
    fun latest(): Latest? {
        return try {
            val conn = (URL("${PathfinderApi.SERVER_URL}/app/latest").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 15000; readTimeout = 20000 }
            try {
                if (conn.responseCode !in 200..299) return null
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (!o.optBoolean("available", false)) return null
                Latest(o.optString("versionName"), o.optString("notes"))
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Download the APK to external cache; returns the file or null on failure. */
    fun download(context: Context): File? {
        return try {
            val conn = (URL("${PathfinderApi.SERVER_URL}/app/citywall.apk").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 20000; readTimeout = 120000 }
            try {
                if (conn.responseCode !in 200..299) return null
                val out = File(context.externalCacheDir, "citywall-update.apk")
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                out
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launch the installer for [apk]. Returns true if launched, or false if the user
     * was sent to enable "install unknown apps" first (they should retry afterwards).
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    }
}
