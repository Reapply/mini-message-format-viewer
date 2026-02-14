package me.inspect.minimessageformatviewer.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import me.inspect.minimessageformatviewer.i18n.MiniMessageI18n
import me.inspect.minimessageformatviewer.rendering.model.RenderMode
import java.awt.BorderLayout
import javax.swing.*

class MiniMessageSettingsConfigurable : Configurable {

    private var colorTableModel: ColorTableModel? = null
    private var table: JBTable? = null
    private var languageCombo: JComboBox<UiLanguage>? = null
    private var renderModeCombo: JComboBox<RenderMode>? = null
    private var highlightOnlyInViewModeCheckbox: JCheckBox? = null
    private var enableRealClickActionsCheckbox: JCheckBox? = null
    private var enableOpenUrlClickActionCheckbox: JCheckBox? = null
    private var enableCopyClickActionCheckbox: JCheckBox? = null
    private var enableSuggestClickActionCheckbox: JCheckBox? = null

    override fun getDisplayName() = MiniMessageI18n.settingsDisplayName()

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        val topPanel = JPanel(BorderLayout(0, 8))
        val optionsPanel = JPanel()
        optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
        val modePanel = JPanel(BorderLayout(8, 0))

        val infoLabel = JBLabel(MiniMessageI18n.settingsInfoLabel())
        infoLabel.border = BorderFactory.createEmptyBorder(0, 0, 6, 0)

