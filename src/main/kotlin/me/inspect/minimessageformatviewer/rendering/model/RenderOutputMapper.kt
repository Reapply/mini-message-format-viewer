package me.inspect.minimessageformatviewer.rendering.model

object RenderOutputMapper {
    fun mapStyledCharacters(
        sourceIndices: List<Int>,
        styles: List<LegacyStyledCharacter>
    ): List<StyledCharacter> {
        val count = minOf(sourceIndices.size, styles.size)
        if (count == 0) return emptyList()

        val mapped = ArrayList<StyledCharacter>(count)
        for (index in 0 until count) {
            val style = styles[index]
            if (style.color == null && !style.bold && !style.italic) {
                continue
            }
            mapped.add(
                StyledCharacter(
                    sourceIndex = sourceIndices[index],
                    color = style.color,
                    bold = style.bold,
                    italic = style.italic
                )
            )
        }
        return mapped
    }
}
