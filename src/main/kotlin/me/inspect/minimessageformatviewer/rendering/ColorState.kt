package me.inspect.minimessageformatviewer.rendering

import java.awt.Color

data class ColorState(
    val color: Color?,
    val gradientColors: List<Color>?,
    val gradientPhase: Float
)
