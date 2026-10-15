package de.wean.wetterkurve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetImageSwitchTest {
    @Test fun visibleSwitchRespondsImmediatelyBeforeDiskOrWidgetTasksRun() {
        val dispatcher=StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store=androidx.lifecycle.ViewModelStore()
        try {
            val model=WetterkurveViewModel(RuntimeEnvironment.getApplication())
            store.put("widget",model)
            assertFalse(model.ui.value.showWidgetWeatherImages)
            model.setWidgetWeatherImages(true)
            assertTrue("Switch waits for asynchronous persistence",model.ui.value.showWidgetWeatherImages)
            model.setWidgetWeatherImages(false)
            assertFalse("A rapid second tap cannot reverse selection",model.ui.value.showWidgetWeatherImages)
            model.setWidgetWeatherImages(true)
            assertTrue(model.ui.value.showWidgetWeatherImages)
            assertTrue(model.ui.value.showClouds)
            assertFalse(model.ui.value.showWind)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
