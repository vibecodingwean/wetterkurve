package de.wean.wetterkurve.widget

internal enum class WeatherBackgroundScene {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_DAY, PARTLY_NIGHT, OVERCAST_DAY, OVERCAST_NIGHT,
    FOG_DAY, FOG_NIGHT, DRIZZLE, RAIN, HEAVY_RAIN, FREEZING_RAIN, SNOW, HEAVY_SNOW,
    SLEET, THUNDERSTORM, HAIL, WIND, SUNRISE, SUNSET, UNKNOWN,
}

/** Precipitation/hazards win over wind and decorative sun transitions. Unknown stays neutral. */
internal fun weatherBackgroundScene(code: Int, isDay: Boolean, wind: Double = 0.0,
    rain: Double? = null, snowfall: Double? = null, solarEvent: String? = null): WeatherBackgroundScene {
    if (code in listOf(95, 96, 99)) return if (code == 95) WeatherBackgroundScene.THUNDERSTORM else WeatherBackgroundScene.HAIL
    if (code in listOf(56, 57, 66, 67)) return WeatherBackgroundScene.FREEZING_RAIN
    if (code in listOf(51, 53, 55, 61, 63, 65, 71, 73, 75, 77, 80, 81, 82, 85, 86)
        && rain != null && rain.isFinite() && rain > 0.0
        && snowfall != null && snowfall.isFinite() && snowfall > 0.0) return WeatherBackgroundScene.SLEET
    return when (code) {
        51, 53, 55 -> WeatherBackgroundScene.DRIZZLE
        61, 63, 80, 81 -> WeatherBackgroundScene.RAIN
        65, 82 -> WeatherBackgroundScene.HEAVY_RAIN
        71, 73, 77, 85 -> WeatherBackgroundScene.SNOW
        75, 86 -> WeatherBackgroundScene.HEAVY_SNOW
        45, 48 -> if (isDay) WeatherBackgroundScene.FOG_DAY else WeatherBackgroundScene.FOG_NIGHT
        in 0..3 -> when {
            wind.isFinite() && wind >= 50.0 -> WeatherBackgroundScene.WIND
            code <= 2 && solarEvent == "sunrise" -> WeatherBackgroundScene.SUNRISE
            code <= 2 && solarEvent == "sunset" -> WeatherBackgroundScene.SUNSET
            code == 0 -> if (isDay) WeatherBackgroundScene.CLEAR_DAY else WeatherBackgroundScene.CLEAR_NIGHT
            code <= 2 -> if (isDay) WeatherBackgroundScene.PARTLY_DAY else WeatherBackgroundScene.PARTLY_NIGHT
            else -> if (isDay) WeatherBackgroundScene.OVERCAST_DAY else WeatherBackgroundScene.OVERCAST_NIGHT
        }
        else -> WeatherBackgroundScene.UNKNOWN
    }
}

/** Only the date-matched, paired local solar times are eligible; polar/missing times stay neutral. */
internal fun currentBackgroundSunEvent(daily: de.wean.wetterkurve.SolarTimes?, now: java.time.LocalDateTime): String? {
    daily ?: return null
    for ((rise, set) in daily.sunrise.zip(daily.sunset)) {
        val sunrise = runCatching { java.time.LocalDateTime.parse(rise) }.getOrNull() ?: continue
        val sunset = runCatching { java.time.LocalDateTime.parse(set) }.getOrNull() ?: continue
        if (sunrise.toLocalDate() != now.toLocalDate() || sunset.toLocalDate() != now.toLocalDate()
            || !sunset.isAfter(sunrise)) continue
        if (kotlin.math.abs(java.time.Duration.between(sunrise, now).seconds) <= 900) return "sunrise"
        if (kotlin.math.abs(java.time.Duration.between(sunset, now).seconds) <= 900) return "sunset"
    }
    return null
}
