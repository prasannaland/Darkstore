package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DarkStoreDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Int): Product?

    @Query("SELECT * FROM products WHERE stockLevel <= minRequiredStock")
    fun getLowStockProducts(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM warehouse_orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<WarehouseOrder>>

    @Query("SELECT * FROM warehouse_orders WHERE id = :id")
    suspend fun getOrderById(id: Int): WarehouseOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: WarehouseOrder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<WarehouseOrder>)

    @Update
    suspend fun updateOrder(order: WarehouseOrder)

    @Delete
    suspend fun deleteOrder(order: WarehouseOrder)
}
