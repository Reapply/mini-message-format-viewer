package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.event.MouseEvent

class MiniMessageViewModeController : Disposable {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val mouseListener = object : EditorMouseListener {
        override fun mouseClicked(event: EditorMouseEvent) {
            if (event.area != EditorMouseEventArea.EDITING_AREA) return

            val mouseEvent = event.mouseEvent
            if (mouseEvent.button != MouseEvent.BUTTON1 || mouseEvent.clickCount != 2) return

            val editor = event.editor
            val offset = editor.logicalPositionToOffset(event.logicalPosition)
            val text = editor.document.charsSequence

            if (!isMiniMessageLiteralAtOffset(text, offset)) return

            val viewModeEnabled = toggleViewMode()
            refreshAllEditors()

            val hintText = if (viewModeEnabled) {
                "MiniMessage VIEW mode"
            } else {
                "MiniMessage EDIT mode"
            }
            HintManager.getInstance().showInformationHint(editor, hintText)
        }
    }

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!MiniMessageSettings.getInstance().viewModeEnabled) return
            scheduleRefreshForDocument(event.document)
        }
    }

    init {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addEditorMouseListener(mouseListener, this)
        multicaster.addDocumentListener(documentListener, this)

        if (MiniMessageSettings.getInstance().viewModeEnabled) {
            refreshAllEditors()
        }
    }

    private fun toggleViewMode(): Boolean {
        val settings = MiniMessageSettings.getInstance()
        settings.viewModeEnabled = !settings.viewModeEnabled
        return settings.viewModeEnabled
    }

    private fun scheduleRefreshForDocument(document: Document) {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest(
            {
                EditorFactory.getInstance().allEditors
                    .asSequence()
                    .filter { !it.isDisposed }
                    .filter { it.document == document }
                    .forEach { editor -> refreshEditorFolding(editor) }
            },
            120
        )
    }

    private fun refreshAllEditors() {
        EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .forEach { editor -> refreshEditorFolding(editor) }
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

    private fun isMiniMessageLiteralAtOffset(text: CharSequence, offset: Int): Boolean {
        val literalRange = findStringLiteralRangeAtOffset(text, offset) ?: return false
        val contentStart = literalRange.first + 1
        val contentEndExclusive = literalRange.last

        if (contentStart >= contentEndExclusive) return false

        val content = text.subSequence(contentStart, contentEndExclusive)
        return TAG_REGEX.containsMatchIn(content)
    }

    private fun findStringLiteralRangeAtOffset(text: CharSequence, offset: Int): IntRange? {
        if (offset < 0 || offset >= text.length) return null

        return STRING_LITERAL_REGEX.findAll(text).map { match -> match.range }.firstOrNull { range ->
            val contentStart = range.first + 1
            val contentEnd = range.last - 1
            contentStart <= contentEnd && offset in contentStart..contentEnd
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
                val tagStart = contentStart + tagMatch.range.first
                val tagEndExclusive = contentStart + tagMatch.range.last + 1
                ranges.add(tagStart to tagEndExclusive)
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
            ApplicationManager.getApplication().getService(MiniMessageViewModeController::class.java)
        }
    }
}
