package me.inspect.minimessageformatviewer.view

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.event.MouseEvent
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class MiniMessageViewModeController : Disposable {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!MiniMessageSettings.getInstance().viewModeEnabled) return
            scheduleRefreshForDocument(event.document)
        }
    }

    private val mouseListener = object : EditorMouseListener {
        override fun mouseClicked(event: EditorMouseEvent) {
            handleClickEvent(event)
        }
    }

    init {
        runOnEdt {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(mouseListener, this)

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

    private fun handleClickEvent(event: EditorMouseEvent) {
        if (event.area != EditorMouseEventArea.EDITING_AREA) return
        if (event.mouseEvent.button != MouseEvent.BUTTON1) return

        val editor = event.editor
        if (editor.isDisposed) return

        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(event.mouseEvent.point))
        val clickEvent = findClickEventAtOffset(editor.document.charsSequence, offset) ?: return

        showClickNotification(editor, clickEvent)
    }

    private fun findClickEventAtOffset(text: CharSequence, offset: Int): ClickEventInfo? {
        if (offset !in 0 until text.length) return null

        STRING_LITERAL_REGEX.findAll(text).forEach { literal ->
            val contentStart = literal.range.first + 1
            val contentEndExclusive = literal.range.last
            if (contentStart >= contentEndExclusive) return@forEach
            if (offset !in contentStart until contentEndExclusive) return@forEach

            val events = collectClickEventRanges(text, contentStart, contentEndExclusive)
            val match = events.firstOrNull { offset in it.start until it.end }
            if (match != null) return match.info
        }

        return null
    }

    private fun collectClickEventRanges(
        text: CharSequence,
        contentStart: Int,
        contentEndExclusive: Int
    ): List<ClickEventRange> {
        data class OpenClick(val contentStartOffset: Int, val info: ClickEventInfo)

        val ranges = mutableListOf<ClickEventRange>()
        val stack = ArrayDeque<OpenClick>()

        var index = contentStart
        while (index < contentEndExclusive) {
            if (text[index] != '<' || isEscaped(text, index)) {
                index++
                continue
            }

            val tagEnd = findTagEnd(text, index + 1, contentEndExclusive)
            if (tagEnd == -1) {
                index++
                continue
            }

            val rawTag = text.subSequence(index + 1, tagEnd).toString().trim()
            val normalizedTag = if (rawTag.endsWith("/")) rawTag.dropLast(1).trimEnd() else rawTag
            if (normalizedTag.equals("/click", ignoreCase = true)) {
                val open = if (stack.isEmpty()) null else stack.removeLast()
                if (open != null && index > open.contentStartOffset) {
                    ranges.add(ClickEventRange(open.contentStartOffset, index, open.info))
                }
                index = tagEnd + 1
                continue
            }

            parseClickEventInfo(normalizedTag)?.let { clickInfo ->
                stack.addLast(OpenClick(tagEnd + 1, clickInfo))
            }
            index = tagEnd + 1
        }

        return ranges
    }

    private fun parseClickEventInfo(tagContent: String): ClickEventInfo? {
        val lower = tagContent.lowercase()
        if (!lower.startsWith("click:")) return null

        val body = tagContent.substringAfter(':', "").trim()
        if (body.isEmpty()) return null

        val actionEnd = body.indexOf(':').let { if (it == -1) body.length else it }
        val action = body.substring(0, actionEnd).trim().lowercase()
        if (action.isEmpty()) return null

        val rawPayload = if (actionEnd < body.length) body.substring(actionEnd + 1).trim() else ""
        val payload = stripWrappingQuotes(rawPayload)
        return ClickEventInfo(action, payload)
    }

    private fun buildClickHintText(event: ClickEventInfo): String {
        val type = event.action.ifBlank { "unknown" }
        val payload = event.payload.ifBlank { "(empty)" }.replace("\n", "\\n")
        return "CLICK TYPE: $type | $payload"
    }

    private fun showClickNotification(editor: Editor, event: ClickEventInfo) {
        val title = "MiniMessage ClickEvent"
        val content = buildClickHintText(event)
        val notification = Notification(NOTIFICATION_GROUP_ID, title, content, NotificationType.INFORMATION)
        Notifications.Bus.notify(notification, editor.project)
    }

    private fun stripWrappingQuotes(value: String): String {
        if (value.length < 2) return value
        val first = value.first()
        val last = value.last()
        val isSingleQuoted = first == '\'' && last == '\''
        val isDoubleQuoted = first == '"' && last == '"'
        return if (isSingleQuoted || isDoubleQuoted) value.substring(1, value.length - 1) else value
    }

    private fun findTagEnd(text: CharSequence, start: Int, limitExclusive: Int): Int {
        var cursor = start
        while (cursor < limitExclusive) {
            if (text[cursor] == '>') return cursor
            cursor++
        }
        return -1
    }

    private fun isEscaped(text: CharSequence, index: Int): Boolean {
        if (index <= 0 || text[index - 1] != '\\') return false
        var slashCount = 0
        var cursor = index - 1
        while (cursor >= 0 && text[cursor] == '\\') {
            slashCount++
            cursor--
        }
        return slashCount % 2 == 1
    }

    private fun refreshEditorFolding(editor: Editor) {
        val document = editor.document
        val foldingModel = editor.foldingModel
        val viewModeEnabled = MiniMessageSettings.getInstance().viewModeEnabled
        val text = document.charsSequence

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

    override fun dispose() = Unit

    companion object {
        private val STRING_LITERAL_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
        private val TAG_REGEX = Regex("<[^<>\\n]+>")
        private val VIEW_MODE_TAG_FOLD_KEY = Key.create<Boolean>("mini.message.viewer.view.mode.tag.fold")
        private const val NOTIFICATION_GROUP_ID = "MiniMessage Formatter"
        private val INITIALIZED = AtomicBoolean(false)

        fun ensureInitialized() {
            if (!INITIALIZED.compareAndSet(false, true)) return
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

    private data class ClickEventInfo(
        val action: String,
        val payload: String
    )

    private data class ClickEventRange(
        val start: Int,
        val end: Int,
        val info: ClickEventInfo
    )
}
