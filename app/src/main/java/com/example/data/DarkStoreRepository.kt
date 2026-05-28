package com.example.data

import kotlinx.coroutines.flow.Flow

class DarkStoreRepository(private val dao: DarkStoreDao) {
    val allProducts: Flow<List<Product>> = dao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = dao.getLowStockProducts()
    val allOrders: Flow<List<WarehouseOrder>> = dao.getAllOrders()

    suspend fun getProductById(id: Int): Product? = dao.getProductById(id)
    suspend fun insertProduct(product: Product) = dao.insertProduct(product)
    suspend fun insertProducts(products: List<Product>) = dao.insertProducts(products)
    suspend fun updateProduct(product: Product) = dao.updateProduct(product)
    suspend fun deleteProduct(product: Product) = dao.deleteProduct(product)

    suspend fun getOrderById(id: Int): WarehouseOrder? = dao.getOrderById(id)
    suspend fun insertOrder(order: WarehouseOrder) = dao.insertOrder(order)
    suspend fun insertOrders(orders: List<WarehouseOrder>) = dao.insertOrders(orders)
    suspend fun updateOrder(order: WarehouseOrder) = dao.updateOrder(order)
    suspend fun deleteOrder(order: WarehouseOrder) = dao.deleteOrder(order)
}
