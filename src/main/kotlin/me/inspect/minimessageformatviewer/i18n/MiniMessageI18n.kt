package me.inspect.minimessageformatviewer.i18n

object MiniMessageI18n {
    fun settingsDisplayName() = MiniMessageBundle.message("settings.display.name")
    fun settingsInfoLabel() = MiniMessageBundle.message("settings.info.label")
    fun settingsRenderingModelLabel() = MiniMessageBundle.message("settings.rendering.model.label")
    fun settingsHighlightOnlyViewMode() = MiniMessageBundle.message("settings.highlight.only.view.mode")
    fun settingsEnableRealClickActions() = MiniMessageBundle.message("settings.click.enable.real.actions")
    fun settingsAllowOpenUrlAction() = MiniMessageBundle.message("settings.click.allow.open_url")
    fun settingsAllowCopyAction() = MiniMessageBundle.message("settings.click.allow.copy_to_clipboard")
    fun settingsAllowSuggestAction() = MiniMessageBundle.message("settings.click.allow.suggest_command")
    fun settingsAddCustomColor() = MiniMessageBundle.message("settings.buttons.add.custom.color")
    fun settingsRemoveSelected() = MiniMessageBundle.message("settings.buttons.remove.selected")
    fun settingsResetDefaults() = MiniMessageBundle.message("settings.buttons.reset.defaults")

    fun tableColumnColorName() = MiniMessageBundle.message("table.column.color.name")
    fun tableColumnHex() = MiniMessageBundle.message("table.column.hex")
    fun tableColumnPreview() = MiniMessageBundle.message("table.column.preview")
    fun tableColumnType() = MiniMessageBundle.message("table.column.type")
    fun tableTypeDefault() = MiniMessageBundle.message("table.type.default")
    fun tableTypeCustom() = MiniMessageBundle.message("table.type.custom")

    fun intentionToggleViewEdit() = MiniMessageBundle.message("intention.toggle.view.edit")
    fun hintViewMode(enabled: Boolean) = if (enabled) {
        MiniMessageBundle.message("hint.view.mode.enabled")
    } else {
        MiniMessageBundle.message("hint.view.mode.disabled")
    }

    fun clickTypeLabel(type: String, payload: String) =
        MiniMessageBundle.message("click.type.label", type, payload)

    fun clickPreviewOnly() = MiniMessageBundle.message("click.preview.only")
    fun clickRunCommandUnsupported() = MiniMessageBundle.message("click.run_command.unsupported")
    fun clickUnsupportedAction(action: String) = MiniMessageBundle.message("click.action.unsupported", action)
    fun clickActionDisabled(action: String) = MiniMessageBundle.message("click.action.disabled", action)
    fun clickPayloadEmpty(action: String) = MiniMessageBundle.message("click.payload.empty", action)
    fun clickOpenedUrl() = MiniMessageBundle.message("click.open_url.opened")
    fun clickFailedOpenUrl(message: String) = MiniMessageBundle.message("click.open_url.failed", message)
    fun clickCopiedPayload() = MiniMessageBundle.message("click.copy_to_clipboard.copied")
    fun clickSuggestConfirmationOpened() = MiniMessageBundle.message("click.suggest.confirmation.opened")
    fun clickSuggestDialogTitle() = MiniMessageBundle.message("click.suggest.dialog.title")
    fun clickSuggestDialogMessage(payload: String) = MiniMessageBundle.message("click.suggest.dialog.message", payload)
    fun clickSuggestDialogConfirm() = MiniMessageBundle.message("click.suggest.dialog.confirm")
    fun clickSuggestDialogCancel() = MiniMessageBundle.message("click.suggest.dialog.cancel")
    fun clickSuggestInsertionCanceled() = MiniMessageBundle.message("click.suggest.insertion.canceled")
    fun clickEditorDisposed() = MiniMessageBundle.message("click.editor.disposed")
    fun clickSuggestedInserted() = MiniMessageBundle.message("click.suggest.inserted")
    fun clickSuggestInsertFailed(message: String) = MiniMessageBundle.message("click.suggest.insert.failed", message)
    fun clickNotificationTitle() = MiniMessageBundle.message("click.notification.title")
    fun clickUnknownType() = MiniMessageBundle.message("click.type.unknown")
    fun clickEmptyPayload() = MiniMessageBundle.message("click.payload.empty.value")

    fun hoverTypeLabel(action: String, details: String): String {
        return MiniMessageBundle.message("hover.tooltip.template", action, details)
    }

    fun hoverTextEmpty() = MiniMessageBundle.message("hover.text.empty")
    fun hoverNoPayload() = MiniMessageBundle.message("hover.payload.none")
    fun hoverItemDetails(payload: String) = MiniMessageBundle.message("hover.item.details", payload)
    fun hoverEntityDetails(payload: String) = MiniMessageBundle.message("hover.entity.details", payload)
}
