package de.wean.wetterkurve.widget

internal data class WeatherCardPalette(val start: Int, val end: Int)

/** Opaque gradients keep white labels readable while reflecting the current sky. */
internal fun weatherCardPalette(icon: String, isDay: Boolean): WeatherCardPalette {
    val sky = icon.removeSuffix("-night")
    val (start, end) = if (isDay) when (sky) {
        "clear" -> 0xFF156995 to 0xFF477A80 // blue with a warm, sunny tint
        "partly-cloudy" -> 0xFF365F77 to 0xFF507683
        "cloudy" -> 0xFF475563 to 0xFF64717C
        "fog" -> 0xFF56616A to 0xFF6C767F
        "rain", "drizzle" -> 0xFF294358 to 0xFF4E6174
        "snow" -> 0xFF3E647B to 0xFF537987
        "thunderstorm" -> 0xFF352C53 to 0xFF574768
        else -> 0xFF1E6686 to 0xFF426D80
    } else when (sky) {
        "clear" -> 0xFF101C36 to 0xFF263756
        "partly-cloudy" -> 0xFF14223A to 0xFF2D4059
        "cloudy" -> 0xFF161E2D to 0xFF354152
        "fog" -> 0xFF1B2530 to 0xFF3E4A57
        "rain", "drizzle" -> 0xFF101D2C to 0xFF293F56
        "snow" -> 0xFF152B40 to 0xFF3A536A
        "thunderstorm" -> 0xFF171125 to 0xFF30253E
        else -> 0xFF14223A to 0xFF2D4059
    }
    return WeatherCardPalette(start.toInt(), end.toInt())
}