        val settings = MiniMessageSettings.getInstance()
        languageCombo = JComboBox(UiLanguage.entries.toTypedArray()).apply {
            selectedItem = UiLanguage.fromRaw(settings.uiLanguage)
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    text = (value as? UiLanguage)?.displayName ?: value?.toString().orEmpty()
                    return this
                }
            }
        }
        renderModeCombo = JComboBox(RenderMode.entries.toTypedArray()).apply {
            selectedItem = RenderMode.fromRaw(settings.renderMode)
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    text = (value as? RenderMode)?.displayName ?: value?.toString().orEmpty()
                    return this
                }
            }
        }

        val languagePanel = JPanel(BorderLayout(8, 0))
        languagePanel.add(JBLabel(MiniMessageI18n.settingsLanguageLabel()), BorderLayout.WEST)
        languagePanel.add(languageCombo, BorderLayout.CENTER)

        modePanel.add(JBLabel(MiniMessageI18n.settingsRenderingModelLabel()), BorderLayout.WEST)
        modePanel.add(renderModeCombo, BorderLayout.CENTER)

        highlightOnlyInViewModeCheckbox = JCheckBox(MiniMessageI18n.settingsHighlightOnlyViewMode()).apply {
            isSelected = settings.highlightOnlyInViewMode
            alignmentX = 0f
        }

        enableRealClickActionsCheckbox = JCheckBox(MiniMessageI18n.settingsEnableRealClickActions()).apply {
            isSelected = settings.enableRealClickActions
            alignmentX = 0f
        }

        enableOpenUrlClickActionCheckbox = JCheckBox(MiniMessageI18n.settingsAllowOpenUrlAction()).apply {
            isSelected = settings.enableOpenUrlClickAction
            alignmentX = 0f
        }

        enableCopyClickActionCheckbox = JCheckBox(MiniMessageI18n.settingsAllowCopyAction()).apply {
            isSelected = settings.enableCopyToClipboardClickAction
            alignmentX = 0f
        }

        enableSuggestClickActionCheckbox = JCheckBox(MiniMessageI18n.settingsAllowSuggestAction()).apply {
            isSelected = settings.enableSuggestCommandClickAction
            alignmentX = 0f
        }

        val clickActionToggleListener = {
            val enabled = enableRealClickActionsCheckbox?.isSelected == true
            enableOpenUrlClickActionCheckbox?.isEnabled = enabled
            enableCopyClickActionCheckbox?.isEnabled = enabled
            enableSuggestClickActionCheckbox?.isEnabled = enabled
        }
        enableRealClickActionsCheckbox?.addActionListener { clickActionToggleListener() }
        clickActionToggleListener()

        optionsPanel.add(languagePanel)
        optionsPanel.add(Box.createVerticalStrut(6))
        optionsPanel.add(modePanel)
        optionsPanel.add(Box.createVerticalStrut(6))
        optionsPanel.add(highlightOnlyInViewModeCheckbox)
        optionsPanel.add(Box.createVerticalStrut(6))
        optionsPanel.add(enableRealClickActionsCheckbox)
        optionsPanel.add(enableOpenUrlClickActionCheckbox)
        optionsPanel.add(enableCopyClickActionCheckbox)
        optionsPanel.add(enableSuggestClickActionCheckbox)

        topPanel.add(infoLabel, BorderLayout.NORTH)
        topPanel.add(optionsPanel, BorderLayout.SOUTH)
        topPanel.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)

        panel.add(topPanel, BorderLayout.NORTH)

        colorTableModel = ColorTableModel(settings.customColors)

        table = JBTable(colorTableModel).apply {
            setDefaultRenderer(Any::class.java, ColorTableCellRenderer())
            rowHeight = 25
        }

        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        panel.add(createButtonPanel(), BorderLayout.SOUTH)

        return panel
    }

    override fun isModified(): Boolean {
        val settings = MiniMessageSettings.getInstance()
        val colorsChanged = colorTableModel?.customColors != settings.customColors
        val languageChanged = (languageCombo?.selectedItem as? UiLanguage)?.name != settings.uiLanguage
        val modeChanged = (renderModeCombo?.selectedItem as? RenderMode)?.name != settings.renderMode
        val viewModeOnlyChanged = highlightOnlyInViewModeCheckbox?.isSelected != settings.highlightOnlyInViewMode
        val clickMasterChanged = enableRealClickActionsCheckbox?.isSelected != settings.enableRealClickActions
        val clickOpenUrlChanged = enableOpenUrlClickActionCheckbox?.isSelected != settings.enableOpenUrlClickAction
        val clickCopyChanged = enableCopyClickActionCheckbox?.isSelected != settings.enableCopyToClipboardClickAction
        val clickSuggestChanged = enableSuggestClickActionCheckbox?.isSelected != settings.enableSuggestCommandClickAction
        return colorsChanged ||
                languageChanged ||
                modeChanged ||
                viewModeOnlyChanged ||
                clickMasterChanged ||
                clickOpenUrlChanged ||
                clickCopyChanged ||
                clickSuggestChanged
    }

    override fun apply() {
        val settings = MiniMessageSettings.getInstance()
        colorTableModel?.let {
            settings.customColors.clear()
            settings.customColors.putAll(it.customColors)
        }
        settings.uiLanguage = (languageCombo?.selectedItem as? UiLanguage)?.name ?: UiLanguage.ENGLISH.name
        settings.renderMode = (renderModeCombo?.selectedItem as? RenderMode)?.name ?: RenderMode.ADVENTURE_API.name
        settings.highlightOnlyInViewMode = highlightOnlyInViewModeCheckbox?.isSelected ?: true
        settings.enableRealClickActions = enableRealClickActionsCheckbox?.isSelected ?: false
        settings.enableOpenUrlClickAction = enableOpenUrlClickActionCheckbox?.isSelected ?: true
        settings.enableCopyToClipboardClickAction = enableCopyClickActionCheckbox?.isSelected ?: true
        settings.enableSuggestCommandClickAction = enableSuggestClickActionCheckbox?.isSelected ?: true
        restartHighlighting()
    }

    override fun reset() {
        colorTableModel?.let { model ->
            val settings = MiniMessageSettings.getInstance()
            model.customColors.clear()
            model.customColors.putAll(settings.customColors)
            model.rebuild()
        }
        val settings = MiniMessageSettings.getInstance()
        languageCombo?.selectedItem = UiLanguage.fromRaw(settings.uiLanguage)
        renderModeCombo?.selectedItem = RenderMode.fromRaw(settings.renderMode)
        highlightOnlyInViewModeCheckbox?.isSelected = settings.highlightOnlyInViewMode
        enableRealClickActionsCheckbox?.isSelected = settings.enableRealClickActions
        enableOpenUrlClickActionCheckbox?.isSelected = settings.enableOpenUrlClickAction
        enableCopyClickActionCheckbox?.isSelected = settings.enableCopyToClipboardClickAction
        enableSuggestClickActionCheckbox?.isSelected = settings.enableSuggestCommandClickAction
        val enabled = settings.enableRealClickActions
        enableOpenUrlClickActionCheckbox?.isEnabled = enabled
        enableCopyClickActionCheckbox?.isEnabled = enabled
        enableSuggestClickActionCheckbox?.isEnabled = enabled
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JPanel()

        buttonPanel.add(JButton(MiniMessageI18n.settingsAddCustomColor()).apply {
            addActionListener { colorTableModel?.addCustomColor() }
        })

        buttonPanel.add(JButton(MiniMessageI18n.settingsRemoveSelected()).apply {
            addActionListener {
                table?.selectedRow?.let { row -> colorTableModel?.removeColor(row) }
            }
        })

        buttonPanel.add(JButton(MiniMessageI18n.settingsResetDefaults()).apply {
            addActionListener { colorTableModel?.resetDefaults() }
        })

        return buttonPanel
    }

    private fun restartHighlighting() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}
