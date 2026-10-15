package me.inspect.minimessageformatviewer.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import me.inspect.minimessageformatviewer.color.ColorResolver
import me.inspect.minimessageformatviewer.parser.TagParser
import me.inspect.minimessageformatviewer.rendering.PaletteProvider
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import java.awt.Color

/**
 * Reads the palette from a Kotlin `val PALETTE = mapOf("accent" to ACCENT, ...)`, the map a server's own
 * `<palette:name>` tag resolver looks names up in.
 */
class KotlinPaletteProvider : PaletteProvider {

    override fun palette(project: Project): Map<String, Color> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(find(project), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun find(project: Project): Map<String, Color> {
        val evaluator = KotlinStringEvaluator()
        val palette = linkedMapOf<String, Color>()
        val fields = PsiShortNamesCache.getInstance(project).getFieldsByName(PROPERTY, GlobalSearchScope.allScope(project))

        for (field in fields) {
            val property = field.navigationElement as? KtProperty ?: continue
            val call = property.initializer as? KtCallExpression ?: continue

            for (argument in call.valueArguments) {
                val pair = argument.getArgumentExpression() as? KtBinaryExpression ?: continue
                if (pair.operationReference.getReferencedName() != "to") continue

                val name = evaluator.evaluate(pair.left)?.text ?: continue
                val value = evaluator.evaluate(pair.right)?.text?.takeIf { it.isNotEmpty() } ?: continue
                val color = TagParser.parse(value, 0)?.color ?: ColorResolver.resolve(value) ?: continue
                palette[name.lowercase()] = color
            }
        }

        return palette
    }

    private companion object {
        const val PROPERTY = "PALETTE"
    }
}
