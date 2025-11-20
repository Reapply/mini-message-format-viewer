package me.inspect.minimessageformatviewer.color

import com.intellij.ui.ColorUtil
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.Color

object ColorResolver {

    private val DEFAULT_COLORS = mapOf(
        "black" to Color(0x00, 0x00, 0x00),
        "dark_blue" to Color(0x00, 0x00, 0xAA),
        "dark_green" to Color(0x00, 0xAA, 0x00),
        "dark_aqua" to Color(0x00, 0xAA, 0xAA),
        "dark_red" to Color(0xAA, 0x00, 0x00),
        "dark_purple" to Color(0xAA, 0x00, 0xAA),
        "gold" to Color(0xFF, 0xAA, 0x00),
        "gray" to Color(0xAA, 0xAA, 0xAA),
        "dark_gray" to Color(0x55, 0x55, 0x55),
        "blue" to Color(0x55, 0x55, 0xFF),
        "green" to Color(0x55, 0xFF, 0x55),
        "aqua" to Color(0x55, 0xFF, 0xFF),
        "red" to Color(0xFF, 0x55, 0x55),
        "light_purple" to Color(0xFF, 0x55, 0xFF),
        "yellow" to Color(0xFF, 0xFF, 0x55),
        "white" to Color(0xFF, 0xFF, 0xFF),
        "grey" to Color(0xAA, 0xAA, 0xAA),
        "dark_grey" to Color(0x55, 0x55, 0x55)
    )

    fun resolve(input: String): Color? {
        val cleaned = input.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")

        if (cleaned.startsWith("#") && cleaned.length >= 7) {
            val hex = cleaned.substring(1, 7)
            return runCatching { ColorUtil.fromHex(hex) }.getOrNull()
        }

        val settings = runCatching { MiniMessageSettings.getInstance() }.getOrNull()
        settings?.customColors?.get(cleaned.lowercase())?.let { customHex ->
            return runCatching { ColorUtil.fromHex(customHex) }.getOrNull()
        }

        return DEFAULT_COLORS[cleaned.lowercase()]
    }
}
