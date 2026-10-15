package me.inspect.minimessageformatviewer.parser

import com.intellij.ui.ColorUtil
import me.inspect.minimessageformatviewer.color.ColorResolver
import java.awt.Color

object TagParser {

    private val NAME = Regex("[!?#]?[a-z0-9_-]+")
    private val HEX = Regex("#[0-9a-f]{6}")

    private val ALIASES = mapOf(
        "colour" to "color",
        "c" to "color",
        "br" to "newline",
        "insert" to "insertion",
        "sel" to "selector",
        "tr" to "lang",
        "translate" to "lang",
        "tr_or" to "lang_or",
        "translate_or" to "lang_or",
        "data" to "nbt"
    )

    /** Tags that put something into the text instead of styling what follows. */
    val INSERTING = setOf("newline", "head", "selector", "score", "nbt", "key", "lang", "lang_or", "sprite")

    /** Tags that change something other than the look of what follows. */
    private val NON_STYLING = setOf("click", "hover", "insertion", "font", "shadow", "pride", "transition")

    /**
     * Tags a mistyped name is checked against: rare ones like `pride` are left out, since a placeholder such as
     * `<price>` is far more likely than a typo of them.
     */
    private val SUGGESTED =
        Decoration.entries.map { it.tagName }.toSet() +
                setOf("reset", "color", "gradient", "rainbow", "palette", "newline", "click", "hover", "head", "font")

    /** Every tag name MiniMessage knows, for completion. */
    val TAG_NAMES: Set<String> =
        Decoration.entries.map { it.tagName }.toSet() +
                setOf("reset", "color", "gradient", "rainbow", "palette") + INSERTING + NON_STYLING

    fun parse(text: String, start: Int, palette: Map<String, Color> = emptyMap()): Tag? {
        if (text[start] != '<') return null

        val end = findEnd(text, start)
        if (end == -1) return null

        val length = end - start + 1
        var contentEnd = end
        val isSelfClosing = text[end - 1] == '/' && end - 1 > start + 1
        if (isSelfClosing) contentEnd--

        val content = text.substring(start + 1, contentEnd)
        if (content.startsWith("/")) return parseClosingTag(content.substring(1), length)

        val args = splitArgs(content)
        val name = content.substring(args[0]).lowercase()
        if (!NAME.matches(name)) return null

        val values = args.drop(1).map { unquote(content.substring(it)) }
        val type = if (isSelfClosing) TagType.SELF_CLOSING else TagType.OPEN
        val tag = parseOpeningTag(name, values, length, type, palette)

        if (tag.name == "hover" && values.firstOrNull()?.lowercase() == "show_text" && args.size > 2) {
            val range = args[2]
            val quoted = content[range.first] == '\'' || content[range.first] == '"'
            val first = start + 1 + range.first + if (quoted) 1 else 0
            val last = start + 1 + range.last - if (quoted) 1 else 0
            return tag.copy(nested = first..last)
        }

        return tag
    }

    private fun parseOpeningTag(
        name: String,
        values: List<String>,
        length: Int,
        type: TagType,
        palette: Map<String, Color>
    ): Tag {
        val normalized = ALIASES[name] ?: Decoration.byName(name)?.tagName ?: name
        val base = Tag(length, type, normalized, hasArguments = values.isNotEmpty())

        if (name.startsWith("#")) {
            return if (HEX.matches(name)) {
                base.copy(color = ColorUtil.fromHex(name.substring(1)))
            } else {
                base.copy(type = TagType.INVALID, problem = "'$name' is not a color: hex colors are written #RRGGBB")
            }
        }

        // `<!shadow>` turns the text shadow off.
        if (name == "!shadow") return base.copy(name = "shadow")

        if (name.startsWith("!")) {
            val decoration = Decoration.byName(name.substring(1))
                ?: return base.copy(type = TagType.INVALID, problem = "'${name.substring(1)}' is not a decoration")
            return base.copy(name = decoration.tagName, decoration = decoration, decorationOn = false)
        }

        Decoration.byName(name)?.let { decoration ->
            return base.copy(decoration = decoration, decorationOn = values.firstOrNull()?.lowercase() != "false")
        }

        return when (normalized) {
            "reset" -> base.copy(type = TagType.RESET)

            "color" -> {
                val value = values.firstOrNull()
                    ?: return base.copy(type = TagType.INVALID, problem = "color needs a color, e.g. <color:red>")
                val color = ColorResolver.resolve(value)
                    ?: return base.copy(type = TagType.INVALID, problem = "Unknown color '$value'")
                base.copy(color = color)
            }

            "palette" -> {
                if (palette.isEmpty()) return base
                val value = values.firstOrNull()
                    ?: return base.copy(type = TagType.INVALID, problem = "palette needs a color name, e.g. <palette:accent>")
                val color = palette[value.lowercase()]
                    ?: return base.copy(
                        type = TagType.INVALID,
                        problem = "Unknown palette color '$value', expected one of: ${palette.keys.joinToString()}"
                    )
                base.copy(color = color)
            }

            "gradient" -> {
                val (colors, phase) = parseGradientArgs(values)
                base.copy(gradientColors = colors, gradientPhase = phase)
            }

            "rainbow" -> {
                val (reversed, phase) = parseRainbowArgs(values.firstOrNull() ?: "")
                base.copy(gradientColors = RAINBOW, gradientPhase = phase, reversed = reversed)
            }

            in INSERTING -> base.copy(type = TagType.SELF_CLOSING)

            in NON_STYLING -> base

            else -> ColorResolver.resolve(name)?.let { base.copy(color = it) }
                ?: base.copy(type = TagType.PLACEHOLDER, problem = suggestion(name))
        }
    }

