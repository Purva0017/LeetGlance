package com.example.myapplication.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.widget.LeetCodeWidget
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// RefreshWorker
// Runs every 3 hours (when network is available) to pull fresh LeetCode data
// and then triggers a Glance widget redraw.
// ─────────────────────────────────────────────────────────────────────────────

class RefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repo = LeetCodeRepository(context)

        // 1. Get the saved username
        val username = repo.getUsername()
        if (username.isBlank()) return Result.success() // nothing to refresh

        // 2. Fetch fresh data from LeetCode (falls back to cache on failure)
        repo.refreshData(username)
            .onFailure {
                // Network failed — cached data is still in DataStore, widget
                // will display the last known values. Retry later.
                return Result.retry()
            }

        // 3. Tell every instance of our Glance widget to redraw
        LeetCodeWidget().updateAll(context)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "leetcode_refresh"

        /**
         * Schedule (or reschedule) a periodic refresh.
         * Call this once from WidgetConfigActivity after the user saves their username.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                repeatInterval = 3,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // restart timer if already scheduled
                request
            )
        }

        /** Cancel the periodic job (e.g., when all widgets are removed). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}