package ru.itmo.chori.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.swing.table.AbstractTableModel

@Serializable
data class Package(
    @SerialName("package_name")
    val name: String,
    val version: String,
    val desc: Array<String>
) {
    val shortDescription: String
    get() = desc.joinToString("\n")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Package

        if (name != other.name) return false
        if (version != other.version) return false
        if (!desc.contentEquals(other.desc)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + desc.contentHashCode()
        return result
    }
}

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