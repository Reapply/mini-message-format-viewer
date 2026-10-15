package me.inspect.minimessageformatviewer.rendering

import com.intellij.ui.ColorUtil
import me.inspect.minimessageformatviewer.color.ColorResolver
import me.inspect.minimessageformatviewer.color.GradientCalculator
import me.inspect.minimessageformatviewer.parser.Decoration
import me.inspect.minimessageformatviewer.parser.Tag
import me.inspect.minimessageformatviewer.parser.TagParser
import me.inspect.minimessageformatviewer.parser.TagType
import java.awt.Color

enum class SpanKind {
    TEXT,

    /** A `<player>` style placeholder, standing in for text filled in later. */
    PLACEHOLDER,

    /** A `<newline>` or `<br>`. */
    NEWLINE,

    /** A tag that puts something into the text, such as `<head:...>` or `<key:...>`. */
    INSERT
}

data class CharStyle(val color: Color?, val decorations: Set<Decoration>, val kind: SpanKind)

/** Text from [start] to [end] drawn in [style]; [hidden] text is not in chat itself, such as hover text. */
data class Span(val start: Int, val end: Int, val style: CharStyle, val hidden: Boolean)

/** A tag from [start] to [end], drawn in [color] if it sets one. */
data class TagMark(val start: Int, val end: Int, val color: Color?)

/** Something likely wrong from [start] to [end]; [weak] ones may be on purpose. */
data class Problem(val start: Int, val end: Int, val message: String, val weak: Boolean)

class Rendered(
    val text: String,
    val spans: List<Span>,
    val tags: List<TagMark>,
    val problems: List<Problem>,
    /** Names of the `<placeholder>` tags in the text. */
    val placeholders: Set<String>,
    /** Names of known tags used without arguments, which a placeholder of the same name would replace. */
    val bareTags: Set<String>
)

/** Works out how MiniMessage text looks: the style of each character, where the tags are and what is wrong. */
object MiniMessageRenderer {

    private const val LEGACY_CODES = "0123456789abcdefklmnor"
    private val LEGACY_HEX = Regex("#[0-9a-fA-F]{6}")

    fun mightFormat(text: String, context: RenderContext): Boolean =
        '<' in text || '§' in text || (context.legacyAmpersand && '&' in text)

    fun render(text: String, context: RenderContext): Rendered {
        val run = Run(text, context)
        run.render(0, text.length, hidden = false)
        return run.finish()
    }

    private class Gradient(val colors: List<Color>, val phase: Float, val reversed: Boolean) {
        val chars = mutableListOf<Int>()
    }

    private data class Style(val color: Color?, val gradient: Gradient?, val decorations: Set<Decoration>) {
        companion object {
            val PLAIN = Style(null, null, emptySet())
        }
    }

    private class Run(private val text: String, private val context: RenderContext) {
        private val styles = arrayOfNulls<CharStyle>(text.length)
        private val hidden = BooleanArray(text.length)
        private val tags = mutableListOf<TagMark>()
        private val problems = mutableListOf<Problem>()
        private val placeholders = linkedSetOf<String>()
        private val bareTags = linkedSetOf<String>()
        private val gradients = mutableListOf<Gradient>()

        fun render(from: Int, to: Int, hidden: Boolean) {
            val stack = mutableListOf<Pair<String, Style>>()
            var style = Style.PLAIN
            var i = from

            while (i < to) {
                if (text[i] == '<') {
                    val tag = TagParser.parse(text, i, context.palette)
                    if (tag != null && i + tag.length <= to) {
                        style = apply(tag, i, i + tag.length, style, stack, hidden)
                        i += tag.length
                        continue
                    }
                }

                val legacy = legacyLength(i, to)
                if (legacy > 0) {
                    style = applyLegacy(i, legacy, style, stack)
                    i += legacy
                    continue
                }

                mark(i, i + 1, style, SpanKind.TEXT, hidden)
                i++
            }
        }

