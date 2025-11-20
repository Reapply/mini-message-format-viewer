package me.inspect.minimessageformatviewer.rendering

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import me.inspect.minimessageformatviewer.color.GradientCalculator
import java.awt.Color
import java.awt.Font

object TextHighlighter {

    fun highlightText(holder: AnnotationHolder, range: TextRange, color: Color) {
        val attributes = TextAttributes(color, null, null, null, Font.PLAIN)
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range)
            .enforcedTextAttributes(attributes)
            .create()
    }

    fun highlightTag(holder: AnnotationHolder, range: TextRange, color: Color) {
        val attributes = TextAttributes(color, null, null, null, Font.BOLD)
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range)
            .enforcedTextAttributes(attributes)
            .create()
    }

    fun highlightSegment(
        holder: AnnotationHolder,
        fileOffset: Int,
        start: Int,
        end: Int,
        state: ColorState,
        gradientRegions: List<GradientRegion>
    ) {
        if (state.gradientColors != null && state.gradientColors.isNotEmpty()) {
            val gradientRegion = gradientRegions.find { region ->
                start < region.end && end > region.start
            }

            if (gradientRegion != null) {
                applyGradient(holder, fileOffset, start, end, state, gradientRegion)
                return
            }
        }

        state.color?.let {
            highlightText(holder, TextRange(fileOffset + start, fileOffset + end), it)
        }
    }

    private fun applyGradient(
        holder: AnnotationHolder,
        fileOffset: Int,
        start: Int,
        end: Int,
        state: ColorState,
        region: GradientRegion
    ) {
        val textLength = region.end - region.start

        for (charIndex in start until end) {
            val positionInGradient = charIndex - region.start
            val color = GradientCalculator.getColor(
                state.gradientColors!!,
                positionInGradient,
                textLength,
                state.gradientPhase
            )

            highlightText(holder, TextRange(fileOffset + charIndex, fileOffset + charIndex + 1), color)
        }
    }
}
