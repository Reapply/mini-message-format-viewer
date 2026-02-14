package me.inspect.minimessageformatviewer.rendering.model

import java.awt.Color

data class StyledCharacter(
    val sourceIndex: Int,
    val color: Color?,
    val bold: Boolean,
    val italic: Boolean
)

data class TagHighlight(
    val start: Int,
    val end: Int,
    val color: Color?
)

data class RenderOutput(
    val styledCharacters: List<StyledCharacter>,
    val tagHighlights: List<TagHighlight>
)