    private fun parseClosingTag(content: String, length: Int): Tag? {
        val name = content.substringBefore(':').lowercase()
        if (!NAME.matches(name)) return null

        val normalized = ALIASES[name] ?: Decoration.byName(name)?.tagName ?: name
        val color = when {
            HEX.matches(name) -> ColorUtil.fromHex(name.substring(1))
            normalized == "gradient" || normalized == "rainbow" -> null
            else -> ColorResolver.resolve(name)
        }

        return Tag(length, TagType.CLOSE, normalized, color)
    }

    /** "Did you mean" for a name a letter or two off a tag MiniMessage knows, or null if it looks like a placeholder. */
    private fun suggestion(name: String): String? {
        if (name.length < 4) return null

        val allowed = if (name.length >= 7) 2 else 1
        val match = (SUGGESTED + ColorResolver.names())
            .filter { it != name }
            .minByOrNull { distance(name, it) }
            ?.takeIf { distance(name, it) <= allowed }
            ?: return null

        return "Unknown tag <$name>, did you mean <$match>? (Ignore this if <$name> is a placeholder.)"
    }

    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    // A '<' before the '>' means the first one was not a tag, as in "a < b".
    private fun findEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                (c == '\'' || c == '"') && text[i - 1] == ':' -> {
                    i = closingQuote(text, i)
                    if (i == -1) return -1
                }

                c == '>' -> return i
                c == '<' -> return -1
            }
            i++
        }
        return -1
    }

    private fun closingQuote(text: String, open: Int): Int {
        var i = open + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                text[open] -> return i
            }
            i++
        }
        return -1
    }

    // The ranges of the ':'-separated parts of [content], keeping quoted arguments whole.
    private fun splitArgs(content: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var partStart = 0
        var i = 0

        while (i < content.length) {
            val c = content[i]
            if ((c == '\'' || c == '"') && i == partStart && i > 0) {
                val close = closingQuote(content, i)
                if (close != -1) i = close
            } else if (c == ':') {
                ranges.add(partStart until i)
                partStart = i + 1
            }
            i++
        }

        ranges.add(partStart until content.length)
        return ranges
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && (value.first() == '\'' || value.first() == '"') && value.last() == value.first()) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    private fun parseRainbowArgs(argsString: String): Pair<Boolean, Float> {
        var reversed = false
        var argsToParse = argsString.trim()

        if (argsToParse.startsWith("!")) {
            reversed = true
            argsToParse = argsToParse.substring(1)
        }

        return reversed to (argsToParse.toFloatOrNull() ?: 0f)
    }

    private fun parseGradientArgs(args: List<String>): Pair<List<Color>, Float> {
        val colors = mutableListOf<Color>()
        var phase = 0f

        for (arg in args) {
            val trimmed = arg.trim()

            val number = trimmed.toFloatOrNull()
            if (number != null) {
                phase = number.coerceIn(-1f, 1f)
                continue
            }

            ColorResolver.resolve(trimmed)?.let { colors.add(it) }
        }

        return (colors.ifEmpty { listOf(Color.WHITE, Color.BLACK) }) to phase
    }

    private val RAINBOW = listOf(
        Color(255, 0, 0),
        Color(255, 165, 0),
        Color(255, 255, 0),
        Color(0, 255, 0),
        Color(0, 0, 255),
        Color(75, 0, 130),
        Color(238, 130, 238)
    )
}
