package me.inspect.minimessageformatviewer.rendering.model

data class SourceMapping(
    val plainText: String,
    val sourceIndices: List<Int>,
    val highlights: List<TagHighlight>
)

object AdventureTextMapping {
    fun map(source: String, customColors: Map<String, String>): SourceMapping {
        val plain = StringBuilder(source.length)
        val indices = mutableListOf<Int>()
        val highlights = mutableListOf<TagHighlight>()

        var i = 0
        while (i < source.length) {
            if (isTagStart(source, i)) {
                val end = source.indexOf('>', i + 1)
                if (end != -1) {
                    val content = source.substring(i + 1, end)
                    if (content.isNotBlank()) {
                        highlights.add(
                            TagHighlight(
                                start = i,
                                end = end + 1,
                                color = ColorParsing.parseTagColor(content, customColors)
                            )
                        )
                        i = end + 1
                        continue
                    }
                }
            }

            plain.append(source[i])
            indices.add(i)
            i++
        }

        return SourceMapping(plain.toString(), indices, highlights)
    }

    private fun isTagStart(source: String, index: Int): Boolean {
        if (source[index] != '<') return false
        if (index > 0 && source[index - 1] == '\\') return false
        return true
    }
}