        private fun apply(
            tag: Tag,
            start: Int,
            end: Int,
            style: Style,
            stack: MutableList<Pair<String, Style>>,
            hidden: Boolean
        ): Style {
            when (tag.type) {
                TagType.INVALID -> {
                    problems.add(Problem(start, end, tag.problem ?: "Invalid tag", weak = false))
                    mark(start, end, style, SpanKind.TEXT, hidden)
                    return style
                }

                TagType.PLACEHOLDER -> {
                    tag.problem?.let { problems.add(Problem(start, end, it, weak = true)) }
                    placeholders.add(tag.name)
                    mark(start, end, style, SpanKind.PLACEHOLDER, hidden)
                    return style
                }

                else -> Unit
            }

            tags.add(TagMark(start, end, tag.color ?: tag.gradientColors?.firstOrNull()))
            if (tag.type != TagType.CLOSE && !tag.hasArguments) bareTags.add(tag.name)
            tag.nested?.let { render(it.first, it.last + 1, hidden = true) }

            return when (tag.type) {
                TagType.RESET -> {
                    stack.clear()
                    Style.PLAIN
                }

                TagType.CLOSE -> {
                    val index = stack.indexOfLast { it.first == tag.name }
                    if (index == -1) {
                        problems.add(Problem(start, end, "</${tag.name}> closes a tag that is not open", weak = true))
                        style
                    } else {
                        val before = stack[index].second
                        while (stack.size > index) stack.removeAt(stack.lastIndex)
                        before
                    }
                }

                TagType.OPEN -> {
                    stack.add(tag.name to style)
                    styled(tag, style)
                }

                else -> {
                    when (tag.name) {
                        "newline" -> mark(start, end, style, SpanKind.NEWLINE, hidden)
                        in TagParser.INSERTING -> mark(start, end, style, SpanKind.INSERT, hidden)
                    }
                    style
                }
            }
        }

        private fun styled(tag: Tag, style: Style): Style = when {
            tag.isGradient -> {
                val gradient = Gradient(tag.gradientColors!!, tag.gradientPhase, tag.reversed)
                gradients.add(gradient)
                Style(null, gradient, style.decorations)
            }

            tag.color != null -> Style(tag.color, null, style.decorations)

            tag.decoration != null -> style.copy(
                decorations = if (tag.decorationOn) style.decorations + tag.decoration
                else style.decorations - tag.decoration
            )

            else -> style
        }

        // `&a`, `§a` and `&#RRGGBB`; `&` can be turned off in settings for code where it is plain text.
        private fun legacyLength(i: Int, to: Int): Int {
            val c = text[i]
            if (c != '§' && !(c == '&' && context.legacyAmpersand)) return 0

            if (i + 8 <= to && LEGACY_HEX.matches(text.substring(i + 1, i + 8))) return 8
            if (i + 1 < to && text[i + 1].lowercaseChar() in LEGACY_CODES) return 2
            return 0
        }

        // A legacy color clears the styles before it, as in Minecraft itself.
        private fun applyLegacy(start: Int, length: Int, style: Style, stack: MutableList<Pair<String, Style>>): Style {
            val end = start + length

            if (length == 8) {
                val color = ColorUtil.fromHex(text.substring(start + 2, end))
                tags.add(TagMark(start, end, color))
                stack.clear()
                return Style(color, null, emptySet())
            }

            val code = text[start + 1].lowercaseChar()
            ColorResolver.LEGACY_COLORS[code]?.let { name ->
                val color = ColorResolver.resolve(name)
                tags.add(TagMark(start, end, color))
                stack.clear()
                return Style(color, null, emptySet())
            }

            tags.add(TagMark(start, end, null))
            val decoration = when (code) {
                'k' -> Decoration.OBFUSCATED
                'l' -> Decoration.BOLD
                'm' -> Decoration.STRIKETHROUGH
                'n' -> Decoration.UNDERLINED
                'o' -> Decoration.ITALIC
                else -> {
                    stack.clear()
                    return Style.PLAIN
                }
            }
            return style.copy(decorations = style.decorations + decoration)
        }

        private fun mark(start: Int, end: Int, style: Style, kind: SpanKind, hidden: Boolean) {
            for (i in start until end) {
                styles[i] = CharStyle(style.color, style.decorations, kind)
                this.hidden[i] = hidden
                style.gradient?.chars?.add(i)
            }
        }

        fun finish(): Rendered {
            for (gradient in gradients) {
                gradient.chars.forEachIndexed { position, i ->
                    val color = GradientCalculator.getColor(
                        gradient.colors, position, gradient.chars.size, gradient.phase, gradient.reversed
                    )
                    styles[i] = styles[i]?.copy(color = color)
                }
            }

            val spans = mutableListOf<Span>()
            var i = 0
            while (i < text.length) {
                val style = styles[i]
                if (style == null) {
                    i++
                    continue
                }

                var end = i + 1
                while (end < text.length && styles[end] == style && hidden[end] == hidden[i]) end++

                spans.add(Span(i, end, style, hidden[i]))
                i = end
            }

            return Rendered(text, spans, tags, problems, placeholders, bareTags)
        }
    }
}
