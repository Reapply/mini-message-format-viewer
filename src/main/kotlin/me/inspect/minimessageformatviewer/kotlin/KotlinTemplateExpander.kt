package me.inspect.minimessageformatviewer.kotlin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.inspect.minimessageformatviewer.rendering.MappedText
import me.inspect.minimessageformatviewer.rendering.TemplateExpander
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Fills in `${ACCENT}`, `${Messages.ERROR}` and helper calls like `${num("<amount>")}` with what they make, so a
 * palette object such as `const val ACCENT = "<#FCA821>"` colors the text after it. An entry whose value
 * [KotlinStringEvaluator] can't work out is left out of the text.
 */
class KotlinTemplateExpander : TemplateExpander {

    override fun expand(element: PsiElement): MappedText? {
        if (element !is KtStringTemplateExpression) return null

        val evaluator = KotlinStringEvaluator(element)
        val builder = MappedText.Builder()
        for (entry in element.entries) {
            if (entry is KtStringTemplateEntryWithExpression) {
                val value = evaluator.evaluate(entry.expression) ?: continue
                builder.append(value.text, value.offsets, sourceOf(entry))
            } else {
                builder.append(entry.text, entry.textRange.startOffset)
            }
        }

        return builder.build()
    }

    // For a call only the function name takes the color, leaving its arguments to their own.
    private fun sourceOf(entry: KtStringTemplateEntryWithExpression): TextRange {
        val call = when (val expression = entry.expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
        return call?.calleeExpression?.textRange ?: entry.textRange
    }
}
