package me.inspect.minimessageformatviewer.i18n

import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import me.inspect.minimessageformatviewer.settings.UiLanguage

object MiniMessageI18n {
    private fun language(): UiLanguage {
        val raw = runCatching { MiniMessageSettings.getInstance().uiLanguage }.getOrNull()
        return UiLanguage.fromRaw(raw)
    }

    private fun text(english: String, korean: String): String {
        return if (language() == UiLanguage.KOREAN) korean else english
    }

    fun settingsDisplayName() = text("MiniMessage Colors", "MiniMessage 색상")
    fun settingsInfoLabel() = text(
        "Customize MiniMessage color mappings. Add your own color names or modify existing ones.",
        "MiniMessage 색상 매핑을 설정합니다. 사용자 색상 이름을 추가하거나 기존 값을 수정할 수 있습니다."
    )
    fun settingsLanguageLabel() = text("Language", "언어")
    fun settingsRenderingModelLabel() = text("Rendering Model", "렌더링 모델")
    fun settingsHighlightOnlyViewMode() = text("Show highlighting only in View mode", "View 모드에서만 하이라이트 표시")
    fun settingsEnableRealClickActions() = text("Enable real ClickEvent actions", "실제 ClickEvent 동작 활성화")
    fun settingsAllowOpenUrlAction() = text("Allow open_url action", "open_url 액션 허용")
    fun settingsAllowCopyAction() = text("Allow copy_to_clipboard action", "copy_to_clipboard 액션 허용")
    fun settingsAllowSuggestAction() = text(
        "Allow suggest_command action (insert at caret)",
        "suggest_command 액션 허용 (커서 위치 삽입)"
    )
    fun settingsAddCustomColor() = text("Add Custom Color", "커스텀 색상 추가")
    fun settingsRemoveSelected() = text("Remove Selected", "선택 항목 삭제")
    fun settingsResetDefaults() = text("Reset Defaults", "기본값 복원")

    fun tableColumnColorName() = text("Color Name", "색상 이름")
    fun tableColumnHex() = text("Hex (without #)", "Hex (# 제외)")
    fun tableColumnPreview() = text("Preview", "미리보기")
    fun tableColumnType() = text("Type", "유형")
    fun tableTypeDefault() = text("Default", "기본")
    fun tableTypeCustom() = text("Custom", "사용자")

    fun intentionToggleViewEdit() = text("Toggle MiniMessage View/Edit mode", "MiniMessage View/Edit 모드 전환")
    fun hintViewMode(enabled: Boolean) = if (enabled) {
        text("MiniMessage VIEW mode", "MiniMessage VIEW 모드")
    } else {
        text("MiniMessage EDIT mode", "MiniMessage EDIT 모드")
    }

    fun clickTypeLabel(type: String, payload: String) = text(
        "CLICK TYPE: $type | $payload",
        "클릭 타입: $type | $payload"
    )
    fun clickPreviewOnly() = text("Preview only (real action disabled)", "미리보기 전용 (실제 액션 비활성화)")
    fun clickRunCommandUnsupported() = text(
        "run_command is not executable in IDE context",
        "IDE 환경에서는 run_command를 실행할 수 없습니다"
    )
    fun clickUnsupportedAction(action: String) = text(
        "Unsupported action: $action",
        "지원하지 않는 액션: $action"
    )
    fun clickActionDisabled(action: String) = text(
        "$action action is disabled in settings",
        "설정에서 $action 액션이 비활성화되어 있습니다"
    )
    fun clickPayloadEmpty(action: String) = text(
        "$action payload is empty",
        "$action payload가 비어 있습니다"
    )
    fun clickOpenedUrl() = text("Opened URL", "URL을 열었습니다")
    fun clickFailedOpenUrl(message: String) = text(
        "Failed to open URL: $message",
        "URL 열기에 실패했습니다: $message"
    )
    fun clickCopiedPayload() = text("Copied payload to clipboard", "payload를 클립보드에 복사했습니다")
    fun clickSuggestConfirmationOpened() = text(
        "suggest_command confirmation opened",
        "suggest_command 확인 창을 열었습니다"
    )
    fun clickSuggestDialogTitle() = text("MiniMessage Suggest Command", "MiniMessage 제안 명령")
    fun clickSuggestDialogMessage(payload: String) = text(
        "Insert suggest_command at current caret?\n$payload",
        "현재 커서 위치에 suggest_command를 삽입할까요?\n$payload"
    )
    fun clickSuggestDialogConfirm() = text("Insert", "삽입")
    fun clickSuggestDialogCancel() = text("Cancel", "취소")
    fun clickSuggestInsertionCanceled() = text(
        "suggest_command insertion canceled",
        "suggest_command 삽입을 취소했습니다"
    )
    fun clickEditorDisposed() = text("Editor is disposed", "에디터가 종료되었습니다")
    fun clickSuggestedInserted() = text("Suggested command inserted at caret", "제안 명령을 커서 위치에 삽입했습니다")
    fun clickSuggestInsertFailed(message: String) = text(
        "Failed to insert command: $message",
        "명령 삽입에 실패했습니다: $message"
    )
    fun clickNotificationTitle() = text("MiniMessage ClickEvent", "MiniMessage ClickEvent")
    fun clickUnknownType() = text("unknown", "알 수 없음")
    fun clickEmptyPayload() = text("(empty)", "(비어 있음)")

    fun hoverTypeLabel(action: String, details: String): String {
        return if (language() == UiLanguage.KOREAN) {
            "<html><b>타입: ${escapeHtml(action)}</b><br/>$details</html>"
        } else {
            "<html><b>TYPE: ${escapeHtml(action)}</b><br/>$details</html>"
        }
    }

    fun hoverTextEmpty() = text("(empty)", "(비어 있음)")
    fun hoverNoPayload() = text("(no payload)", "(payload 없음)")
    fun hoverItemDetails(payload: String) = text(
        "ITEM: $payload",
        "아이템: $payload"
    )
    fun hoverEntityDetails(payload: String) = text(
        "ENTITY: $payload",
        "엔티티: $payload"
    )

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
}
