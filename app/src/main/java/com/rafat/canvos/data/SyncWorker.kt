package com.rafat.canvos.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.api.NetworkOnly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background check every 15 minutes (Android's shortest interval) for new Canvas
 * activity and due-date reminders, plus the Google/phone calendar sync (hourly).
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(NetworkOnly) {
        val app = applicationContext as CanvosApp
        val repo = app.repo.value ?: return@withContext Result.success()
        val s = app.settings.state.value
        if (s.notifications || s.dueReminders != DueReminder.OFF) attempt { Notifier.check(app, repo, s) }
        val prefs = app.getSharedPreferences("notify", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (CalendarSync.Prefs(app).enabled && now - prefs.getLong("calendar_last", 0L) > 55 * 60_000L) {
            attempt {
                CalendarSync.sync(app, repo)
                prefs.edit().putLong("calendar_last", now).apply()
            }
        }
        Result.success()
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Network hiccup or similar; the next run tries again.
        }
    }

    companion object {
        private const val PERIODIC = "canvos-sync"
        private const val ONCE = "canvos-sync-now"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** (Re)schedule according to the current settings. Safe to call often. */
        fun schedule(ctx: Context) {
            val app = ctx.applicationContext as CanvosApp
            val wm = WorkManager.getInstance(ctx)
            val s = app.settings.state.value
            val notify = s.notifications || s.dueReminders != DueReminder.OFF
            val calendar = CalendarSync.Prefs(ctx).enabled
            if (app.repo.value == null || (!calendar && !notify)) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val minutes = if (notify) 15L else 6 * 60L
            val req = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES).setConstraints(online).build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** Check right away (also records the starting point after signing in). */
        fun runOnce(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
            )
        }

        fun cancelAll(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(ctx).cancelUniqueWork(ONCE)
        }
    }
}
