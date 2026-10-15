package me.inspect.minimessageformatviewer.preview

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import me.inspect.minimessageformatviewer.parser.Decoration
import me.inspect.minimessageformatviewer.rendering.Rendered
import me.inspect.minimessageformatviewer.rendering.SpanKind
import java.awt.Color

/** Draws MiniMessage text as HTML the way it shows in Minecraft chat, for the hover preview. */
object MiniMessagePreview {

    private val CHAT_BACKGROUND = Color(0x1D, 0x1D, 0x1D)
    private val CHAT_TEXT = Color.WHITE
    private val PLACEHOLDER = Regex("<([a-z0-9_-]+)[^>]*>")

    fun html(rendered: Rendered): String = buildString {
        append("<table cellspacing='0' cellpadding='8'><tr><td bgcolor='#${ColorUtil.toHex(CHAT_BACKGROUND)}'>")
        append("<code>")

        for (span in rendered.spans) {
            if (span.hidden) continue
            val raw = rendered.text.substring(span.start, span.end)

            val content = when (span.style.kind) {
                SpanKind.NEWLINE -> {
                    append("<br>")
                    continue
                }

                // A head is shown as a small face, other inserted things by their tag name.
                SpanKind.INSERT -> if (raw.startsWith("<head")) "☺" else "[${raw.trim('<', '>').substringBefore(':')}]"

                SpanKind.PLACEHOLDER -> PLACEHOLDER.replace(raw) { "‹${it.groupValues[1]}›" }

                SpanKind.TEXT -> {
                    val text = raw.replace("\\n", "\n").replace("\\\"", "\"")
                    if (Decoration.OBFUSCATED in span.style.decorations) {
                        text.map { if (it.isWhitespace()) it else '▒' }.joinToString("")
                    } else {
                        text
                    }
                }
            }

            append(styled(content, span.style.color ?: CHAT_TEXT, span.style.decorations, span.style.kind == SpanKind.PLACEHOLDER))
        }

        append("</code></td></tr></table>")
    }

    private fun styled(text: String, color: Color, decorations: Set<Decoration>, placeholder: Boolean): String {
        val tags = buildList {
            if (Decoration.BOLD in decorations) add("b")
            if (Decoration.ITALIC in decorations || placeholder) add("i")
            if (Decoration.UNDERLINED in decorations) add("u")
            if (Decoration.STRIKETHROUGH in decorations) add("s")
        }

        val escaped = StringUtil.escapeXmlEntities(text).replace("\n", "<br>")
        return buildString {
            append("<font color='#${ColorUtil.toHex(color)}'>")
            tags.forEach { append("<$it>") }
            append(escaped)
            tags.reversed().forEach { append("</$it>") }
            append("</font>")
        }
    }
}
