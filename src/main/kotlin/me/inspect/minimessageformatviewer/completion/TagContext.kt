package me.inspect.minimessageformatviewer.completion

/**
 * The tag being typed at the caret: `<click:run_` is the `click` tag at argument 1 with prefix `run_`, and
 * `</bo` is a closing tag with prefix `bo`.
 */
data class TagContext(
    /** Offset of the tag's `<`. */
    val open: Int,
    val isClosing: Boolean,
    /** The tag name as typed so far, lowercase. */
    val name: String,
    /** 0 while typing the name, 1 for the first argument, and so on. */
    val argumentIndex: Int,
    /** The text being typed at the caret, which completion replaces. */
    val prefix: String
) {
    companion object {
        private const val MAX_LENGTH = 200

        /** The tag ending at [offset] in [text], or null if the caret isn't in an unfinished tag. */
        fun at(text: CharSequence, offset: Int): TagContext? {
            var start = offset
            while (start > 0 && offset - start < MAX_LENGTH && isTagChar(text[start - 1])) start--

            val open = start - 1
            if (open < 0 || text[open] != '<') return null

            var content = text.subSequence(start, offset).toString()
            val isClosing = content.startsWith("/")
            if (isClosing) content = content.substring(1)

            val parts = content.split(':')
            if (isClosing && parts.size > 1) return null

            return TagContext(open, isClosing, parts[0].lowercase(), parts.size - 1, parts.last())
        }

        // Quotes, spaces and '>' end the search, so the caret inside `show_text:'<re` finds the inner `<`.
        private fun isTagChar(c: Char) = c.isLetterOrDigit() || c in "_-#:!./@[]=~^,+"
    }
}
