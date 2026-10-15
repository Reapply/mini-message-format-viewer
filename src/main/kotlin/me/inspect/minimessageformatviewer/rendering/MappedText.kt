package me.inspect.minimessageformatviewer.rendering

import com.intellij.openapi.util.TextRange

/**
 * The MiniMessage text of a string, with each character's file offset, or -1 for a character inserted from
 * elsewhere (such as the value of `${ACCENT}`), so colors worked out on the text land on the right spots in the editor.
 */
class MappedText private constructor(
    val text: String,
    private val offsets: IntArray,
    private val insertions: List<Insertion>
) {

    /** Text from [start] to [end] that came from [source] in the file, e.g. `${ACCENT}`. */
    data class Insertion(val start: Int, val end: Int, val source: TextRange)

    /** The file offset of the character at [index], or -1 if it was inserted. */
    fun fileOffset(index: Int): Int = offsets[index]

    /** The index in [text] of the character at [fileOffset] in the file, or -1 if it isn't part of this text. */
    fun indexOfFileOffset(fileOffset: Int): Int = offsets.indexOf(fileOffset)

    /** Whether the character at [fileOffset] in the file is part of this text. */
    fun containsFileOffset(fileOffset: Int): Boolean = offsets.contains(fileOffset)

    /** The file ranges of the characters in [start, end) that are written in the file itself. */
    fun fileRanges(start: Int, end: Int): List<TextRange> {
        val ranges = mutableListOf<TextRange>()
        var runStart = -1
        var runEnd = -1

        for (i in start until end) {
            val offset = offsets[i]
            if (offset != -1 && offset == runEnd) {
                runEnd++
                continue
            }

            if (runStart != -1) ranges.add(TextRange(runStart, runEnd))
            runStart = offset
            runEnd = if (offset == -1) -1 else offset + 1
        }

        if (runStart != -1) ranges.add(TextRange(runStart, runEnd))
        return ranges
    }

    /** The insertions that overlap [start, end). */
    fun insertionsIn(start: Int, end: Int): List<Insertion> =
        insertions.filter { it.start < end && start < it.end }

    class Builder {
        private val text = StringBuilder()
        private val offsets = mutableListOf<Int>()
        private val insertions = mutableListOf<Insertion>()

        /** Adds [value], written in the file starting at [fileOffset]. */
        fun append(value: String, fileOffset: Int) {
            text.append(value)
            value.indices.forEach { offsets.add(fileOffset + it) }
        }

        /** Adds [value], which the file writes as [source] instead. */
        fun appendInserted(value: String, source: TextRange) {
            append(value, IntArray(value.length) { -1 }, source)
        }

        /**
         * Adds [value] with each character's file offset (-1 where inserted), which the file writes as [source]:
         * for `${num("<amount>")}` the `<amount>` keeps its offsets while the color around it comes from `num`.
         */
        fun append(value: String, valueOffsets: IntArray, source: TextRange) {
            if (valueOffsets.any { it == -1 }) {
                insertions.add(Insertion(text.length, text.length + value.length, source))
            }
            text.append(value)
            valueOffsets.forEach { offsets.add(it) }
        }

        fun build(): MappedText = MappedText(text.toString(), offsets.toIntArray(), insertions)
    }

    companion object {
        /** A string that is shown as part of a bigger one, so has nothing of its own. */
        val EMPTY = MappedText("", IntArray(0), emptyList())

        /** [text] written as is in the file starting at [fileOffset]. */
        fun of(text: String, fileOffset: Int): MappedText =
            Builder().apply { append(text, fileOffset) }.build()
    }
}
