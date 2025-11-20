package me.inspect.minimessageformatviewer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.*

class MiniMessageSettingsConfigurable : Configurable {

    private var colorTableModel: ColorTableModel? = null
    private var table: JBTable? = null

    override fun getDisplayName() = "MiniMessage Colors"

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())

        val infoLabel = JBLabel("Customize MiniMessage color mappings. Add your own color names or modify existing ones.")
        infoLabel.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        panel.add(infoLabel, BorderLayout.NORTH)

        val settings = MiniMessageSettings.getInstance()
        colorTableModel = ColorTableModel(settings.customColors)

        table = JBTable(colorTableModel).apply {
            setDefaultRenderer(Any::class.java, ColorTableCellRenderer())
            rowHeight = 25
        }

        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        panel.add(createButtonPanel(), BorderLayout.SOUTH)

        return panel
    }

    override fun isModified() =
        colorTableModel?.customColors != MiniMessageSettings.getInstance().customColors

    override fun apply() {
        val settings = MiniMessageSettings.getInstance()
        colorTableModel?.let {
            settings.customColors.clear()
            settings.customColors.putAll(it.customColors)
        }
    }

    override fun reset() {
        colorTableModel?.let { model ->
            val settings = MiniMessageSettings.getInstance()
            model.customColors.clear()
            model.customColors.putAll(settings.customColors)
            model.rebuild()
        }
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JPanel()

        buttonPanel.add(JButton("Add Custom Color").apply {
            addActionListener { colorTableModel?.addCustomColor() }
        })

        buttonPanel.add(JButton("Remove Selected").apply {
            addActionListener {
                table?.selectedRow?.let { row -> colorTableModel?.removeColor(row) }
            }
        })

        buttonPanel.add(JButton("Reset Defaults").apply {
            addActionListener { colorTableModel?.resetDefaults() }
        })

        return buttonPanel
    }
}
