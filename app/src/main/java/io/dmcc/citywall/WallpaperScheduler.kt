package io.dmcc.citywall

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic wallpaper refresh. The interval comes from [Settings]
 * (user-configurable). WorkManager persists this across reboot on its own, so there's
 * no BootReceiver. 15 minutes is the platform floor.
 */
object WallpaperScheduler {
    private const val UNIQUE_WORK = "citywall-periodic"

    fun enablePeriodic(ctx: Context) {
        val minutes = Settings(ctx).updateMinutes.coerceAtLeast(15)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        // UPDATE (not KEEP) so re-enabling after changing the frequency applies it.
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun disablePeriodic(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK)
    }
}
