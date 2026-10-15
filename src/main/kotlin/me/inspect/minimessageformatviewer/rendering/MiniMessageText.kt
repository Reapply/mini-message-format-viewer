package me.inspect.minimessageformatviewer.rendering

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/** Finds the MiniMessage text of strings in any language. */
object MiniMessageText {

    /**
     * The text of [element] if it is a string, filled in by a [TemplateExpander] where one knows the language.
     * Only the innermost of several elements covering the same string gets it, so a string is read once.
     */
    fun expand(element: PsiElement): MappedText? {
        TemplateExpander.EP_NAME.extensionList
            .firstNotNullOfOrNull { it.expand(element) }
            ?.let { return it }

        val text = element.text ?: return null
        if (!looksLikeString(text)) return null
        if (element.firstChild?.textRange == element.textRange) return null

        val stringStart = findStringStart(text)
        val stringEnd = findStringEnd(text)
        if (stringStart == -1 || stringEnd == -1 || stringStart >= stringEnd) return null

        return MappedText.of(
            text.substring(stringStart, stringEnd),
            element.textRange.startOffset + stringStart
        )
    }

    /** Whether [element] sits in a string, for completion and typing. */
    fun isInString(element: PsiElement): Boolean {
        var current: PsiElement? = element
        repeat(4) {
            val candidate = current ?: return false
            if (candidate is PsiFile) return false
            if (TemplateExpander.EP_NAME.extensionList.any { it.expand(candidate) != null }) return true
            if (looksLikeString(candidate.text ?: "")) return true
            current = candidate.parent
        }
        return false
    }

    private fun looksLikeString(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length >= 2 &&
                ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                        (trimmed.startsWith("'") && trimmed.endsWith("'")))
    }

    private fun findStringStart(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        return if (firstQuote != -1) firstQuote + 1 else -1
    }

    private fun findStringEnd(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        if (firstQuote == -1) return -1

        val quoteChar = text[firstQuote]
        val lastQuote = text.lastIndexOf(quoteChar)

        return if (lastQuote > firstQuote) lastQuote else -1
    }
}
