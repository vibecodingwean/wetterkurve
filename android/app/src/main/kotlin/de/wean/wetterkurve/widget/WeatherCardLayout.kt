package de.wean.wetterkurve.widget

/** Baselines in density-independent card units, shared by drawing and narrow-size tests. */
internal data class WeatherCardLayout(
    val city: Float,
    val temperature: Float,
    val temperatureSize: Float,
    val condition: Float,
    val range: Float?,
    val hourTime: Float? = null,
    val hourIcon: Float? = null,
    val hourIconSize: Float = 0f,
    val hourTemperature: Float? = null,
    val hourTemperatureSize: Float = 0f,
)

internal fun weatherCardLayout(height: Float, medium: Boolean): WeatherCardLayout = when {
    medium && height < 165f -> WeatherCardLayout(
        city = 28f, temperature = 51f, temperatureSize = 20f,
        condition = 38f, range = 52f,
        hourTime = height - 34f, hourIcon = height - 22f,
        hourIconSize = 15f, hourTemperature = height - 4f, hourTemperatureSize = 10f,
    )
    medium -> WeatherCardLayout(
        city = 31f, temperature = 79f, temperatureSize = 43f,
        condition = 72f, range = 88f,
        hourTime = height * 0.59f, hourIcon = height * 0.59f + 24f,
        hourIconSize = 23f, hourTemperature = height * 0.59f + 49f,
        hourTemperatureSize = 13f,
    )
    height < 145f -> WeatherCardLayout(
        city = 31f, temperature = 68f, temperatureSize = 31f,
        condition = height - 8f, range = null,
    )
    else -> WeatherCardLayout(
        city = 31f, temperature = 79f, temperatureSize = 39f,
        condition = height - 31f, range = height - 15f,
    )
}

internal fun fitCardText(value: String, maxWidth: Float, measure: (String) -> Float): String {
    if (measure(value) <= maxWidth) return value
    var prefix = value
    while (prefix.isNotEmpty() && measure("$prefix…") > maxWidth) prefix = prefix.dropLast(1)
    return if (prefix.isEmpty()) "" else "$prefix…"
}
