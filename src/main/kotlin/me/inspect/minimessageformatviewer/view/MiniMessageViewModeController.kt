package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings

class MiniMessageViewModeController : Disposable {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!MiniMessageSettings.getInstance().viewModeEnabled) return
            scheduleRefreshForDocument(event.document)
        }
    }

    init {
        runOnEdt {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this)

            if (MiniMessageSettings.getInstance().viewModeEnabled) {
                refreshAllEditors()
            }
        }
    }

    fun toggleViewMode(editor: Editor?): Boolean {
        val settings = MiniMessageSettings.getInstance()
        settings.viewModeEnabled = !settings.viewModeEnabled
        val enabled = settings.viewModeEnabled

        refreshAllEditors()
        restartHighlighting()
        editor?.let {
            HintManager.getInstance().showInformationHint(
                it,
                if (enabled) "MiniMessage VIEW mode" else "MiniMessage EDIT mode"
            )
        }

        return enabled
    }

    private fun scheduleRefreshForDocument(document: Document) {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest(
            {
                EditorFactory.getInstance().allEditors
                    .asSequence()
                    .filter { !it.isDisposed }
                    .filter { it.document == document }
                    .forEach(::refreshEditorFolding)
            },
            120
        )
    }

    private fun refreshAllEditors() {
        EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .forEach(::refreshEditorFolding)
    }

    private fun restartHighlighting() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    private fun refreshEditorFolding(editor: Editor) {
        val foldingModel = editor.foldingModel
        val viewModeEnabled = MiniMessageSettings.getInstance().viewModeEnabled
        val text = editor.document.charsSequence

        foldingModel.runBatchFoldingOperation {
            foldingModel.allFoldRegions
                .asSequence()
                .filter { region -> region.getUserData(VIEW_MODE_TAG_FOLD_KEY) == true }
                .toList()
                .forEach { region -> foldingModel.removeFoldRegion(region) }

            if (!viewModeEnabled) return@runBatchFoldingOperation

            collectTagRanges(text).forEach { range ->
                if (range.second <= range.first) return@forEach

                val region = foldingModel.addFoldRegion(range.first, range.second, "") ?: return@forEach
                region.isExpanded = false
                region.putUserData(VIEW_MODE_TAG_FOLD_KEY, true)
            }
        }
    }

    private fun collectTagRanges(text: CharSequence): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()

        STRING_LITERAL_REGEX.findAll(text).forEach { literalMatch ->
            val contentStart = literalMatch.range.first + 1
            val contentEndExclusive = literalMatch.range.last
            if (contentStart >= contentEndExclusive) return@forEach

            val content = text.subSequence(contentStart, contentEndExclusive)
            TAG_REGEX.findAll(content).forEach { tagMatch ->
                val start = contentStart + tagMatch.range.first
                val endExclusive = contentStart + tagMatch.range.last + 1
                ranges.add(start to endExclusive)
            }
        }

        return ranges
    }

    override fun dispose() = Unit

    companion object {
        private val STRING_LITERAL_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
        private val TAG_REGEX = Regex("<[^<>\\n]+>")
        private val VIEW_MODE_TAG_FOLD_KEY = Key.create<Boolean>("mini.message.viewer.view.mode.tag.fold")

        fun ensureInitialized() {
            runOnEdt { getInstance() }
        }

        fun getInstance(): MiniMessageViewModeController {
            return ApplicationManager.getApplication().getService(MiniMessageViewModeController::class.java)
        }

        fun isMiniMessageLiteralAtOffset(text: CharSequence, offset: Int): Boolean {
            if (offset < 0 || offset >= text.length) return false

            return STRING_LITERAL_REGEX.findAll(text).any { literal ->
                val contentStart = literal.range.first + 1
                val contentEndExclusive = literal.range.last
                if (contentStart >= contentEndExclusive) return@any false
                if (offset !in contentStart until contentEndExclusive) return@any false

                val content = text.subSequence(contentStart, contentEndExclusive)
                TAG_REGEX.containsMatchIn(content)
            }
        }

        private inline fun runOnEdt(crossinline action: () -> Unit) {
            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread) {
                action()
            } else {
                app.invokeLater { action() }
            }
        }
    }
}
