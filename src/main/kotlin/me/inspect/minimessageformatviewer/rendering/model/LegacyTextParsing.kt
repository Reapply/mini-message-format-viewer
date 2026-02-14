package me.inspect.minimessageformatviewer.rendering.model

import java.awt.Color

data class LegacyStyledCharacter(
    val color: Color?,
    val bold: Boolean,
    val italic: Boolean,
    val underlined: Boolean,
    val strikethrough: Boolean,
    val obfuscated: Boolean
)

data class LegacySourceMapping(
    val plainText: String,
    val sourceIndices: List<Int>,
    val highlights: List<TagHighlight>
)

object LegacyTextParsing {
    private val colorCodes = ('0'..'9') + ('a'..'f')
    private val formatCodes = setOf('k', 'l', 'm', 'n', 'o', 'r')

    fun mapSource(source: String): LegacySourceMapping {
        val plain = StringBuilder(source.length)
        val sourceIndices = mutableListOf<Int>()
        val highlights = mutableListOf<TagHighlight>()

        var i = 0
        while (i < source.length) {
            if (i + 1 < source.length && source[i].isLegacyPrefix()) {
                val prefix = source[i]
                val next = source[i + 1]

                val compactHexLength = compactHexLength(source, i)
                if (compactHexLength > 0) {
                    val hex = source.substring(i + 2, i + 8)
                    highlights.add(TagHighlight(i, i + compactHexLength, parseHex(hex)))
                    i += compactHexLength
                    continue
                }

                val verboseHexLength = verboseHexLength(source, i, prefix)
                if (verboseHexLength > 0) {
                    val color = parseVerboseHexColor(source, i)
                    highlights.add(TagHighlight(i, i + verboseHexLength, color))
                    i += verboseHexLength
                    continue
                }

                if (isLegacyCode(next)) {
                    highlights.add(
                        TagHighlight(
                            start = i,
                            end = i + 2,
                            color = ColorParsing.fromLegacyCode(next)
                        )
                    )
                    i += 2
                    continue
                }
            }

            plain.append(source[i])
            sourceIndices.add(i)
            i++
        }

        return LegacySourceMapping(
            plainText = plain.toString(),
            sourceIndices = sourceIndices,
            highlights = highlights
        )
    }

    fun normalizeToSection(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0

        while (i < source.length) {
            if (i + 1 < source.length && source[i].isLegacyPrefix()) {
                val prefix = source[i]
                val next = source[i + 1]

                val compactHexLength = compactHexLength(source, i)
                if (compactHexLength > 0) {
                    val hex = source.substring(i + 2, i + 8)
                    out.append('§')
                        .append('x')
                    hex.forEach { digit ->
                        out.append('§').append(digit)
                    }
                    i += compactHexLength
                    continue
                }

                val verboseHexLength = verboseHexLength(source, i, prefix)
                if (verboseHexLength > 0) {
                    out.append('§').append('x')
                    var cursor = i + 2
                    repeat(6) {
                        out.append('§').append(source[cursor + 1])
                        cursor += 2
                    }
                    i += verboseHexLength
                    continue
                }

                if (isLegacyCode(next)) {
                    out.append('§').append(next.lowercaseChar())
                    i += 2
                    continue
                }
            }

            out.append(source[i])
            i++
        }

        return out.toString()
    }

    fun parseStyledCharacters(serializedLegacy: String): List<LegacyStyledCharacter> {
        data class StyleState(
            val color: Color? = null,
            val bold: Boolean = false,
            val italic: Boolean = false,
            val underlined: Boolean = false,
            val strikethrough: Boolean = false,
            val obfuscated: Boolean = false
        )

        var style = StyleState()
        val result = mutableListOf<LegacyStyledCharacter>()
        var i = 0

        while (i < serializedLegacy.length) {
            val ch = serializedLegacy[i]
            if (ch == '§' && i + 1 < serializedLegacy.length) {
                val next = serializedLegacy[i + 1].lowercaseChar()

                if (next == '#'
                    && i + 7 < serializedLegacy.length
                    && serializedLegacy.substring(i + 2, i + 8).all { it.isHexDigit() }
                ) {
                    val color = parseHex(serializedLegacy.substring(i + 2, i + 8))
                    style = StyleState(
                        color = color,
                        bold = false,
                        italic = false,
                        underlined = false,
                        strikethrough = false,
                        obfuscated = false
                    )
                    i += 8
                    continue
                }

                val verboseHexLength = verboseHexLength(serializedLegacy, i, '§')
                if (verboseHexLength > 0) {
                    val color = parseVerboseHexColor(serializedLegacy, i)
                    style = StyleState(
                        color = color,
                        bold = false,
                        italic = false,
                        underlined = false,
                        strikethrough = false,
                        obfuscated = false
                    )
                    i += verboseHexLength
                    continue
                }

                when {
                    next in colorCodes -> {
                        style = StyleState(
                            color = ColorParsing.fromLegacyCode(next),
                            bold = false,
                            italic = false,
                            underlined = false,
                            strikethrough = false,
                            obfuscated = false
                        )
                        i += 2
                        continue
                    }

                    next == 'l' -> {
                        style = style.copy(bold = true)
                        i += 2
                        continue
                    }

                    next == 'o' -> {
                        style = style.copy(italic = true)
                        i += 2
                        continue
                    }

                    next == 'n' -> {
                        style = style.copy(underlined = true)
                        i += 2
                        continue
                    }

                    next == 'm' -> {
                        style = style.copy(strikethrough = true)
                        i += 2
                        continue
                    }

                    next == 'k' -> {
                        style = style.copy(obfuscated = true)
                        i += 2
                        continue
                    }

                    next == 'r' -> {
                        style = StyleState()
                        i += 2
                        continue
                    }

                    next in formatCodes -> {
                        i += 2
                        continue
                    }
                }
            }

            result.add(
                LegacyStyledCharacter(
                    color = style.color,
                    bold = style.bold,
                    italic = style.italic,
                    underlined = style.underlined,
                    strikethrough = style.strikethrough,
                    obfuscated = style.obfuscated
                )
            )
            i++
        }

        return result
    }

    private fun compactHexLength(source: String, index: Int): Int {
        if (index + 7 >= source.length) return 0
        if (source[index + 1] != '#') return 0
        val hex = source.substring(index + 2, index + 8)
        return if (hex.all { it.isHexDigit() }) 8 else 0
    }

    private fun verboseHexLength(source: String, index: Int, prefix: Char): Int {
        if (index + 13 >= source.length) return 0
        if (source[index + 1].lowercaseChar() != 'x') return 0

        var cursor = index + 2
        repeat(6) {
            if (cursor + 1 >= source.length) return 0
            if (source[cursor] != prefix) return 0
            if (!source[cursor + 1].isHexDigit()) return 0
            cursor += 2
        }

        return cursor - index
    }

    private fun parseVerboseHexColor(source: String, index: Int): Color? {
        val digits = StringBuilder(6)
        var cursor = index + 2
        repeat(6) {
            digits.append(source[cursor + 1])
            cursor += 2
        }
        return parseHex(digits.toString())
    }

    private fun parseHex(hex: String): Color? {
        return runCatching {
            Color(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
        }.getOrNull()
    }

    private fun isLegacyCode(ch: Char): Boolean {
        val code = ch.lowercaseChar()
        return code in colorCodes || code in formatCodes || code == 'x'
    }

    private fun Char.isLegacyPrefix(): Boolean {
        return this == '&' || this == '§'
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
