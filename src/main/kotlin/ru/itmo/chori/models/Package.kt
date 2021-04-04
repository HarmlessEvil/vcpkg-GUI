package ru.itmo.chori.models

import javax.swing.table.AbstractTableModel

data class Package(val name: String, val version: String, val shortDescription: String)

class PackagesTableModel(private var packages: List<Package>) : AbstractTableModel() {
    private val columnNames = listOf("Name", "Version", "Description")

    override fun getRowCount(): Int = packages.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): String = when(columnIndex) {
        0 -> packages[rowIndex].name
        1 -> packages[rowIndex].version
        2 -> packages[rowIndex].shortDescription
        else -> throw IndexOutOfBoundsException()
    }

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return String::class.java
    }

    fun setPackages(packages: List<Package>) {
        this.packages = packages
        fireTableDataChanged()
    }
}