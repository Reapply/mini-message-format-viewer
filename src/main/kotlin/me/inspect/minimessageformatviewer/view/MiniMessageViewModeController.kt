package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings

class MiniMessageViewModeController : Disposable {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val guardedBlocksByDocument = LinkedHashMap<Document, MutableList<RangeMarker>>()

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
        if (!enabled) {
            clearAllGuardedBlocks()
        }
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
        val document = editor.document
        val foldingModel = editor.foldingModel
        val viewModeEnabled = MiniMessageSettings.getInstance().viewModeEnabled
        val text = document.charsSequence

        refreshDocumentGuards(document, text, viewModeEnabled)

        foldingModel.runBatchFoldingOperation {
            foldingModel.allFoldRegions
                .asSequence()
                .filter { region -> isViewModeTagFoldRegion(region, text) }
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

    private fun refreshDocumentGuards(document: Document, text: CharSequence, viewModeEnabled: Boolean) {
        ApplicationManager.getApplication().runWriteAction {
            removeDocumentGuardBlocks(document)

            if (!viewModeEnabled) return@runWriteAction

            val ranges = collectMiniMessageContentRanges(text)
            if (ranges.isEmpty()) return@runWriteAction

            val guardedBlocks = mutableListOf<RangeMarker>()
            ranges.forEach { (start, endExclusive) ->
                if (endExclusive <= start) return@forEach
                val marker = document.createGuardedBlock(start, endExclusive)
                marker.putUserData(VIEW_MODE_CONTENT_GUARD_KEY, true)
                guardedBlocks.add(marker)
            }

            if (guardedBlocks.isNotEmpty()) {
                guardedBlocksByDocument[document] = guardedBlocks
            }
        }
    }

    private fun collectMiniMessageContentRanges(text: CharSequence): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()

        STRING_LITERAL_REGEX.findAll(text).forEach { literalMatch ->
            val contentStart = literalMatch.range.first + 1
            val contentEndExclusive = literalMatch.range.last
            if (contentStart >= contentEndExclusive) return@forEach

            val content = text.subSequence(contentStart, contentEndExclusive)
            if (TAG_REGEX.containsMatchIn(content)) {
                ranges.add(contentStart to contentEndExclusive)
            }
        }

        return ranges
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

    private fun isViewModeTagFoldRegion(region: FoldRegion, text: CharSequence): Boolean {
        if (region.getUserData(VIEW_MODE_TAG_FOLD_KEY) == true) return true
        if (region.placeholderText != "") return false

        val start = region.startOffset
        val end = region.endOffset
        if (start < 0 || end > text.length || end <= start) return false

        val foldedText = text.subSequence(start, end).toString()
        return TAG_REGEX.matches(foldedText)
    }

    private fun removeDocumentGuardBlocks(document: Document) {
        val blocks = guardedBlocksByDocument.remove(document).orEmpty()
        blocks.forEach { marker ->
            if (!marker.isValid) return@forEach
            runCatching { document.removeGuardedBlock(marker) }
        }
    }

    private fun clearAllGuardedBlocks() {
        ApplicationManager.getApplication().runWriteAction {
            guardedBlocksByDocument.keys.toList().forEach(::removeDocumentGuardBlocks)
        }
    }

    override fun dispose() {
        clearAllGuardedBlocks()
    }

    companion object {
        private val STRING_LITERAL_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
        private val TAG_REGEX = Regex("<[^<>\\n]+>")
        private val VIEW_MODE_TAG_FOLD_KEY = Key.create<Boolean>("mini.message.viewer.view.mode.tag.fold")
        private val VIEW_MODE_CONTENT_GUARD_KEY = Key.create<Boolean>("mini.message.viewer.view.mode.content.guard")

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
