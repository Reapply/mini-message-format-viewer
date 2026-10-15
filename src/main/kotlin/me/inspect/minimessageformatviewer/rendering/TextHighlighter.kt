package me.inspect.minimessageformatviewer.rendering

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import me.inspect.minimessageformatviewer.parser.Decoration
import java.awt.Color
import java.awt.Font

object TextHighlighter {

    fun highlightText(holder: AnnotationHolder, range: TextRange, color: Color?, decorations: Set<Decoration>) {
        if (color == null && decorations.isEmpty()) return
        annotate(holder, range, attributes(color, decorations, bold = false))
    }

    fun highlightTag(holder: AnnotationHolder, range: TextRange, color: Color) {
        annotate(holder, range, attributes(color, emptySet(), bold = true))
    }

    private fun annotate(holder: AnnotationHolder, range: TextRange, attributes: TextAttributes) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range)
            .enforcedTextAttributes(attributes)
            .create()
    }

    // Obfuscated text gets a wavy underline, standing in for its scrambling.
    private fun attributes(color: Color?, decorations: Set<Decoration>, bold: Boolean): TextAttributes {
        var font = Font.PLAIN
        if (bold || Decoration.BOLD in decorations) font = font or Font.BOLD
        if (Decoration.ITALIC in decorations) font = font or Font.ITALIC

        val attributes = TextAttributes(color, null, null, null, font)
        val effectColor = color ?: JBColor.foreground()
        val effects = buildList {
            if (Decoration.UNDERLINED in decorations) add(EffectType.LINE_UNDERSCORE)
            if (Decoration.STRIKETHROUGH in decorations) add(EffectType.STRIKEOUT)
            if (Decoration.OBFUSCATED in decorations) add(EffectType.WAVE_UNDERSCORE)
        }

        effects.firstOrNull()?.let {
            attributes.effectType = it
            attributes.effectColor = effectColor
        }
        if (effects.size > 1) {
            attributes.setAdditionalEffects(effects.drop(1).associateWith { effectColor })
        }

        return attributes
    }
}
