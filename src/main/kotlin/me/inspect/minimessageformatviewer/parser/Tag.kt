package me.inspect.minimessageformatviewer.parser

import java.awt.Color

data class Tag(
    val length: Int,
    val type: TagType,
    /** The tag's name with aliases resolved (`b` is `bold`), which a closing tag matches on. */
    val name: String = "",
    val color: Color? = null,
    val gradientColors: List<Color>? = null,
    val gradientPhase: Float = 0f,
    val reversed: Boolean = false,
    val decoration: Decoration? = null,
    val decorationOn: Boolean = true,
    val hasArguments: Boolean = false,
    /** MiniMessage text inside the tag, such as the text of `<hover:show_text:'...'>`, as indexes into the text. */
    val nested: IntRange? = null,
    val problem: String? = null
) {
    val isGradient: Boolean
        get() = !gradientColors.isNullOrEmpty()
}
