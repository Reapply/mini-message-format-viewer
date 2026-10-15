package me.inspect.minimessageformatviewer.kotlin

/** A string worked out from code, with each character's file offset, or -1 where it comes from elsewhere. */
class Evaluated(val text: String, val offsets: IntArray) {

    operator fun plus(other: Evaluated) = Evaluated(text + other.text, offsets + other.offsets)

    fun slice(start: Int, end: Int) = Evaluated(text.substring(start, end), offsets.copyOfRange(start, end))

    /** [transform] applied to the text, or null if it changes the length so offsets no longer line up. */
    fun mapText(transform: (String) -> String): Evaluated? =
        transform(text).takeIf { it.length == text.length }?.let { Evaluated(it, offsets) }

    companion object {
        val EMPTY = Evaluated("", IntArray(0))

        fun inserted(text: String) = Evaluated(text, IntArray(text.length) { -1 })

        fun written(text: String, fileOffset: Int) = Evaluated(text, IntArray(text.length) { fileOffset + it })
    }
}
