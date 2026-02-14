package me.inspect.minimessageformatviewer.rendering

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.inspect.minimessageformatviewer.rendering.model.MessageRenderModelFactory
import me.inspect.minimessageformatviewer.rendering.model.RenderMode
import me.inspect.minimessageformatviewer.rendering.model.StyledCharacter
import me.inspect.minimessageformatviewer.rendering.model.TagHighlight
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.Color
import java.awt.Font

class MiniMessageAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text ?: return
        if (!isStringLiteral(text)) return

        val fileOffset = element.textRange.startOffset
        val stringStart = findStringStart(text)
        val stringEndExclusive = findStringEndExclusive(text)

        if (stringStart == -1 || stringEndExclusive == -1 || stringStart >= stringEndExclusive) return

        val settings = runCatching { MiniMessageSettings.getInstance() }.getOrNull() ?: return
        val renderMode = RenderMode.fromRaw(settings.renderMode)
        val sourceContent = text.substring(stringStart, stringEndExclusive)

        if (!shouldAnnotate(renderMode, sourceContent)) return

        val renderModel = MessageRenderModelFactory.create(renderMode)
        val renderOutput = renderModel.render(sourceContent, settings.customColors)
        val contentStartOffset = fileOffset + stringStart

        renderOutput.tagHighlights.forEach { highlight ->
            highlightTag(holder, contentStartOffset, highlight)
        }
        renderOutput.styledCharacters.forEach { styled ->
            highlightStyledCharacter(holder, contentStartOffset, styled)
        }
    }

    private fun shouldAnnotate(mode: RenderMode, source: String): Boolean {
        return when (mode) {
            RenderMode.ADVENTURE_API -> source.contains('<')
            RenderMode.LEGACY -> containsLegacyFormatting(source)
        }
    }

    private fun containsLegacyFormatting(source: String): Boolean {
        var i = 0
        while (i + 1 < source.length) {
            val prefix = source[i]
            if ((prefix == '&' || prefix == '§') && source[i + 1].lowercaseChar() in "0123456789abcdefklmnorx#") {
                return true
            }
            i++
        }
        return false
    }

    private fun highlightTag(holder: AnnotationHolder, contentStartOffset: Int, tag: TagHighlight) {
        if (tag.start >= tag.end) return
        val color = ReadableColorUtil.adjustForEditor(tag.color ?: DEFAULT_TAG_COLOR)

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(contentStartOffset + tag.start, contentStartOffset + tag.end))
            .enforcedTextAttributes(TextAttributes(color, null, null, null, Font.BOLD))
            .create()
    }

    private fun highlightStyledCharacter(
        holder: AnnotationHolder,
        contentStartOffset: Int,
        styledCharacter: StyledCharacter
    ) {
        val fontType = buildFontType(styledCharacter.bold, styledCharacter.italic)
        val color = styledCharacter.color?.let(ReadableColorUtil::adjustForEditor)
        if (color == null && fontType == Font.PLAIN) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(contentStartOffset + styledCharacter.sourceIndex, contentStartOffset + styledCharacter.sourceIndex + 1))
            .enforcedTextAttributes(TextAttributes(color, null, null, null, fontType))
            .create()
    }

    private fun buildFontType(bold: Boolean, italic: Boolean): Int {
        var type = Font.PLAIN
        if (bold) type = type or Font.BOLD
        if (italic) type = type or Font.ITALIC
        return type
    }

    private fun isStringLiteral(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))
    }

    private fun findStringStart(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        return if (firstQuote != -1) firstQuote + 1 else -1
    }

    private fun findStringEndExclusive(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        if (firstQuote == -1) return -1

        val quoteChar = text[firstQuote]
        val lastQuote = text.lastIndexOf(quoteChar)

        return if (lastQuote > firstQuote) lastQuote else -1
    }

    companion object {
        private val DEFAULT_TAG_COLOR = Color(0x6A, 0x6A, 0x6A)
    }
}
