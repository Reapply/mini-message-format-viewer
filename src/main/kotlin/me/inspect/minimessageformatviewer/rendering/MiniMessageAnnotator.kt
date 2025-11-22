package me.inspect.minimessageformatviewer.rendering

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.inspect.minimessageformatviewer.parser.TagParser
import me.inspect.minimessageformatviewer.parser.TagType
import java.util.LinkedList

class MiniMessageAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text ?: return
        if (!text.contains('<')) return
        if (!isStringLiteral(text)) return

        val fileOffset = element.textRange.startOffset
        val stringStart = findStringStart(text)
        val stringEnd = findStringEnd(text)

        if (stringStart == -1 || stringEnd == -1 || stringStart >= stringEnd) return

        val gradientRegions = GradientRegionFinder.find(text, stringStart, stringEnd)
        val colorStack = LinkedList<ColorState>()

        var currentState = ColorState(null, null, 0f, false)
        var segmentStart = stringStart
        var i = stringStart

        while (i < stringEnd) {
            if (text[i] != '<') {
                i++
                continue
            }

            val tag = TagParser.parse(text, i)
            if (tag == null || i + tag.length > stringEnd) {
                i++
                continue
            }

            if (segmentStart < i) {
                TextHighlighter.highlightSegment(
                    holder, fileOffset, segmentStart, i, currentState, gradientRegions
                )
            }

            val tagColor = tag.color ?: tag.gradientColors?.firstOrNull()
            tagColor?.let {
                TextHighlighter.highlightTag(
                    holder,
                    TextRange(fileOffset + i, fileOffset + i + tag.length),
                    it
                )
            }

            currentState = when (tag.type) {
                TagType.RESET -> {
                    colorStack.clear()
                    ColorState(null, null, 0f, false)
                }

                TagType.CLOSE -> {
                    if (colorStack.isNotEmpty()) colorStack.removeLast()
                    else ColorState(null, null, 0f, false)
                }

                TagType.OPEN -> {
                    colorStack.addLast(currentState)
                    when {
                        tag.isGradient -> ColorState(null, tag.gradientColors, tag.gradientPhase, tag.reversed)
                        tag.color != null -> ColorState(tag.color, null, 0f, false)
                        else -> currentState
                    }
                }

                TagType.SELF_CLOSING -> currentState
            }

            i += tag.length
            segmentStart = i
        }

        if (segmentStart < stringEnd) {
            TextHighlighter.highlightSegment(
                holder, fileOffset, segmentStart, stringEnd, currentState, gradientRegions
            )
        }
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

    private fun findStringEnd(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        if (firstQuote == -1) return -1

        val quoteChar = text[firstQuote]
        val lastQuote = text.lastIndexOf(quoteChar)

        return if (lastQuote > firstQuote) lastQuote else -1
    }
}
