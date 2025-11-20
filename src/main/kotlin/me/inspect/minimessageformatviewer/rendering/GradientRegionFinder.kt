package me.inspect.minimessageformatviewer.rendering

import me.inspect.minimessageformatviewer.parser.Tag
import me.inspect.minimessageformatviewer.parser.TagParser
import me.inspect.minimessageformatviewer.parser.TagType

object GradientRegionFinder {

    fun find(text: String, stringStart: Int, stringEnd: Int): List<GradientRegion> {
        val regions = mutableListOf<GradientRegion>()
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

            if (tag.isGradient && tag.type == TagType.OPEN) {
                val contentStart = i + tag.length
                val closingTagPos = findClosingTag(text, contentStart, stringEnd)
                val contentEnd = if (closingTagPos != -1) closingTagPos else stringEnd

                regions.add(GradientRegion(contentStart, contentEnd))
            }

            i += tag.length
        }

        return regions
    }

    private fun findClosingTag(text: String, start: Int, stringEnd: Int): Int {
        var depth = 1
        var i = start

        while (i < stringEnd && depth > 0) {
            if (text[i] != '<') {
                i++
                continue
            }

            val tag = TagParser.parse(text, i)
            if (tag == null) {
                i++
                continue
            }

            val tagText = text.substring(i, (i + tag.length).coerceAtMost(stringEnd))
            if (tagText.contains("</gradient", ignoreCase = true)) {
                depth--
                if (depth == 0) return i
            } else if (tag.isGradient && tag.type == TagType.OPEN) {
                depth++
            }

            if (!tag.isGradient && tag.color != null && tag.type == TagType.OPEN) {
                return i
            }

            i += tag.length
        }

        return -1
    }
}
