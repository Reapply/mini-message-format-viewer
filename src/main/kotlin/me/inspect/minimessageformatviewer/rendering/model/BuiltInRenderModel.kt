package me.inspect.minimessageformatviewer.rendering.model

import me.inspect.minimessageformatviewer.color.GradientCalculator
import me.inspect.minimessageformatviewer.parser.TagParser
import me.inspect.minimessageformatviewer.parser.TagType
import me.inspect.minimessageformatviewer.rendering.ColorState
import me.inspect.minimessageformatviewer.rendering.GradientRegion
import me.inspect.minimessageformatviewer.rendering.GradientRegionFinder
import java.util.LinkedList

object BuiltInRenderModel : MessageRenderModel {

    override fun render(source: String, customColors: Map<String, String>): RenderOutput {
        val gradientRegions = GradientRegionFinder.find(source, 0, source.length)
        val colorStack = LinkedList<ColorState>()
        val styledCharacters = mutableListOf<StyledCharacter>()
        val tagHighlights = mutableListOf<TagHighlight>()

        var currentState = ColorState(
            color = null,
            gradientColors = null,
            gradientPhase = 0f,
            reversed = false,
            bold = false,
            italic = false,
            underlined = false,
            strikethrough = false,
            obfuscated = false
        )
        var segmentStart = 0
        var i = 0

        while (i < source.length) {
            if (source[i] != '<') {
                i++
                continue
            }

            val tag = TagParser.parse(source, i)
            if (tag == null || i + tag.length > source.length) {
                i++
                continue
            }

            if (segmentStart < i) {
                appendSegment(styledCharacters, segmentStart, i, currentState, gradientRegions)
            }

            val tagColor = tag.color ?: tag.gradientColors?.firstOrNull()
            tagHighlights.add(TagHighlight(i, i + tag.length, tagColor))

            currentState = when (tag.type) {
                TagType.RESET -> {
                    colorStack.clear()
                    ColorState(
                        color = null,
                        gradientColors = null,
                        gradientPhase = 0f,
                        reversed = false,
                        bold = false,
                        italic = false,
                        underlined = false,
                        strikethrough = false,
                        obfuscated = false
                    )
                }

                TagType.CLOSE -> {
                    if (colorStack.isNotEmpty()) colorStack.removeLast()
                    else ColorState(
                        color = null,
                        gradientColors = null,
                        gradientPhase = 0f,
                        reversed = false,
                        bold = false,
                        italic = false,
                        underlined = false,
                        strikethrough = false,
                        obfuscated = false
                    )
                }

                TagType.OPEN -> {
                    colorStack.addLast(currentState)
                    when {
                        tag.isGradient -> ColorState(
                            null,
                            tag.gradientColors,
                            tag.gradientPhase,
                            tag.reversed,
                            currentState.bold,
                            currentState.italic,
                            currentState.underlined,
                            currentState.strikethrough,
                            currentState.obfuscated
                        )

                        tag.color != null -> ColorState(
                            tag.color,
                            null,
                            0f,
                            false,
                            currentState.bold,
                            currentState.italic,
                            currentState.underlined,
                            currentState.strikethrough,
                            currentState.obfuscated
                        )

                        tag.bold -> currentState.copy(bold = true)
                        tag.italic -> currentState.copy(italic = true)
                        tag.underlined -> currentState.copy(underlined = true)
                        tag.strikethrough -> currentState.copy(strikethrough = true)
                        tag.obfuscated -> currentState.copy(obfuscated = true)
                        else -> currentState
                    }
                }

                TagType.SELF_CLOSING -> currentState
            }

            i += tag.length
            segmentStart = i
        }

        if (segmentStart < source.length) {
            appendSegment(styledCharacters, segmentStart, source.length, currentState, gradientRegions)
        }

        return RenderOutput(
            styledCharacters = styledCharacters,
            tagHighlights = tagHighlights
        )
    }

    private fun appendSegment(
        out: MutableList<StyledCharacter>,
        start: Int,
        end: Int,
        state: ColorState,
        gradientRegions: List<GradientRegion>
    ) {
        if (state.gradientColors != null && state.gradientColors.isNotEmpty()) {
            val region = gradientRegions.find { start < it.end && end > it.start }
            if (region != null) {
                applyGradient(out, start, end, state, region)
                return
            }
        }

        for (index in start until end) {
            if (
                state.color == null &&
                !state.bold &&
                !state.italic &&
                !state.underlined &&
                !state.strikethrough &&
                !state.obfuscated
            ) {
                continue
            }
            out.add(
                StyledCharacter(
                    sourceIndex = index,
                    color = state.color,
                    bold = state.bold,
                    italic = state.italic,
                    underlined = state.underlined,
                    strikethrough = state.strikethrough,
                    obfuscated = state.obfuscated
                )
            )
        }
    }

    private fun applyGradient(
        out: MutableList<StyledCharacter>,
        start: Int,
        end: Int,
        state: ColorState,
        region: GradientRegion
    ) {
        val colors = state.gradientColors ?: return
        val length = (region.end - region.start).coerceAtLeast(1)

        for (index in start until end) {
            val position = index - region.start
            val color = GradientCalculator.getColor(
                colors = colors,
                position = position,
                length = length,
                phase = state.gradientPhase,
                reversed = state.reversed
            )

            out.add(
                StyledCharacter(
                    sourceIndex = index,
                    color = color,
                    bold = state.bold,
                    italic = state.italic,
                    underlined = state.underlined,
                    strikethrough = state.strikethrough,
                    obfuscated = state.obfuscated
                )
            )
        }
    }
}
