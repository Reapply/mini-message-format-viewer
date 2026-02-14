package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings

class ToggleMiniMessageViewModeIntention : IntentionAction {
    override fun getText(): String {
        return if (MiniMessageSettings.getInstance().viewModeEnabled) {
            "Switch MiniMessage to Edit mode"
        } else {
            "Switch MiniMessage to View mode"
        }
    }

    override fun getFamilyName(): String = "MiniMessage"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        val offset = editor.caretModel.offset
        return MiniMessageViewModeController.isMiniMessageLiteralAtOffset(editor.document.charsSequence, offset)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val targetEditor = editor ?: return
        MiniMessageViewModeController.getInstance().toggleViewMode(targetEditor)
    }

    override fun startInWriteAction(): Boolean = false
}
