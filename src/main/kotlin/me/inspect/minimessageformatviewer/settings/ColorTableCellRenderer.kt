package me.inspect.minimessageformatviewer.settings

import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class ColorTableCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (column == 2 && value is Color) {
            component.background = value
            text = ""
        } else if (component is JLabel) {
            component.background = if (isSelected) table?.selectionBackground else table?.background
        }

        return component
    }
}
