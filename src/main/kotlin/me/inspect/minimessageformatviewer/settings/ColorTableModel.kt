package me.inspect.minimessageformatviewer.settings

import com.intellij.ui.ColorUtil
import me.inspect.minimessageformatviewer.i18n.MiniMessageI18n
import java.awt.Color
import javax.swing.table.AbstractTableModel

class ColorTableModel(val customColors: MutableMap<String, String>) : AbstractTableModel() {

    private val defaultColors = mapOf(
        "black" to "000000", "dark_blue" to "0000AA", "dark_green" to "00AA00",
        "dark_aqua" to "00AAAA", "dark_red" to "AA0000", "dark_purple" to "AA00AA",
        "gold" to "FFAA00", "gray" to "AAAAAA", "dark_gray" to "555555",
        "blue" to "5555FF", "green" to "55FF55", "aqua" to "55FFFF",
        "red" to "FF5555", "light_purple" to "FF55FF", "yellow" to "FFFF55",
        "white" to "FFFFFF", "grey" to "AAAAAA", "dark_grey" to "555555"
    )

    private val colorEntries = mutableListOf<ColorEntry>()

    init {
        rebuildEntries()
    }

    override fun getRowCount() = colorEntries.size
    override fun getColumnCount() = 4

    override fun getColumnName(column: Int) = when (column) {
        0 -> MiniMessageI18n.tableColumnColorName()
        1 -> MiniMessageI18n.tableColumnHex()
        2 -> MiniMessageI18n.tableColumnPreview()
        3 -> MiniMessageI18n.tableColumnType()
        else -> ""
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val entry = colorEntries[rowIndex]
        return when (columnIndex) {
            0 -> entry.name
            1 -> entry.hex
            2 -> hexToColor(entry.hex)
            3 -> if (entry.isDefault) MiniMessageI18n.tableTypeDefault() else MiniMessageI18n.tableTypeCustom()
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        val entry = colorEntries[rowIndex]
        return when (columnIndex) {
            0 -> !entry.isDefault
            1 -> true
            else -> false
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (aValue !is String) return

        val entry = colorEntries[rowIndex]

        when (columnIndex) {
            0 -> updateColorName(entry, aValue)
            1 -> updateColorHex(entry, aValue, rowIndex)
        }
    }

    fun addCustomColor() {
        var counter = 1
        var newName = "mycolor"
        while (customColors.containsKey(newName) || defaultColors.containsKey(newName)) {
            newName = "mycolor$counter"
            counter++
        }
        customColors[newName] = "FFFFFF"
        rebuildEntries()
        fireTableDataChanged()
    }

    fun removeColor(rowIndex: Int) {
        if (rowIndex in colorEntries.indices) {
            customColors.remove(colorEntries[rowIndex].name)
            rebuildEntries()
            fireTableDataChanged()
        }
    }

    fun resetDefaults() {
        defaultColors.keys.forEach { customColors.remove(it) }
        rebuildEntries()
        fireTableDataChanged()
    }

    fun rebuild() {
        rebuildEntries()
        fireTableDataChanged()
    }

    private fun rebuildEntries() {
        colorEntries.clear()

        defaultColors.keys.sorted().forEach { name ->
            val customHex = customColors[name]
            colorEntries.add(ColorEntry(name, customHex ?: defaultColors[name]!!, isDefault = true))
        }

        customColors.keys.filter { it !in defaultColors }.sorted().forEach { name ->
            colorEntries.add(ColorEntry(name, customColors[name]!!, isDefault = false))
        }
    }

    private fun updateColorName(entry: ColorEntry, newName: String) {
        if (entry.isDefault) return

        val cleanName = newName.trim().lowercase()
        if (cleanName.matches(Regex("[a-z_][a-z0-9_]*"))) {
            customColors.remove(entry.name)
            customColors[cleanName] = entry.hex
            rebuildEntries()
            fireTableDataChanged()
        }
    }

    private fun updateColorHex(entry: ColorEntry, newHex: String, rowIndex: Int) {
        val cleanValue = newHex.trim().removePrefix("#").uppercase()
        if (!cleanValue.matches(Regex("[0-9A-F]{6}"))) return

        if (entry.isDefault) {
            if (cleanValue != defaultColors[entry.name]) {
                customColors[entry.name] = cleanValue
            } else {
                customColors.remove(entry.name)
            }
        } else {
            customColors[entry.name] = cleanValue
        }

        entry.hex = cleanValue
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    private fun hexToColor(hex: String) = runCatching {
        ColorUtil.fromHex(hex)
    }.getOrDefault(Color.WHITE)
}
