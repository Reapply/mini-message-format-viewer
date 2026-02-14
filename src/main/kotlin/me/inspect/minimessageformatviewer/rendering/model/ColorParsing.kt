package me.inspect.minimessageformatviewer.rendering.model

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import java.awt.Color

object ColorParsing {
    private val legacyCodeColors = mapOf(
        '0' to Color(0x00, 0x00, 0x00),
        '1' to Color(0x00, 0x00, 0xAA),
        '2' to Color(0x00, 0xAA, 0x00),
        '3' to Color(0x00, 0xAA, 0xAA),
        '4' to Color(0xAA, 0x00, 0x00),
        '5' to Color(0xAA, 0x00, 0xAA),
        '6' to Color(0xFF, 0xAA, 0x00),
        '7' to Color(0xAA, 0xAA, 0xAA),
        '8' to Color(0x55, 0x55, 0x55),
        '9' to Color(0x55, 0x55, 0xFF),
        'a' to Color(0x55, 0xFF, 0x55),
        'b' to Color(0x55, 0xFF, 0xFF),
        'c' to Color(0xFF, 0x55, 0x55),
        'd' to Color(0xFF, 0x55, 0xFF),
        'e' to Color(0xFF, 0xFF, 0x55),
        'f' to Color(0xFF, 0xFF, 0xFF)
    )

    fun parseNamedOrHex(raw: String, customColors: Map<String, String>): Color? {
        val token = raw.trim().removePrefix("#")
        if (token.length == 6 && token.all { it.isHexDigit() }) {
            return parseHex(token)
        }

        customColors[token.lowercase()]?.let { custom ->
            val normalized = custom.removePrefix("#")
            if (normalized.length == 6 && normalized.all { it.isHexDigit() }) {
                return parseHex(normalized)
            }
        }

        val named = NamedTextColor.NAMES.value(token.lowercase()) ?: return null
        return Color(named.value())
    }

    fun parseTagColor(rawTagContent: String, customColors: Map<String, String>): Color? {
        val cleaned = rawTagContent
            .trim()
            .removePrefix("/")
            .removeSuffix("/")
            .trim()

        if (cleaned.isEmpty()) return null

        val lower = cleaned.lowercase()

        if (lower.startsWith("gradient:")) {
            val firstColorToken = cleaned.substringAfter(':')
                .split(':')
                .firstOrNull { it.isNotBlank() && it.trim().toFloatOrNull() == null }
                ?: return null
            return parseNamedOrHex(firstColorToken, customColors)
        }

        if (lower.startsWith("rainbow")) {
            return Color(0xFF, 0x55, 0xFF)
        }

        if (':' in cleaned) {
            val prefix = cleaned.substringBefore(':').lowercase()
            val value = cleaned.substringAfter(':')
            return when (prefix) {
                "color", "colour", "c" -> parseNamedOrHex(value, customColors)
                else -> null
            }
        }

        return parseNamedOrHex(cleaned, customColors)
    }

    fun fromLegacyCode(code: Char): Color? {
        return legacyCodeColors[code.lowercaseChar()]
    }

    fun fromAdventureTextColor(color: TextColor?): Color? {
        color ?: return null
        return Color(color.value())
    }

    private fun parseHex(hex: String): Color {
        return Color(
            hex.substring(0, 2).toInt(16),
            hex.substring(2, 4).toInt(16),
            hex.substring(4, 6).toInt(16)
        )
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
