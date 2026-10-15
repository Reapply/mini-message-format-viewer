package me.inspect.minimessageformatviewer.rendering

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class MiniMessageAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val mapped = MiniMessageText.expand(element) ?: return
        val context = RenderContext.of(element.project)
        if (!MiniMessageRenderer.mightFormat(mapped.text, context)) return

        val rendered = MiniMessageRenderer.render(mapped.text, context)

        for (span in rendered.spans) {
            if (span.style.kind != SpanKind.TEXT && span.style.kind != SpanKind.PLACEHOLDER) continue
            mapped.fileRanges(span.start, span.end).forEach {
                TextHighlighter.highlightText(holder, it, span.style.color, span.style.decorations)
            }
        }

        val coloredInsertions = mutableSetOf<MappedText.Insertion>()
        for (tag in rendered.tags) {
            val color = tag.color ?: continue
            mapped.fileRanges(tag.start, tag.end).forEach { TextHighlighter.highlightTag(holder, it, color) }

            // An inserted `${ACCENT}` takes the color of the first tag it holds.
            mapped.insertionsIn(tag.start, tag.end)
                .filter { coloredInsertions.add(it) }
                .forEach { TextHighlighter.highlightTag(holder, it.source, color) }
        }

        // Problems in inserted text are shown where that text is written instead.
        for (problem in rendered.problems) {
            val ranges = mapped.fileRanges(problem.start, problem.end)
            if (ranges.isEmpty()) continue

            val severity = if (problem.weak) HighlightSeverity.WEAK_WARNING else HighlightSeverity.WARNING
            holder.newAnnotation(severity, problem.message)
                .range(TextRange(ranges.first().startOffset, ranges.last().endOffset))
                .create()
        }
    }
}
