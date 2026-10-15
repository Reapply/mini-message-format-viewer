package me.inspect.minimessageformatviewer.rendering

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/**
 * Reads a string expression of some language into MiniMessage text, filling in what the language inserts into it,
 * such as the constant behind Kotlin's `${ACCENT}`.
 */
interface TemplateExpander {

    /** The MiniMessage text of [element], or null to leave it to the plain string literal handling. */
    fun expand(element: PsiElement): MappedText?

    companion object {
        val EP_NAME: ExtensionPointName<TemplateExpander> =
            ExtensionPointName.create("me.inspect.mini-message-format-viewer.templateExpander")
    }
}
