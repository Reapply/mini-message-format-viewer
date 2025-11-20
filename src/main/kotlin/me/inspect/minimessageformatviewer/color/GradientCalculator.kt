package me.inspect.minimessageformatviewer.color

import java.awt.Color
import kotlin.math.floor

object GradientCalculator {

    fun getColor(colors: List<Color>, position: Int, length: Int, phase: Float = 0f): Color {
        if (colors.isEmpty()) return Color.WHITE
        if (colors.size == 1) return colors[0]
        if (length <= 0) return colors[0]

        var gradientPosition = position.toFloat() / length.toFloat()
        gradientPosition = (gradientPosition + phase).coerceIn(0f, 1f)

        val segmentCount = colors.size - 1
        val scaledPosition = gradientPosition * segmentCount
        val colorIndex = floor(scaledPosition).toInt().coerceIn(0, segmentCount - 1)
        val nextColorIndex = (colorIndex + 1).coerceIn(0, colors.size - 1)

        val localPosition = scaledPosition - colorIndex

        return interpolate(colors[colorIndex], colors[nextColorIndex], localPosition)
    }

    private fun interpolate(color1: Color, color2: Color, factor: Float): Color {
        val f = factor.coerceIn(0f, 1f)
        val invF = 1f - f

        val red = (color1.red * invF + color2.red * f).toInt().coerceIn(0, 255)
        val green = (color1.green * invF + color2.green * f).toInt().coerceIn(0, 255)
        val blue = (color1.blue * invF + color2.blue * f).toInt().coerceIn(0, 255)

        return Color(red, green, blue)
    }
}
