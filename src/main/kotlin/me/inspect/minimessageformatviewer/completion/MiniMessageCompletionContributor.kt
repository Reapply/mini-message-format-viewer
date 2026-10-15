package me.inspect.minimessageformatviewer.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.util.ui.ColorIcon
import me.inspect.minimessageformatviewer.parser.TagParser
import me.inspect.minimessageformatviewer.parser.TagType
import me.inspect.minimessageformatviewer.rendering.MiniMessageText
import me.inspect.minimessageformatviewer.rendering.RenderContext
import java.awt.Color

/**
 * Completes MiniMessage in strings: tag names after `<`, arguments after `:` (click actions, colors, keybinds and
 * so on), the tags still open after `</`, and placeholders already used in the file.
 */
class MiniMessageCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val document = parameters.editor.document
        val tag = TagContext.at(document.charsSequence, parameters.offset) ?: return
        if (!MiniMessageText.isInString(parameters.position)) return

        val context = RenderContext.of(parameters.position.project)
        val before = stringBefore(parameters.originalPosition, tag.open)

        val suggestions = when {
            tag.isClosing -> closingTags(before, context.palette)
            tag.argumentIndex == 0 -> placeholders(before, document.text, context.palette) + TagCatalog.tags(context.palette)
            else -> TagCatalog.arguments(tag.name, tag.argumentIndex, context.palette)
        }

        // The matcher skips a leading '!', so `<bol` would offer `!bold` too unless the '!' was typed.
        val negating = tag.prefix.startsWith("!")
        val results = result.withPrefixMatcher(tag.prefix)
        suggestions
            .filter { tag.argumentIndex > 0 || it.value.startsWith("!") == negating }
            .distinctBy { it.value }
            .forEach { results.addElement(lookup(it)) }
    }

    // The tags open before the caret, innermost first, since that is the one to close.
    private fun closingTags(before: String, palette: Map<String, Color>): List<Suggestion> {
        val open = mutableListOf<String>()
        var i = 0
        while (i < before.length) {
            val tag = if (before[i] == '<') TagParser.parse(before, i, palette) else null
            if (tag == null) {
                i++
                continue
            }

            when (tag.type) {
                TagType.OPEN -> open.add(tag.name)
                TagType.CLOSE -> {
                    val index = open.lastIndexOf(tag.name)
                    if (index != -1) while (open.size > index) open.removeAt(open.lastIndex)
                }
                TagType.RESET -> open.clear()
                else -> Unit
            }
            i += tag.length
        }

        return open.reversed().distinct().mapIndexed { index, name ->
            Suggestion(name, "open tag", After.CLOSE, priority = 100.0 - index)
        }
    }

    // Placeholders from this string rank above those from elsewhere in the file.
    private fun placeholders(before: String, file: String, palette: Map<String, Color>): List<Suggestion> {
        val inString = placeholderNames(before, palette)
        val inFile = placeholderNames(file, palette) - inString
        return inString.map { Suggestion(it, "placeholder", After.CLOSE, priority = 50.0) } +
                inFile.map { Suggestion(it, "placeholder", After.CLOSE, priority = 10.0) }
    }

    private fun placeholderNames(text: String, palette: Map<String, Color>): Set<String> =
        PLACEHOLDER.findAll(text)
            .filter { TagParser.parse(it.value, 0, palette)?.type == TagType.PLACEHOLDER }
            .map { it.groupValues[1] }
            .toSet()

    // The MiniMessage text of the string before the tag at [open], for the tags and placeholders it already has.
    private fun stringBefore(position: PsiElement?, open: Int): String {
        var element = position ?: return ""
        repeat(MAX_DEPTH) {
            val mapped = MiniMessageText.expand(element)
            if (mapped != null) {
                val index = mapped.indexOfFileOffset(open)
                if (index != -1) return mapped.text.substring(0, index)
            }
            element = element.parent ?: return ""
        }
        return ""
    }

    private fun lookup(suggestion: Suggestion): LookupElement {
        val element = LookupElementBuilder.create(suggestion.value)
            .withLookupStrings(suggestion.aliases + suggestion.value)
            .withIcon(suggestion.color?.let { ColorIcon(12, it) })
            .withTypeText(suggestion.typeText, true)
            .withTailText(suggestion.aliases.takeIf { it.isNotEmpty() }?.joinToString(prefix = "  ", separator = ", "), true)
            .withInsertHandler(HANDLERS.getValue(suggestion.after))
        return PrioritizedLookupElement.withPriority(element, suggestion.priority)
    }

    private companion object {
        const val MAX_DEPTH = 6

        val PLACEHOLDER = Regex("<([a-z0-9_-]+)>")

        val HANDLERS = After.entries.associateWith { after ->
            InsertHandler<LookupElement> { context, _ -> finish(context, after) }
        }

        fun finish(context: InsertionContext, after: After) {
            val document = context.document
            val editor = context.editor
            val tail = context.tailOffset
            val next = document.charsSequence.getOrNull(tail)

            when (after) {
                After.NOTHING -> Unit

                After.CLOSE -> {
                    if (next != '>') document.insertString(tail, ">")
                    editor.caretModel.moveToOffset(tail + 1)
                }

                After.COLON, After.COLON_AND_COMPLETE -> {
                    if (next != ':') document.insertString(tail, ":")
                    editor.caretModel.moveToOffset(tail + 1)
                    if (after == After.COLON_AND_COMPLETE) {
                        AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
                    }
                }

                After.QUOTED_TEXT -> {
                    document.insertString(tail, ":''>")
                    editor.caretModel.moveToOffset(tail + 2)
                }
            }
        }
    }
}
