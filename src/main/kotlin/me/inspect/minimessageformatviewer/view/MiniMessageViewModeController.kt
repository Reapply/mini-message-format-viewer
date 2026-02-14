package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.Cursor
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

class MiniMessageViewModeController : Disposable {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val viewerStateByEditor = LinkedHashMap<EditorEx, Boolean>()
    private val keyListenerByEditor = LinkedHashMap<Editor, KeyListener>()

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!MiniMessageSettings.getInstance().viewModeEnabled) return
            scheduleRefreshForDocument(event.document)
        }
    }

    private val mouseMotionListener = object : EditorMouseMotionListener {
        override fun mouseMoved(event: EditorMouseEvent) {
            updateCursorForEvent(event)
        }

        override fun mouseDragged(event: EditorMouseEvent) {
            updateCursorForEvent(event)
        }
    }

    init {
        runOnEdt {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseMotionListener(mouseMotionListener, this)
            installAltEnterListenersForAllEditors()

            if (MiniMessageSettings.getInstance().viewModeEnabled) {
                refreshAllEditors()
            }
        }
    }

    fun toggleViewMode(editor: Editor?): Boolean {
        val settings = MiniMessageSettings.getInstance()
        val actualViewMode = resolveCurrentViewMode(editor, settings.viewModeEnabled)
        settings.viewModeEnabled = !actualViewMode
        val enabled = settings.viewModeEnabled

        refreshAllEditors()
        if (!enabled) {
            restoreAllViewerStates()
            resetAllEditorCursors()
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

    private fun resolveCurrentViewMode(editor: Editor?, fallback: Boolean): Boolean {
        val editorEx = editor as? EditorEx ?: return fallback
        if (editorEx.isDisposed) return fallback
        return viewerStateByEditor.containsKey(editorEx)
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

    private fun updateCursorForEvent(event: EditorMouseEvent) {
        val editor = event.editor
        if (editor.isDisposed) return

        val cursorType = when {
            !MiniMessageSettings.getInstance().viewModeEnabled -> Cursor.TEXT_CURSOR
            event.area != EditorMouseEventArea.EDITING_AREA -> Cursor.TEXT_CURSOR
            isOffsetInsideMiniMessageLiteral(editor, event) -> Cursor.DEFAULT_CURSOR
            else -> Cursor.TEXT_CURSOR
        }

        val component = editor.contentComponent
        if (component.cursor?.type != cursorType) {
            component.cursor = Cursor.getPredefinedCursor(cursorType)
        }
    }

    private fun isOffsetInsideMiniMessageLiteral(editor: Editor, event: EditorMouseEvent): Boolean {
        val point = event.mouseEvent.point
        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
        return isMiniMessageLiteralAtOffset(editor.document.charsSequence, offset)
    }

    private fun refreshAllEditors() {
        installAltEnterListenersForAllEditors()
        EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .forEach(::refreshEditorFolding)
    }

    private fun installAltEnterListenersForAllEditors() {
        EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .forEach(::ensureAltEnterListenerInstalled)

        keyListenerByEditor.keys
            .filter { it.isDisposed }
            .toList()
            .forEach(keyListenerByEditor::remove)
    }

    private fun ensureAltEnterListenerInstalled(editor: Editor) {
        if (keyListenerByEditor.containsKey(editor)) return

        val listener = object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (!MiniMessageSettings.getInstance().viewModeEnabled) return
                if (event.keyCode != KeyEvent.VK_ENTER || !event.isAltDown) return

                val offset = editor.caretModel.offset
                if (!isMiniMessageLiteralAtOffset(editor.document.charsSequence, offset)) return

                event.consume()
                toggleViewMode(editor)
            }
        }

        editor.contentComponent.addKeyListener(listener)
        keyListenerByEditor[editor] = listener
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

        refreshEditorViewerState(editor, text, viewModeEnabled)

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

    private fun refreshEditorViewerState(editor: Editor, text: CharSequence, viewModeEnabled: Boolean) {
        val editorEx = editor as? EditorEx ?: return
        if (editorEx.isDisposed) return

        val hasMiniMessageContent = containsMiniMessageLiteral(text)
        val shouldBeViewer = viewModeEnabled && hasMiniMessageContent
        val originalViewerState = viewerStateByEditor[editorEx]

        if (shouldBeViewer) {
            if (originalViewerState == null) {
                viewerStateByEditor[editorEx] = editorEx.isViewer
            }
            editorEx.isViewer = true
            return
        }

        if (originalViewerState != null) {
            editorEx.isViewer = originalViewerState
            viewerStateByEditor.remove(editorEx)
        }
    }

    private fun containsMiniMessageLiteral(text: CharSequence): Boolean {
        return STRING_LITERAL_REGEX.findAll(text).any { literal ->
            val contentStart = literal.range.first + 1
            val contentEndExclusive = literal.range.last
            if (contentStart >= contentEndExclusive) return@any false

            val content = text.subSequence(contentStart, contentEndExclusive)
            TAG_REGEX.containsMatchIn(content)
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

    private fun isViewModeTagFoldRegion(region: FoldRegion, text: CharSequence): Boolean {
        if (region.getUserData(VIEW_MODE_TAG_FOLD_KEY) == true) return true
        if (region.placeholderText != "") return false

        val start = region.startOffset
        val end = region.endOffset
        if (start < 0 || end > text.length || end <= start) return false

        val foldedText = text.subSequence(start, end).toString()
        return TAG_REGEX.matches(foldedText)
    }

    private fun restoreAllViewerStates() {
        viewerStateByEditor.entries.toList().forEach { (editor, originalViewerState) ->
            if (editor.isDisposed) return@forEach
            editor.isViewer = originalViewerState
        }
        viewerStateByEditor.clear()
    }

    private fun resetAllEditorCursors() {
        EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .forEach { editor -> editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) }
    }

    override fun dispose() {
        restoreAllViewerStates()
        resetAllEditorCursors()
        keyListenerByEditor.forEach { (editor, listener) ->
            if (!editor.isDisposed) {
                editor.contentComponent.removeKeyListener(listener)
            }
        }
        keyListenerByEditor.clear()
    }

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
