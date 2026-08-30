package de.wean.wetterkurve

import android.app.Application
import de.wean.wetterkurve.worker.ForecastWorker

class WetterkurveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ForecastWorker.schedule(this)
    }
}
