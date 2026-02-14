package me.inspect.minimessageformatviewer.parser

import java.awt.Color

data class Tag(
    val length: Int,
    val type: TagType,
    val color: Color? = null,
    val gradientColors: List<Color>? = null,
    val gradientPhase: Float = 0f,
    val reversed: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false
) {
    val isGradient: Boolean
        get() = !gradientColors.isNullOrEmpty()
}
