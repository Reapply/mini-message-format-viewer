package me.inspect.minimessageformatviewer.kotlin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import me.inspect.minimessageformatviewer.rendering.MiniMessageRenderer
import me.inspect.minimessageformatviewer.rendering.RenderContext
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Checks calls like `Messages.send(player, WELCOME_EARNED, "amount" to x)` against the message: a `<placeholder>`
 * nothing fills stays in chat as written, and a pair the message has no `<name>` for does nothing.
 *
 * It applies to functions taking a `String` message and then a `vararg` of `Pair<String, String>`.
 */
class KotlinPlaceholderInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                check(expression, holder)
            }
        }

    private fun check(call: KtCallExpression, holder: ProblemsHolder) {
        val callee = call.calleeExpression as? KtNameReferenceExpression ?: return
        val function = callee.references.firstNotNullOfOrNull { it.resolve() }?.navigationElement as? KtNamedFunction
            ?: return

        val parameters = function.valueParameters
        val placeholdersIndex = parameters.indexOfFirst {
            it.isVarArg && it.typeReference?.text?.replace(" ", "") == "Pair<String,String>"
        }
        if (placeholdersIndex == -1) return

        val messageParameter = parameters.take(placeholdersIndex).lastOrNull { it.typeReference?.text == "String" } ?: return

        var message: KtExpression? = null
        val pairs = mutableListOf<KtExpression>()
        call.valueArguments.forEachIndexed { index, argument ->
            val name = argument.getArgumentName()?.asName?.asString()
            val parameter = if (name != null) {
                parameters.firstOrNull { it.name == name }
            } else {
                parameters.getOrNull(minOf(index, placeholdersIndex))
            }
            val expression = argument.getArgumentExpression() ?: return

            when (parameter) {
                messageParameter -> message = expression
                parameters[placeholdersIndex] -> {
                    // A spread array's names can't be seen.
                    if (argument.getSpreadElement() != null) return
                    pairs.add(expression)
                }
            }
        }

        val messageArgument = message ?: return
        val evaluator = KotlinStringEvaluator()
        val template = evaluator.evaluate(messageArgument)?.text ?: return

        val given = linkedMapOf<String, KtExpression>()
        for (pair in pairs) {
            val binary = KtPsiUtil.safeDeparenthesize(pair) as? KtBinaryExpression ?: return
            if (binary.operationReference.getReferencedName() != "to") return
            val name = evaluator.evaluate(binary.left)?.text ?: return
            given[name] = pair
        }

        val rendered = MiniMessageRenderer.render(template, RenderContext.of(call.project))

        for (name in rendered.placeholders - given.keys) {
            holder.registerProblem(messageArgument, "Message uses <$name> but no \"$name\" placeholder is passed")
        }

        for ((name, pair) in given) {
            if (name in rendered.placeholders || name in rendered.bareTags) continue
            holder.registerProblem(pair, "Message has no <$name> for this placeholder to fill")
        }
    }
}
