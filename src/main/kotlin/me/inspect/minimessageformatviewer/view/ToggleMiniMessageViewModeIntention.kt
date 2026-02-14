package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import me.inspect.minimessageformatviewer.i18n.MiniMessageI18n

class ToggleMiniMessageViewModeIntention : IntentionAction {
    override fun getText(): String = MiniMessageI18n.intentionToggleViewEdit()

    override fun getFamilyName(): String = "MiniMessage"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        return MiniMessageViewModeController.isMiniMessageLiteralAtOffset(
            editor.document.charsSequence,
            editor.caretModel.offset
        )
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        MiniMessageViewModeController.getInstance().toggleViewMode(editor)
    }

    override fun startInWriteAction(): Boolean = false
}
