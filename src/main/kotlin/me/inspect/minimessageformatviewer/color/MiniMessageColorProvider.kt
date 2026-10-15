package me.inspect.minimessageformatviewer.color

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.ColorUtil
import java.awt.Color

/**
 * A gutter swatch for strings that are a single color, such as `const val ACCENT = "<#FCA821>"`; clicking it opens a
 * color picker that rewrites the hex.
 */
class MiniMessageColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? {
        val match = colorIn(element) ?: return null
        return runCatching { ColorUtil.fromHex(match.groupValues[1]) }.getOrNull()
    }

    override fun setColorTo(element: PsiElement, color: Color) {
        val match = colorIn(element) ?: return
        val file = element.containingFile ?: return
        val document = PsiDocumentManager.getInstance(element.project).getDocument(file) ?: return

        val hexRange = match.groups[1]!!.range
        val start = element.textRange.startOffset + hexRange.first
        val old = match.groupValues[1]
        val hex = ColorUtil.toHex(color).let { if (old == old.lowercase()) it.lowercase() else it.uppercase() }

        WriteCommandAction.runWriteCommandAction(element.project, "Change Color", null, {
            document.replaceString(start, start + old.length, hex)
        }, file)
    }

    // Only leaves, so each string gets one swatch.
    private fun colorIn(element: PsiElement): MatchResult? {
        if (element.firstChild != null) return null

        val text = element.text
        val match = SINGLE_COLOR.matchEntire(text.trim('"', '\'')) ?: return null

        val isString = text.length >= 2 && text.first() in QUOTES && text.last() == text.first() ||
                element.parent?.parent?.text?.let { it.length == text.length + 2 && it.first() in QUOTES } == true
        if (!isString) return null

        val offset = text.indexOf(match.value)
        return SINGLE_COLOR.find(text, offset)
    }

    private companion object {
        val SINGLE_COLOR = Regex("""<(?:(?:color|colour|c):)?#([0-9a-fA-F]{6})>""")
        val QUOTES = setOf('"', '\'')
    }
}
