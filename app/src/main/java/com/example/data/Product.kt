package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // "Fashion", "Beauty & Care", "Grocery"
    val sku: String,
    val stockLevel: Int,
    val minRequiredStock: Int,
    val price: Double,
    val binLocation: String, // e.g. "Aisle 3, Shelf B"
    val lastUpdated: Long = System.currentTimeMillis()
)
