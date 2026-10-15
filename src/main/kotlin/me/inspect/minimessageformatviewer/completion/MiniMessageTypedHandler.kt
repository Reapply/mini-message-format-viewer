package me.inspect.minimessageformatviewer.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import me.inspect.minimessageformatviewer.rendering.MiniMessageText

/** Opens completion when `<` is typed in a string, and when `:` is typed inside a tag for its next argument. */
class MiniMessageTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped != '<' && charTyped != ':') return Result.CONTINUE

        val offset = editor.caretModel.offset
        if (charTyped == ':' && TagContext.at(editor.document.charsSequence, offset)?.name.isNullOrEmpty()) {
            return Result.CONTINUE
        }

        val element = file.findElementAt(offset - 1) ?: file.findElementAt(offset) ?: return Result.CONTINUE
        if (MiniMessageText.isInString(element)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }

        return Result.CONTINUE
    }
}
