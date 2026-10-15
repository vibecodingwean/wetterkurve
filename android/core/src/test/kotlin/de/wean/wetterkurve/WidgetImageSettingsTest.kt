package de.wean.wetterkurve

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WidgetImageSettingsTest {
    @Test fun selectedWidgetImageOptionSurvivesSaveAndReload() {
        val selected = SettingsStore.parse("""{"showWidgetWeatherImages":true,"showWind":true,"showFeelsLike":true,"language":"de"}""")
        val saved = Json.parseToJsonElement(SettingsStore.serialize(selected)).jsonObject
        assertEquals("true", saved["showWidgetWeatherImages"]?.jsonPrimitive?.content)
        val reloaded = Json.parseToJsonElement(SettingsStore.serialize(SettingsStore.parse(saved.toString()))).jsonObject
        assertEquals(saved, reloaded)
        assertEquals(true, selected.showWind)
        assertEquals(true, selected.showFeelsLike)
        assertEquals("de", selected.language)
    }
    @Test fun oldAndFreshSettingsKeepTheEstablishedGradientByDefault() {
        for (state in listOf(SettingsStore.defaultState(), SettingsStore.parse("""{"showWind":true}"""))) {
            val saved = Json.parseToJsonElement(SettingsStore.serialize(state)).jsonObject
            assertEquals("false", saved["showWidgetWeatherImages"]?.jsonPrimitive?.content)
        }
    }
}
