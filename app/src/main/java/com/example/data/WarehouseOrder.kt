package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "warehouse_orders")
data class WarehouseOrder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val orderNumber: String,
    val customerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "Pending", "Picking", "Packed", "Dispatched"
    val itemsDescription: String, // e.g. "2x Classic Denim, 1x Organics Body Wash"
    val category: String, // dominant category for color coding and sorting
    val totalAmount: Double,
    val pickerName: String? = null
)
