package io.dmcc.citywall

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Posts the optional "new city" notification when a background update changes the
 * wallpaper to a place you weren't in before. Gated behind [Settings.notifyOnChange]
 * and the runtime POST_NOTIFICATIONS permission (33+); a no-op if either is off.
 */
object Notifier {
    private const val CHANNEL_ID = "citywall_updates"
    private const val NOTIFICATION_ID = 1001

    fun notifyCityChanged(context: Context, cityName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper updates",
                NotificationManager.IMPORTANCE_LOW, // quiet: no sound, just appears
            ).apply { description = "Tells you when CityWall sets a wallpaper for a new city." }
            manager.createNotificationChannel(channel)
        }

        // Tapping it opens the app on the wallpaper tab.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pending = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New city — $cityName")
            .setContentText("Your wallpaper is now a map of $cityName.")
            .setAutoCancel(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
