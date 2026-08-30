package de.wean.wetterkurve.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.wean.wetterkurve.WeatherService
import de.wean.wetterkurve.data.ForecastRepository
import de.wean.wetterkurve.widget.WetterkurveWidgets
import java.util.concurrent.TimeUnit

class ForecastWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = ForecastRepository(applicationContext)
        val state = repo.loadState()
        state.locations.forEach { location ->
            runCatching { repo.refresh(location, force = true) }
        }
        WetterkurveWidgets.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "wetterkurve-forecast"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ForecastWorker>(
                WeatherService.UPDATE_SECONDS.toLong(),
                TimeUnit.SECONDS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
