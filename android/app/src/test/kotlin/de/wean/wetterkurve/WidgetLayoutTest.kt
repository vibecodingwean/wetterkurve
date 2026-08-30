package de.wean.wetterkurve

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetLayoutTest {
    private val appRoot = File("src/main")

    @Test
    fun temperatureWidget_isThinAndFullWidthByDefault() {
        val xml = File(appRoot, "res/xml/wetterkurve_compact_info.xml").readText()
        assertTrue(xml, xml.contains("targetCellWidth=\"5\""))
        assertTrue(xml, xml.contains("targetCellHeight=\"1\""))
        assertTrue(xml, xml.contains("minWidth=\"320dp\""))
        assertTrue(xml, xml.contains("minHeight=\"48dp\""))
        assertTrue(xml, xml.contains("resizeMode=\"horizontal\""))
        assertTrue(xml, xml.contains("widget_preview_compact"))
    }

    @Test
    fun chartWidget_hasOwnPreview() {
        val xml = File(appRoot, "res/xml/wetterkurve_chart_info.xml").readText()
        assertTrue(xml, xml.contains("targetCellWidth=\"5\""))
        assertTrue(xml, xml.contains("targetCellHeight=\"3\""))
        assertTrue(xml, xml.contains("widget_preview_chart"))
        assertFalse(xml, xml.contains("@drawable/clear"))
    }

    @Test
    fun appScreen_isConfigOnly_withoutChart() {
        val screen = File(appRoot, "kotlin/de/wean/wetterkurve/WetterkurveScreen.kt").readText()
        assertFalse(screen, screen.contains("ChartCard"))
        assertFalse(screen, screen.contains("asImageBitmap"))
        assertTrue(screen, screen.contains("statusBarsPadding"))
        assertTrue(screen, screen.contains("paintLegendSample"))
        assertTrue(screen, screen.contains("model.t(\"chartRainScale\")"))
        assertTrue(screen, screen.contains("ChartLegend"))
        assertTrue(
            screen,
            screen.contains("Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp))"),
        )
        assertFalse(screen, screen.contains("model.t(\"chartLegend\")"))
    }

    @Test
    fun chartDrawsCloudOverlayAndWindCurve() {
        val renderer = File(appRoot, "kotlin/de/wean/wetterkurve/chart/ChartRenderer.kt").readText()
        val widgets = File(appRoot, "kotlin/de/wean/wetterkurve/widget/WetterkurveWidgets.kt").readText()
        assertTrue(renderer, renderer.contains("drawCloudStrip"))
        assertTrue(renderer, renderer.contains("cloudCover"))
        assertTrue(renderer, renderer.contains("dayPlotRgb"))
        assertTrue(renderer, renderer.contains("nightPlotRgb"))
        assertTrue(renderer, renderer.contains("cloudGrayRgb"))
        assertTrue(renderer, renderer.contains("#00E272"))
        assertTrue(renderer, renderer.contains("yWind"))
        assertTrue(renderer, renderer.contains("paintLegendSample"))
        assertTrue(renderer, renderer.contains("LegendSample"))
        assertTrue(renderer, renderer.contains("DashPathEffect(floatArrayOf(dash, dash)"))
        assertTrue(widgets, widgets.contains("GlanceAppWidget"))
        assertFalse(File(appRoot, "kotlin/de/wean/wetterkurve/widget/WidgetUpdater.kt").exists())
    }

    @Test
    fun chartWidget_togglesCloudAndWindIndependently() {
        val renderer = File(appRoot, "kotlin/de/wean/wetterkurve/chart/ChartRenderer.kt").readText()
        val widgets = File(appRoot, "kotlin/de/wean/wetterkurve/widget/WetterkurveWidgets.kt").readText()
        val face = File(appRoot, "kotlin/de/wean/wetterkurve/widget/ChartWidgetFace.kt").readText()
        val screen = File(appRoot, "kotlin/de/wean/wetterkurve/WetterkurveScreen.kt").readText()
        val model = File(appRoot, "kotlin/de/wean/wetterkurve/WetterkurveViewModel.kt").readText()
        assertTrue(renderer, renderer.contains("if (showClouds)"))
        assertTrue(renderer, renderer.contains("if (showWind)"))
        assertTrue(face, face.contains("showClouds"))
        assertTrue(face, face.contains("showWind"))
        assertFalse(face, face.contains("layerReserve"))
        assertFalse(widgets, widgets.contains("ToggleChartLayerAction"))
        assertFalse(widgets, widgets.contains("Alignment.BottomEnd"))
        assertFalse(widgets, widgets.contains("LayerChip"))
        assertTrue(model, model.contains("fun toggleClouds()"))
        assertTrue(model, model.contains("fun toggleWind()"))
        assertTrue(model, model.contains("showClouds = !state.showClouds"))
        assertTrue(model, model.contains("showWind = !state.showWind"))
        assertTrue(screen, screen.contains("model.toggleClouds()"))
        assertTrue(screen, screen.contains("model.toggleWind()"))
        assertTrue(screen, screen.contains("model.t(\"clouds\")"))
        assertTrue(screen, screen.contains("model.t(\"wind\")"))
    }
}
