package me.inspect.minimessageformatviewer.rendering

import com.intellij.openapi.editor.colors.EditorColorsManager
import java.awt.Color

object ReadableColorUtil {
    fun adjustForEditor(color: Color): Color {
        val background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        val bgLuma = luma(background)
        val colorLuma = luma(color)

        if (bgLuma > 0.70f && colorLuma > 0.92f) {
            return blend(color, Color.BLACK, 0.45f)
        }

        if (bgLuma < 0.30f && colorLuma < 0.14f) {
            return blend(color, Color.WHITE, 0.45f)
        }

        return color
    }

    private fun luma(color: Color): Float {
        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun blend(base: Color, overlay: Color, amount: Float): Color {
        val clampedAmount = amount.coerceIn(0f, 1f)
        val inv = 1f - clampedAmount
        val red = (base.red * inv + overlay.red * clampedAmount).toInt().coerceIn(0, 255)
        val green = (base.green * inv + overlay.green * clampedAmount).toInt().coerceIn(0, 255)
        val blue = (base.blue * inv + overlay.blue * clampedAmount).toInt().coerceIn(0, 255)
        return Color(red, green, blue)
    }
}
