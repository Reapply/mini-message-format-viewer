package me.inspect.minimessageformatviewer.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Works out the string a Kotlin expression makes from what is written in code: string templates, `+`, `val`s, and
 * calls to functions that return a single expression, such as `fun num(text: String) = paint(VALUE, text)`.
 * Anything it can't see the value of (a `var`, a function with statements, a number) makes the result null.
 *
 * Literal text inside [root] keeps its file offsets; everything else counts as inserted.
 */
class KotlinStringEvaluator(private val root: PsiElement? = null) {

    fun evaluate(expression: KtExpression?): Evaluated? = evaluate(expression, emptyMap(), 0)

    private fun evaluate(expression: KtExpression?, env: Map<KtParameter, Evaluated>, depth: Int): Evaluated? {
        if (expression == null || depth > MAX_DEPTH) return null
        val next = depth + 1

        return when (expression) {
            is KtStringTemplateExpression -> template(expression, env, next)

            is KtParenthesizedExpression -> evaluate(expression.expression, env, next)

            is KtBinaryExpression -> {
                if (expression.operationToken != KtTokens.PLUS) return null
                val left = evaluate(expression.left, env, next) ?: return null
                val right = evaluate(expression.right, env, next) ?: return null
                left + right
            }

            is KtDotQualifiedExpression ->
                stringMethod(expression, env, next) ?: evaluate(expression.selectorExpression, env, next)

            is KtNameReferenceExpression -> when (val target = resolve(expression)) {
                is KtParameter -> env[target]
                is KtProperty -> if (target.isVar) null else evaluate(target.initializer, env, next)
                else -> null
            }

            is KtCallExpression -> call(expression, env, next)

            else -> null
        }
    }

    private fun template(expression: KtStringTemplateExpression, env: Map<KtParameter, Evaluated>, depth: Int): Evaluated? {
        var result = Evaluated.EMPTY
        for (entry in expression.entries) {
            val written = isWritten(entry)
            result += when {
                entry is KtStringTemplateEntryWithExpression -> evaluate(entry.expression, env, depth) ?: return null
                written -> Evaluated.written(entry.text, entry.textRange.startOffset)
                entry is KtEscapeStringTemplateEntry -> Evaluated.inserted(entry.unescapedValue)
                else -> Evaluated.inserted(entry.text)
            }
        }
        return result
    }

    private fun call(expression: KtCallExpression, env: Map<KtParameter, Evaluated>, depth: Int): Evaluated? {
        val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return null
        val function = resolve(callee) as? KtNamedFunction ?: return null
        val body = returnedExpression(function) ?: return null
        val parameters = function.valueParameters

        val bound = HashMap<KtParameter, Evaluated>()
        expression.valueArguments.forEachIndexed { index, argument ->
            val name = argument.getArgumentName()?.asName?.asString()
            val parameter = (if (name != null) parameters.firstOrNull { it.name == name } else parameters.getOrNull(index))
                ?.takeUnless { it.isVarArg }
                ?: return@forEachIndexed
            evaluate(argument.getArgumentExpression(), env, depth)?.let { bound[parameter] = it }
        }

        for (parameter in parameters) {
            if (parameter in bound) continue
            evaluate(parameter.defaultValue, bound.toMap(), depth)?.let { bound[parameter] = it }
        }

        return evaluate(body, bound, depth)
    }

    // `fun f() = x` or `fun f() { return x }`.
    private fun returnedExpression(function: KtNamedFunction): KtExpression? {
        val body = function.bodyExpression ?: return null
        if (!function.hasBlockBody()) return body

        val statement = (body as? KtBlockExpression)?.statements?.singleOrNull()
        return (statement as? KtReturnExpression)?.returnedExpression
    }

    // The few String functions palette helpers use, e.g. `color.substring(1)` to turn `<#FFF000>` into `#FFF000>`.
    private fun stringMethod(expression: KtDotQualifiedExpression, env: Map<KtParameter, Evaluated>, depth: Int): Evaluated? {
        val call = expression.selectorExpression as? KtCallExpression ?: return null
        val name = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return null
        if (name !in STRING_METHODS) return null

        val receiver = evaluate(expression.receiverExpression, env, depth) ?: return null
        val args = call.valueArguments.map { it.getArgumentExpression() }
        val text = receiver.text

        fun int(index: Int) = (args.getOrNull(index) as? KtConstantExpression)?.text?.toIntOrNull()
        fun string(index: Int) = args.getOrNull(index)?.let { evaluate(it, env, depth)?.text }

        return when (name) {
            "substring" -> {
                val start = int(0) ?: return null
                val end = if (args.size > 1) int(1) ?: return null else text.length
                if (start < 0 || end > text.length || start > end) null else receiver.slice(start, end)
            }

            "removePrefix" -> {
                val prefix = string(0) ?: return null
                if (text.startsWith(prefix)) receiver.slice(prefix.length, text.length) else receiver
            }

            "removeSuffix" -> {
                val suffix = string(0) ?: return null
                if (text.endsWith(suffix)) receiver.slice(0, text.length - suffix.length) else receiver
            }

            "removeSurrounding" -> {
                val prefix = string(0) ?: return null
                val suffix = if (args.size > 1) string(1) ?: return null else prefix
                if (text.length >= prefix.length + suffix.length && text.startsWith(prefix) && text.endsWith(suffix)) {
                    receiver.slice(prefix.length, text.length - suffix.length)
                } else {
                    receiver
                }
            }

            "lowercase" -> receiver.mapText { it.lowercase() }

            "uppercase" -> receiver.mapText { it.uppercase() }

            else -> null
        }
    }

    // navigationElement reaches the source of a library with attached sources.
    private fun resolve(reference: KtNameReferenceExpression): PsiElement? =
        reference.references.firstNotNullOfOrNull { it.resolve() }?.navigationElement

    private fun isWritten(element: PsiElement): Boolean =
        root != null && root.containingFile == element.containingFile && root.textRange.contains(element.textRange)

    private companion object {
        // Stops values that refer to each other in a loop.
        const val MAX_DEPTH = 32

        val STRING_METHODS = setOf("substring", "removePrefix", "removeSuffix", "removeSurrounding", "lowercase", "uppercase")
    }
}
