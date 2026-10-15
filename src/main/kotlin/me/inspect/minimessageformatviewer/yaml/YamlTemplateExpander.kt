package me.inspect.minimessageformatviewer.yaml

import com.intellij.psi.PsiElement
import me.inspect.minimessageformatviewer.rendering.MappedText
import me.inspect.minimessageformatviewer.rendering.TemplateExpander
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl

/** Reads YAML values, quoted or not, such as config messages written `message: <palette:error>Not allowed`. */
class YamlTemplateExpander : TemplateExpander {

    override fun expand(element: PsiElement): MappedText? {
        if (element.language != YAMLLanguage.INSTANCE) return null

        // Keys, mappings and the tokens inside a value are not read on their own.
        if (element !is YAMLScalar) return MappedText.EMPTY
        if (element !is YAMLScalarImpl) return null

        val builder = MappedText.Builder()
        for (range in element.contentRanges) {
            builder.append(range.substring(element.text), element.textRange.startOffset + range.startOffset)
        }
        return builder.build()
    }
}
