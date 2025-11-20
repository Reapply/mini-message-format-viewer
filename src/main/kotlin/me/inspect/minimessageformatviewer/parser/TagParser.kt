package me.inspect.minimessageformatviewer.parser

import me.inspect.minimessageformatviewer.color.ColorResolver
import java.awt.Color

object TagParser {

    fun parse(text: String, start: Int): Tag? {
        if (text[start] != '<') return null

        val end = text.indexOf('>', start + 1)
        if (end == -1) return null

        val rawContent = text.substring(start + 1, end).trim()
        if (rawContent.isEmpty()) return null

        val isSelfClosing = rawContent.endsWith("/")
        val content = if (isSelfClosing) rawContent.dropLast(1).trimEnd() else rawContent
        if (content.isEmpty()) return null

        val length = end - start + 1

        return when {
            content.equals("reset", ignoreCase = true) ->
                Tag(length, TagType.RESET)

            content.startsWith("/") ->
                parseClosingTag(content.substring(1), length)

            content.lowercase().startsWith("gradient") ->
                parseGradientTag(content, length, isSelfClosing)

            else ->
                parseColorTag(content, length, isSelfClosing)
        }
    }

    private fun parseClosingTag(content: String, length: Int): Tag {
        val isGradient = content.lowercase() == "gradient"
        val color = if (!isGradient) ColorResolver.resolve(content) else null
        return Tag(length, TagType.CLOSE, color)
    }

    private fun parseGradientTag(content: String, length: Int, isSelfClosing: Boolean): Tag? {
        val colonIndex = content.indexOf(':')

        val (colors, phase) = if (colonIndex == -1) {
            getDefaultRainbow() to 0f
        } else {
            parseGradientArgs(content.substring(colonIndex + 1))
        }

        val type = if (isSelfClosing) TagType.SELF_CLOSING else TagType.OPEN
        return Tag(length, type, gradientColors = colors, gradientPhase = phase)
    }

    private fun parseColorTag(content: String, length: Int, isSelfClosing: Boolean): Tag? {
        val color = resolveColorFromContent(content) ?: return null
        val type = if (isSelfClosing) TagType.SELF_CLOSING else TagType.OPEN
        return Tag(length, type, color)
    }

    private fun resolveColorFromContent(content: String): Color? {
        val lower = content.lowercase()

        if (lower.startsWith("#")) {
            return ColorResolver.resolve(lower)
        }

        val separatorIndex = lower.indexOf(':')
        if (separatorIndex > 0) {
            val prefix = lower.take(separatorIndex)
            if (prefix in setOf("color", "colour", "c")) {
                return ColorResolver.resolve(content.substring(separatorIndex + 1))
            }
            return null
        }

        return ColorResolver.resolve(lower)
    }

    private fun parseGradientArgs(argsString: String): Pair<List<Color>, Float> {
        val args = argsString.split(':')
        val colors = mutableListOf<Color>()
        var phase = 0f

        for (arg in args) {
            val trimmed = arg.trim()

            trimmed.toFloatOrNull()?.let {
                phase = it.coerceIn(-1f, 1f)
                return@let
            }

            ColorResolver.resolve(trimmed)?.let { colors.add(it) }
        }

        return (colors.ifEmpty { listOf(Color.WHITE, Color.BLACK) }) to phase
    }

    private fun getDefaultRainbow(): List<Color> = listOf(
        Color(255, 0, 0),
        Color(255, 165, 0),
        Color(255, 255, 0),
        Color(0, 255, 0),
        Color(0, 0, 255),
        Color(75, 0, 130),
        Color(238, 130, 238)
    )
}
