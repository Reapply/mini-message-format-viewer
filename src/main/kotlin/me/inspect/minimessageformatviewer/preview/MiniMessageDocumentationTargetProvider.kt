package me.inspect.minimessageformatviewer.preview

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import me.inspect.minimessageformatviewer.rendering.MiniMessageRenderer
import me.inspect.minimessageformatviewer.rendering.MiniMessageText
import me.inspect.minimessageformatviewer.rendering.Rendered
import me.inspect.minimessageformatviewer.rendering.RenderContext

/** Hovering a MiniMessage string (or Ctrl+Q / F1 in it) shows it drawn the way it looks in chat. */
class MiniMessageDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        var element: PsiElement? = file.findElementAt(offset)

        // Not over `${ACCENT}` itself, which keeps the language's own documentation.
        repeat(MAX_DEPTH) {
            val candidate = element ?: return emptyList()
            if (candidate is PsiFile) return emptyList()

            val mapped = MiniMessageText.expand(candidate)
            if (mapped != null && mapped.containsFileOffset(offset)) {
                return if (render(candidate)?.tags?.isNotEmpty() == true) {
                    listOf(MiniMessagePreviewTarget(candidate))
                } else {
                    emptyList()
                }
            }

            element = candidate.parent
        }

        return emptyList()
    }

    private class MiniMessagePreviewTarget(private val element: PsiElement) : DocumentationTarget {

        override fun createPointer(): Pointer<out DocumentationTarget> {
            val pointer = SmartPointerManager.createPointer(element)
            return Pointer { pointer.element?.let(::MiniMessagePreviewTarget) }
        }

        override fun computePresentation(): TargetPresentation =
            TargetPresentation.builder("MiniMessage preview").presentation()

        override fun computeDocumentation(): DocumentationResult? {
            val rendered = render(element) ?: return null
            return DocumentationResult.documentation(MiniMessagePreview.html(rendered))
        }
    }

    private companion object {
        const val MAX_DEPTH = 6

        fun render(element: PsiElement): Rendered? {
            val mapped = MiniMessageText.expand(element) ?: return null
            return MiniMessageRenderer.render(mapped.text, RenderContext.of(element.project))
        }
    }
}
