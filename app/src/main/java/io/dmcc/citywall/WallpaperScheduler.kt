package io.dmcc.citywall

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the hourly wallpaper refresh. WorkManager persists this across reboot on
 * its own, so there's no BootReceiver. Tune [INTERVAL_HOURS] to change the cadence
 * (15 minutes is the platform floor).
 */
object WallpaperScheduler {
    private const val UNIQUE_WORK = "citywall-periodic"
    private const val INTERVAL_HOURS = 1L

    fun enablePeriodic(ctx: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disablePeriodic(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK)
    }
}
