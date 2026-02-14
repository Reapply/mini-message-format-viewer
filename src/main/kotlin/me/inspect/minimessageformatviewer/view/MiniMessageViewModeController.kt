package me.inspect.minimessageformatviewer.view

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.Cursor
import java.awt.event.MouseEvent
import kotlin.math.max
import kotlin.math.min

class MiniMessageViewModeController : Disposable {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!MiniMessageSettings.getInstance().viewModeEnabled) return
            scheduleRefreshForDocument(event.document)
        }
    }

    private val mouseMotionListener = object : EditorMouseMotionListener {
        override fun mouseMoved(event: EditorMouseEvent) {
            if (event.area != EditorMouseEventArea.EDITING_AREA) return

            val editor = event.editor
            if (!MiniMessageSettings.getInstance().viewModeEnabled) {
                resetMouseState(editor)
                return
            }

            val offset = resolveOffset(editor, event) ?: run {
                resetMouseState(editor)
                return
            }

            val range = findInteractiveRange(editor, offset)
            val hasClick = range?.interaction?.click != null
            val hoverText = range?.interaction?.hover?.toDisplayText()

            editor.contentComponent.cursor = Cursor.getPredefinedCursor(
                if (hasClick) Cursor.HAND_CURSOR else Cursor.TEXT_CURSOR
            )
            editor.contentComponent.toolTipText = hoverText
        }
    }

    private val mouseListener = object : EditorMouseListener {
        override fun mouseExited(event: EditorMouseEvent) {
            resetMouseState(event.editor)
        }

        override fun mouseClicked(event: EditorMouseEvent) {
            if (event.area != EditorMouseEventArea.EDITING_AREA) return
            if (!MiniMessageSettings.getInstance().viewModeEnabled) return

            val mouseEvent = event.mouseEvent
            if (mouseEvent.button != MouseEvent.BUTTON1 || mouseEvent.clickCount != 1) return

            val editor = event.editor
            val offset = resolveOffset(editor, event) ?: return
            val click = findInteractiveRange(editor, offset)?.interaction?.click ?: return

            val payload = buildString {
                append("메세지를 클릭했습니다!")
                append(" [")
                append(click.action)
                if (click.value.isNotBlank()) {
                    append(": ")
                    append(click.value)
                }
                append("]")
            }
            HintManager.getInstance().showInformationHint(editor, payload)
        }
    }

    init {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addDocumentListener(documentListener, this)
        multicaster.addEditorMouseMotionListener(mouseMotionListener, this)
        multicaster.addEditorMouseListener(mouseListener, this)

        if (MiniMessageSettings.getInstance().viewModeEnabled) {
            refreshAllEditors()
        }
    }

    fun toggleViewMode(editor: Editor?): Boolean {
        val settings = MiniMessageSettings.getInstance()
        settings.viewModeEnabled = !settings.viewModeEnabled
        val viewEnabled = settings.viewModeEnabled

        refreshAllEditors()

        editor?.let {
            val hintText = if (viewEnabled) {
                "MiniMessage VIEW mode (수정 불가)"
            } else {
                "MiniMessage EDIT mode"
            }
            HintManager.getInstance().showInformationHint(it, hintText)
        }

        return viewEnabled
    }

    private fun scheduleRefreshForDocument(document: Document) {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest(
            {
                EditorFactory.getInstance().allEditors
                    .asSequence()
                    .filter { !it.isDisposed }
                    .filter { it.document == document }
                    .forEach(::refreshEditorState)
            },
            120
        )
    }

    private fun refreshAllEditors() {
        EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .forEach(::refreshEditorState)
    }

    private fun refreshEditorState(editor: Editor) {
        val text = editor.document.charsSequence
        val parsed = collectViewData(text)
        val viewModeEnabled = MiniMessageSettings.getInstance().viewModeEnabled

        refreshFolding(editor, parsed.tagRanges, viewModeEnabled)
        refreshGuardedBlocks(editor.document, parsed.protectedRanges, viewModeEnabled)
        editor.putUserData(INTERACTIVE_RANGES_KEY, if (viewModeEnabled) parsed.interactiveRanges else emptyList())

        if (!viewModeEnabled) {
            resetMouseState(editor)
        }
    }

    private fun refreshFolding(editor: Editor, tagRanges: List<IntRange>, viewModeEnabled: Boolean) {
        val foldingModel = editor.foldingModel
        foldingModel.runBatchFoldingOperation {
            foldingModel.allFoldRegions
                .asSequence()
                .filter { region -> region.getUserData(VIEW_MODE_TAG_FOLD_KEY) == true }
                .toList()
                .forEach(foldingModel::removeFoldRegion)

            if (!viewModeEnabled) return@runBatchFoldingOperation

            tagRanges.forEach { range ->
                if (range.first >= range.last + 1) return@forEach

                val start = range.first
                val endExclusive = range.last + 1
                val region = foldingModel.addFoldRegion(start, endExclusive, "") ?: return@forEach
                region.isExpanded = false
                region.putUserData(VIEW_MODE_TAG_FOLD_KEY, true)
            }
        }
    }

    private fun refreshGuardedBlocks(document: Document, protectedRanges: List<IntRange>, viewModeEnabled: Boolean) {
        val documentEx = document as? DocumentEx ?: return

        documentEx.guardedBlocks
            .asSequence()
            .filter { marker -> marker.getUserData(VIEW_MODE_GUARDED_KEY) == true }
            .toList()
            .forEach(documentEx::removeGuardedBlock)

        if (!viewModeEnabled) return

        protectedRanges.forEach { range ->
            if (range.first >= range.last + 1) return@forEach

            val marker = documentEx.createGuardedBlock(range.first, range.last + 1)
            marker.isGreedyToLeft = true
            marker.isGreedyToRight = true
            marker.putUserData(VIEW_MODE_GUARDED_KEY, true)
        }
    }

    private fun collectViewData(text: CharSequence): ParsedViewData {
        val tagRanges = mutableListOf<IntRange>()
        val protectedRanges = mutableListOf<IntRange>()
        val interactiveRanges = mutableListOf<InteractiveRange>()

        STRING_LITERAL_REGEX.findAll(text).forEach { literalMatch ->
            val contentStart = literalMatch.range.first + 1
            val contentEndExclusive = literalMatch.range.last
            if (contentStart >= contentEndExclusive) return@forEach

            val literalContent = text.subSequence(contentStart, contentEndExclusive)
            if (!TAG_REGEX.containsMatchIn(literalContent)) return@forEach

            protectedRanges.add(contentStart until contentEndExclusive)

            TAG_REGEX.findAll(literalContent).forEach { tagMatch ->
                val start = contentStart + tagMatch.range.first
                val endExclusive = contentStart + tagMatch.range.last + 1
                tagRanges.add(start until endExclusive)
            }

            interactiveRanges += collectInteractiveRanges(literalContent, contentStart)
        }

        return ParsedViewData(tagRanges, protectedRanges, interactiveRanges)
    }

    private fun collectInteractiveRanges(content: CharSequence, absoluteStartOffset: Int): List<InteractiveRange> {
        val ranges = mutableListOf<InteractiveRange>()
        val clickStack = ArrayDeque<ClickEventData?>()
        val hoverStack = ArrayDeque<HoverEventData?>()
        var interaction = InteractionData(null, null)

        var index = 0
        while (index < content.length) {
            if (content[index] == '<' && (index == 0 || content[index - 1] != '\\')) {
                val tagEnd = content.indexOf('>', index + 1)
                if (tagEnd != -1) {
                    val rawTag = content.substring(index + 1, tagEnd).trim()
                    if (rawTag.isNotEmpty()) {
                        val selfClosing = rawTag.endsWith("/")
                        val body = if (selfClosing) rawTag.dropLast(1).trim() else rawTag
                        val lower = body.lowercase()

                        when {
                            lower == "reset" -> {
                                clickStack.clear()
                                hoverStack.clear()
                                interaction = InteractionData(null, null)
                            }

                            lower.startsWith("/") -> {
                                when (lower.removePrefix("/").trim()) {
                                    "click" -> {
                                        interaction = interaction.copy(
                                            click = clickStack.removeLastOrNull()
                                        )
                                    }

                                    "hover" -> {
                                        interaction = interaction.copy(
                                            hover = hoverStack.removeLastOrNull()
                                        )
                                    }
                                }
                            }

                            !selfClosing && lower.startsWith("click:") -> {
                                parseClick(body)?.let { click ->
                                    clickStack.addLast(interaction.click)
                                    interaction = interaction.copy(click = click)
                                }
                            }

                            !selfClosing && lower.startsWith("hover:") -> {
                                parseHover(body)?.let { hover ->
                                    hoverStack.addLast(interaction.hover)
                                    interaction = interaction.copy(hover = hover)
                                }
                            }
                        }
                    }

                    index = tagEnd + 1
                    continue
                }
            }

            if (interaction.click != null || interaction.hover != null) {
                val absoluteOffset = absoluteStartOffset + index
                appendInteractiveRange(ranges, absoluteOffset, interaction)
            }

            index++
        }

        return ranges
    }

    private fun appendInteractiveRange(
        ranges: MutableList<InteractiveRange>,
        absoluteOffset: Int,
        interaction: InteractionData
    ) {
        val last = ranges.lastOrNull()
        if (last != null && last.endExclusive == absoluteOffset && last.interaction == interaction) {
            ranges[ranges.lastIndex] = last.copy(endExclusive = absoluteOffset + 1)
            return
        }

        ranges += InteractiveRange(
            start = absoluteOffset,
            endExclusive = absoluteOffset + 1,
            interaction = interaction
        )
    }

    private fun parseClick(body: String): ClickEventData? {
        val args = body.substringAfter(":", "").trim()
        if (args.isBlank()) return null

        val action = args.substringBefore(':').trim().lowercase()
        if (action.isBlank()) return null

        val value = normalizeValue(args.substringAfter(':', "").trim())
        return ClickEventData(action, value)
    }

    private fun parseHover(body: String): HoverEventData? {
        val args = body.substringAfter(":", "").trim()
        if (args.isBlank()) return null

        val action = args.substringBefore(':').trim().lowercase()
        if (action.isBlank()) return null

        val value = normalizeValue(args.substringAfter(':', "").trim())
        return HoverEventData(action, value)
    }

    private fun normalizeValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return trimmed.substring(1, trimmed.length - 1)
                    .replace("\\n", "\n")
            }
        }
        return trimmed.replace("\\n", "\n")
    }

    private fun findInteractiveRange(editor: Editor, offset: Int): InteractiveRange? {
        val ranges = editor.getUserData(INTERACTIVE_RANGES_KEY) ?: return null
        if (ranges.isEmpty()) return null

        val clampedOffset = min(max(offset, 0), editor.document.textLength)
        return ranges.firstOrNull { range ->
            clampedOffset in range.start until range.endExclusive
        }
    }

    private fun resolveOffset(editor: Editor, event: EditorMouseEvent): Int? {
        val point = event.mouseEvent.point ?: return null
        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
        return offset.takeIf { it in 0..editor.document.textLength }
    }

    private fun resetMouseState(editor: Editor) {
        editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
        editor.contentComponent.toolTipText = null
    }

    override fun dispose() = Unit

    data class ClickEventData(val action: String, val value: String)
    data class HoverEventData(val action: String, val value: String) {
        fun toDisplayText(): String {
            if (action == "show_text") return value
            return if (value.isBlank()) action else "$action: $value"
        }
    }

    private data class InteractionData(
        val click: ClickEventData?,
        val hover: HoverEventData?
    )

    private data class InteractiveRange(
        val start: Int,
        val endExclusive: Int,
        val interaction: InteractionData
    )

    private data class ParsedViewData(
        val tagRanges: List<IntRange>,
        val protectedRanges: List<IntRange>,
        val interactiveRanges: List<InteractiveRange>
    )

    companion object {
        private val STRING_LITERAL_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
        private val TAG_REGEX = Regex("<[^<>\\n]+>")

        private val VIEW_MODE_TAG_FOLD_KEY = Key.create<Boolean>("mini.message.viewer.view.mode.tag.fold")
        private val VIEW_MODE_GUARDED_KEY = Key.create<Boolean>("mini.message.viewer.view.mode.guarded")
        private val INTERACTIVE_RANGES_KEY =
            Key.create<List<InteractiveRange>>("mini.message.viewer.view.mode.interactive.ranges")

        fun ensureInitialized() {
            getInstance()
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
    }
}
