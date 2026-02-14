package me.inspect.minimessageformatviewer.rendering

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.inspect.minimessageformatviewer.rendering.model.MessageRenderModelFactory
import me.inspect.minimessageformatviewer.rendering.model.RenderMode
import me.inspect.minimessageformatviewer.rendering.model.StyledCharacter
import me.inspect.minimessageformatviewer.rendering.model.TagHighlight
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import me.inspect.minimessageformatviewer.view.MiniMessageViewModeController
import java.awt.Color
import java.awt.Font
import java.util.ArrayDeque

class MiniMessageAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text ?: return
        if (!isStringLiteral(text)) return

        val fileOffset = element.textRange.startOffset
        val stringStart = findStringStart(text)
        val stringEndExclusive = findStringEndExclusive(text)

        if (stringStart == -1 || stringEndExclusive == -1 || stringStart >= stringEndExclusive) return

        val settings = runCatching { MiniMessageSettings.getInstance() }.getOrNull() ?: return
        if (settings.highlightOnlyInViewMode && !settings.viewModeEnabled) return

        MiniMessageViewModeController.ensureInitialized()

        val renderMode = RenderMode.fromRaw(settings.renderMode)
        val sourceContent = text.substring(stringStart, stringEndExclusive)

        if (!shouldAnnotate(renderMode, sourceContent)) return

        val renderModel = MessageRenderModelFactory.create(renderMode)
        val renderOutput = renderModel.render(sourceContent, settings.customColors)
        val contentStartOffset = fileOffset + stringStart

        if (hasForcedGlobalEffect()) {
            applyForcedEffectToAllCharacters(holder, contentStartOffset, sourceContent.length)
        }

        renderOutput.tagHighlights.forEach { highlight ->
            highlightTag(holder, contentStartOffset, highlight)
        }
        renderOutput.styledCharacters.forEach { styled ->
            highlightStyledCharacter(holder, contentStartOffset, styled)
        }
        applyHoverEventTooltips(holder, contentStartOffset, sourceContent)
    }

    private fun shouldAnnotate(mode: RenderMode, source: String): Boolean {
        return when (mode) {
            RenderMode.BUILT_IN -> source.contains('<')
            RenderMode.ADVENTURE_API -> source.contains('<')
        }
    }

    private fun highlightTag(holder: AnnotationHolder, contentStartOffset: Int, tag: TagHighlight) {
        if (tag.start >= tag.end) return
        val color = ReadableColorUtil.adjustForEditor(tag.color ?: DEFAULT_TAG_COLOR)

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(contentStartOffset + tag.start, contentStartOffset + tag.end))
            .enforcedTextAttributes(TextAttributes(color, null, null, null, Font.BOLD))
            .create()
    }

    private fun highlightStyledCharacter(
        holder: AnnotationHolder,
        contentStartOffset: Int,
        styledCharacter: StyledCharacter
    ) {
        val fontType = buildFontType(styledCharacter.bold, styledCharacter.italic)
        val color = styledCharacter.color?.let(ReadableColorUtil::adjustForEditor)
        val shouldUnderline = styledCharacter.underlined || TestStyleOverrides.FORCE_UNDERLINE_ALL
        val shouldStrikethrough = styledCharacter.strikethrough || TestStyleOverrides.FORCE_STRIKETHROUGH_ALL
        val shouldObfuscated = styledCharacter.obfuscated || TestStyleOverrides.FORCE_OBFUSCATED_ALL

        val effects = collectEffects(shouldUnderline, shouldStrikethrough, shouldObfuscated, color)
        val primaryEffect = effects.entries.firstOrNull()

        if (color == null && fontType == Font.PLAIN && primaryEffect == null) return

        val textAttributes = TextAttributes(
            color,
            null,
            primaryEffect?.value,
            primaryEffect?.key,
            fontType
        )

        if (effects.size > 1) {
            val additional = LinkedHashMap<EffectType, Color>()
            effects.entries.drop(1).forEach { (type, effectColor) ->
                additional[type] = effectColor
            }
            textAttributes.setAdditionalEffects(additional)
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(contentStartOffset + styledCharacter.sourceIndex, contentStartOffset + styledCharacter.sourceIndex + 1))
            .enforcedTextAttributes(textAttributes)
            .create()
    }

    private fun applyForcedEffectToAllCharacters(holder: AnnotationHolder, contentStartOffset: Int, length: Int) {
        val forcedEffects = collectEffects(
            underline = TestStyleOverrides.FORCE_UNDERLINE_ALL,
            strikethrough = TestStyleOverrides.FORCE_STRIKETHROUGH_ALL,
            obfuscated = TestStyleOverrides.FORCE_OBFUSCATED_ALL,
            baseColor = null
        )
        val primaryEffect = forcedEffects.entries.firstOrNull() ?: return

        for (index in 0 until length) {
            val textAttributes = TextAttributes(
                null,
                null,
                primaryEffect.value,
                primaryEffect.key,
                Font.PLAIN
            )
            if (forcedEffects.size > 1) {
                val additional = LinkedHashMap<EffectType, Color>()
                forcedEffects.entries.drop(1).forEach { (type, effectColor) ->
                    additional[type] = effectColor
                }
                textAttributes.setAdditionalEffects(additional)
            }

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(contentStartOffset + index, contentStartOffset + index + 1))
                .enforcedTextAttributes(textAttributes)
                .create()
        }
    }

    private fun hasForcedGlobalEffect(): Boolean {
        return TestStyleOverrides.FORCE_UNDERLINE_ALL ||
                TestStyleOverrides.FORCE_STRIKETHROUGH_ALL ||
                TestStyleOverrides.FORCE_OBFUSCATED_ALL
    }

    private fun collectEffects(
        underline: Boolean,
        strikethrough: Boolean,
        obfuscated: Boolean,
        baseColor: Color?
    ): LinkedHashMap<EffectType, Color> {
        val effects = LinkedHashMap<EffectType, Color>(3)
        if (underline) {
            effects[EffectType.LINE_UNDERSCORE] = baseColor ?: DEFAULT_UNDERLINE_COLOR
        }
        if (strikethrough) {
            effects[EffectType.STRIKEOUT] = baseColor ?: DEFAULT_STRIKE_COLOR
        }
        if (obfuscated) {
            effects[EffectType.BOXED] = baseColor ?: DEFAULT_OBFUSCATED_COLOR
        }
        return effects
    }

    private fun buildFontType(bold: Boolean, italic: Boolean): Int {
        var type = Font.PLAIN
        if (bold) type = type or Font.BOLD
        if (italic) type = type or Font.ITALIC
        return type
    }

    private fun applyHoverEventTooltips(holder: AnnotationHolder, contentStartOffset: Int, sourceContent: String) {
        val emittedKeys = getOrCreateHoverTooltipSessionKeys(holder)
        collectHoverEventRanges(sourceContent).forEach { range ->
            if (range.end <= range.start) return@forEach
            val absoluteStart = contentStartOffset + range.start
            val absoluteEnd = contentStartOffset + range.end
            val sessionKey = "$absoluteStart:$absoluteEnd:${range.tooltip.hashCode()}"
            if (!emittedKeys.add(sessionKey)) return@forEach

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(absoluteStart, absoluteEnd))
                .tooltip(range.tooltip)
                .create()
        }
    }

    private fun getOrCreateHoverTooltipSessionKeys(holder: AnnotationHolder): MutableSet<String> {
        val session = holder.currentAnnotationSession
        val existing = session.getUserData(HOVER_TOOLTIP_SESSION_KEY)
        if (existing != null) return existing

        val created = LinkedHashSet<String>()
        session.putUserData(HOVER_TOOLTIP_SESSION_KEY, created)
        return created
    }

    private fun collectHoverEventRanges(source: String): List<HoverTooltipRange> {
        data class HoverOpen(val contentStart: Int, val tooltip: String)

        val opens = ArrayDeque<HoverOpen>()
        val ranges = mutableListOf<HoverTooltipRange>()
        var index = 0

        while (index < source.length) {
            if (source[index] != '<' || isEscaped(source, index)) {
                index++
                continue
            }

            val tagEnd = source.indexOf('>', index + 1)
            if (tagEnd == -1) {
                index++
                continue
            }

            val tagContent = source.substring(index + 1, tagEnd).trim()
            if (tagContent.equals("/hover", ignoreCase = true)) {
                val open = if (opens.isEmpty()) null else opens.removeLast()
                if (open != null && index > open.contentStart) {
                    ranges.add(HoverTooltipRange(open.contentStart, index, open.tooltip))
                }
                index = tagEnd + 1
                continue
            }

            parseHoverTooltip(tagContent)?.let { tooltip ->
                opens.addLast(HoverOpen(tagEnd + 1, tooltip))
            }
            index = tagEnd + 1
        }

        return ranges
    }

    private fun parseHoverTooltip(tagContent: String): String? {
        val lower = tagContent.lowercase()
        if (!lower.startsWith("hover:")) return null

        val body = tagContent.substringAfter(':', "").trim()
        if (body.isEmpty()) return null

        val actionEnd = body.indexOf(':').let { if (it == -1) body.length else it }
        val action = body.substring(0, actionEnd).trim().lowercase()
        if (action.isEmpty()) return null

        val rawPayload = if (actionEnd < body.length) body.substring(actionEnd + 1).trim() else ""
        val payload = stripWrappingQuotes(rawPayload)

        val details = when (action) {
            "show_text" -> if (payload.isBlank()) "(empty)" else escapeHtml(payload)
            "show_item" -> if (payload.isBlank()) "ITEM: (empty)" else "ITEM: ${escapeHtml(payload)}"
            "show_entity" -> if (payload.isBlank()) "ENTITY: (empty)" else "ENTITY: ${escapeHtml(payload)}"
            else -> if (payload.isBlank()) "(no payload)" else escapeHtml(payload)
        }

        return "<html><b>TYPE: ${escapeHtml(action)}</b><br/>$details</html>"
    }

    private fun stripWrappingQuotes(value: String): String {
        if (value.length < 2) return value
        val first = value.first()
        val last = value.last()
        val isSingleQuoted = first == '\'' && last == '\''
        val isDoubleQuoted = first == '"' && last == '"'
        return if (isSingleQuoted || isDoubleQuoted) value.substring(1, value.length - 1) else value
    }

    private fun escapeHtml(text: String): String {
        return buildString(text.length + 16) {
            text.forEach { ch ->
                when (ch) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    '\n' -> append("<br/>")
                    else -> append(ch)
                }
            }
        }
    }

    private fun isEscaped(source: String, index: Int): Boolean {
        if (index <= 0 || source[index - 1] != '\\') return false
        var slashCount = 0
        var cursor = index - 1
        while (cursor >= 0 && source[cursor] == '\\') {
            slashCount++
            cursor--
        }
        return slashCount % 2 == 1
    }

    private fun isStringLiteral(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))
    }

    private fun findStringStart(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        return if (firstQuote != -1) firstQuote + 1 else -1
    }

    private fun findStringEndExclusive(text: String): Int {
        val firstQuote = text.indexOfFirst { it == '"' || it == '\'' }
        if (firstQuote == -1) return -1

        val quoteChar = text[firstQuote]
        val lastQuote = text.lastIndexOf(quoteChar)

        return if (lastQuote > firstQuote) lastQuote else -1
    }

    companion object {
        private val DEFAULT_TAG_COLOR = Color(0x6A, 0x6A, 0x6A)
        private val DEFAULT_UNDERLINE_COLOR = Color(0x5E, 0x5E, 0x5E)
        private val DEFAULT_STRIKE_COLOR = Color(0x7A, 0x7A, 0x7A)
        private val DEFAULT_OBFUSCATED_COLOR = Color(0x5D, 0x6E, 0x8A)
        private val HOVER_TOOLTIP_SESSION_KEY = Key.create<MutableSet<String>>("mini.message.hover.tooltip.session.keys")
    }

    private data class HoverTooltipRange(
        val start: Int,
        val end: Int,
        val tooltip: String
    )
}
