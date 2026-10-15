package me.inspect.minimessageformatviewer.java

import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiUtil
import me.inspect.minimessageformatviewer.rendering.MappedText
import me.inspect.minimessageformatviewer.rendering.TemplateExpander

/**
 * Reads a Java string concatenation such as `Messages.ACCENT + "text"` as one message, filling in the constants it
 * uses, so the color they hold carries into the literals after them.
 */
class JavaTemplateExpander : TemplateExpander {

    override fun expand(element: PsiElement): MappedText? {
        val expression = when (element) {
            is PsiJavaToken -> element.parent as? PsiLiteralExpression ?: return null
            is PsiExpression -> element
            else -> return null
        }

        // A part of a concatenation is shown with the whole of it.
        if (enclosingConcatenation(expression) != null) return MappedText.EMPTY
        if (element is PsiJavaToken || !isConcatenation(expression)) return null

        val builder = MappedText.Builder()
        append(expression, builder)
        return builder.build()
    }

    private fun append(expression: PsiExpression?, builder: MappedText.Builder) {
        when {
            expression == null -> Unit

            expression is PsiParenthesizedExpression -> append(expression.expression, builder)

            isConcatenation(expression) -> (expression as PsiPolyadicExpression).operands.forEach { append(it, builder) }

            expression is PsiLiteralExpression && expression.value is String -> {
                val text = expression.text
                val quote = if (text.startsWith("\"\"\"")) 3 else 1
                val start = if (quote == 3) text.indexOf('\n') + 1 else 1
                if (start <= 0 || text.length - quote < start) return
                builder.append(text.substring(start, text.length - quote), expression.textRange.startOffset + start)
            }

            else -> {
                val value = constant(expression) ?: return
                builder.appendInserted(value, expression.textRange)
            }
        }
    }

    // Falls back to a final field's initializer when the field's type can't be resolved, as without a JDK.
    private fun constant(expression: PsiExpression): String? {
        val helper = JavaPsiFacade.getInstance(expression.project).constantEvaluationHelper
        (helper.computeConstantExpression(expression) as? String)?.let { return it }

        val field = (expression as? PsiReferenceExpression)?.resolve() as? PsiField ?: return null
        if (!field.hasModifierProperty(PsiModifier.FINAL)) return null
        return helper.computeConstantExpression(field.initializer) as? String
    }

    private fun enclosingConcatenation(expression: PsiExpression): PsiPolyadicExpression? =
        (PsiUtil.skipParenthesizedExprUp(expression.parent) as? PsiPolyadicExpression)?.takeIf { isConcatenation(it) }

    // A string literal among the operands settles it even when the type can't be worked out.
    private fun isConcatenation(expression: PsiExpression): Boolean =
        expression is PsiPolyadicExpression &&
                expression.operationTokenType == JavaTokenType.PLUS &&
                (expression.type?.equalsToText(CommonClassNames.JAVA_LANG_STRING) == true ||
                        expression.operands.any { (it as? PsiLiteralExpression)?.value is String })
}
