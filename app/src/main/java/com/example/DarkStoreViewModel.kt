package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class DarkStoreViewModel(private val repository: DarkStoreRepository) : ViewModel() {

    // Filter states
    private val _selectedCategoryFilter = MutableStateFlow<String?>("All") // "All", "Fashion", "Beauty & Care", "Grocery"
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyLowStock = MutableStateFlow(false)
    val showOnlyLowStock: StateFlow<Boolean> = _showOnlyLowStock.asStateFlow()

    // Interactive picker name for active order fulfillment
    private val _activePickerName = MutableStateFlow("Picker-01")
    val activePickerName: StateFlow<String> = _activePickerName.asStateFlow()

    // Simulation states
    private val _isSimulationActive = MutableStateFlow(true)
    val isSimulationActive: StateFlow<Boolean> = _isSimulationActive.asStateFlow()

    private val _simulationLog = MutableStateFlow<List<String>>(
        listOf("Dark Store system initialized.", "Incoming orders connection ready.")
    )
    val simulationLog: StateFlow<List<String>> = _simulationLog.asStateFlow()

    // Active Picking Order state (for fulfillment screen modal or detail)
    private val _activePickingOrder = MutableStateFlow<WarehouseOrder?>(null)
    val activePickingOrder: StateFlow<WarehouseOrder?> = _activePickingOrder.asStateFlow()

    // Core flows derived from Room repository
    val products: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repository.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<WarehouseOrder>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var simulationJob: Job? = null

    init {
        // Pre-populate Database if empty, which triggers the Flow automatically
        viewModelScope.launch {
            products.first() // wait for first emit
            delay(50) // small grace delay
            if (products.value.isEmpty()) {
                prePopulateData()
            }
        }
        startSimulation()
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setOnlyLowStock(enabled: Boolean) {
        _showOnlyLowStock.value = enabled
    }

    fun setPickerName(name: String) {
        if (name.isNotBlank()) {
            _activePickerName.value = name
        }
    }

    fun setActivePickingOrder(order: WarehouseOrder?) {
        _activePickingOrder.value = order
    }

    fun toggleSimulation() {
        _isSimulationActive.value = !_isSimulationActive.value
        if (_isSimulationActive.value) {
            startSimulation()
            addSimulationLog("Live Simulation: CONNECTED")
        } else {
            simulationJob?.cancel()
            addSimulationLog("Live Simulation: PAUSED")
        }
    }

    private fun addSimulationLog(message: String) {
        val current = _simulationLog.value.toMutableList()
        current.add(0, "[${System.currentTimeMillis().timeString()}] $message")
        if (current.size > 20) {
            _simulationLog.value = current.take(20)
        } else {
            _simulationLog.value = current
        }
    }

    private fun Long.timeString(): String {
        val date = java.util.Date(this)
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(date)
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                delay(8000) // Execute simulation event every 8 seconds
                if (!_isSimulationActive.value) continue

                // Simulate stock sales & order arrival random event (80% chance for incoming, 20% restock)
                if (Random.nextDouble() < 0.8) {
                    val allItemsList = products.value
                    if (allItemsList.isNotEmpty()) {
                        val randomProduct = allItemsList[Random.nextInt(allItemsList.size)]
                        val orderQty = Random.nextInt(1, 3)

                        if (randomProduct.stockLevel >= orderQty) {
                            // Update Product in DB (decrease stock)
                            val updatedProduct = randomProduct.copy(
                                stockLevel = randomProduct.stockLevel - orderQty,
                                lastUpdated = System.currentTimeMillis()
                            )
                            repository.updateProduct(updatedProduct)

                            // Create an Order
                            val buyerName = getRandomCustomerName()
                            val orderNumber = "ORD-${Random.nextInt(1000, 9999)}"
                            val newOrder = WarehouseOrder(
                                orderNumber = orderNumber,
                                customerName = buyerName,
                                status = "Pending",
                                itemsDescription = "${orderQty}x ${randomProduct.name}",
                                category = randomProduct.category,
                                totalAmount = randomProduct.price * orderQty
                            )
                            repository.insertOrder(newOrder)

                            addSimulationLog("New Order: $orderNumber for ${buyerName} (${orderQty}x ${randomProduct.name})")
                            if (updatedProduct.stockLevel <= updatedProduct.minRequiredStock) {
                                addSimulationLog("LOW STOCK WARNING: ${updatedProduct.name} fell to ${updatedProduct.stockLevel} units!")
                            }
                        } else {
                            // Auto trigger a simulated replenishment request if 0 stock
                            val restockedVal = randomProduct.copy(
                                stockLevel = randomProduct.stockLevel + Random.nextInt(15, 30),
                                lastUpdated = System.currentTimeMillis()
                            )
                            repository.updateProduct(restockedVal)
                            addSimulationLog("Automated Supplier replenishment: +${restockedVal.stockLevel - randomProduct.stockLevel} of ${randomProduct.name}")
                        }
                    }
                } else {
                    // Random Supplier Restock event to keep simulation active
                    val allItemsList = products.value
                    if (allItemsList.isNotEmpty()) {
                        val criticalItem = allItemsList.firstOrNull { it.stockLevel <= it.minRequiredStock } 
                            ?: allItemsList[Random.nextInt(allItemsList.size)]
                        
                        val restockQty = Random.nextInt(10, 25)
                        val restockedProduct = criticalItem.copy(
                            stockLevel = criticalItem.stockLevel + restockQty,
                            lastUpdated = System.currentTimeMillis()
                        )
                        repository.updateProduct(restockedProduct)
                        addSimulationLog("Supplier Restocked: +$restockQty units of ${restockedProduct.name}")
                    }
                }
            }
        }
    }

    // Manual CRUD Actions
    fun adjustStock(product: Product, delta: Int) {
        viewModelScope.launch {
            val newLevel = (product.stockLevel + delta).coerceAtLeast(0)
            val updated = product.copy(
                stockLevel = newLevel,
                lastUpdated = System.currentTimeMillis()
            )
            repository.updateProduct(updated)
            addSimulationLog("Manual Stock adjustments: ${product.name} set to $newLevel ($delta)")
        }
    }

    fun createProduct(name: String, category: String, sku: String, stock: Int, minStock: Int, price: Double, bin: String) {
        viewModelScope.launch {
            val newProduct = Product(
                name = name,
                category = category,
                sku = sku,
                stockLevel = stock,
                minRequiredStock = minStock,
                price = price,
                binLocation = bin
            )
            repository.insertProduct(newProduct)
            addSimulationLog("Product Created: $name [SKU: $sku]")
        }
    }

    fun modifyProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
            addSimulationLog("Product Edited: ${product.name}")
        }
    }

    fun removeProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            addSimulationLog("Product Deleted: ${product.name}")
        }
    }

    fun createOrder(orderNumber: String, buyer: String, items: String, category: String, total: Double) {
        viewModelScope.launch {
            val order = WarehouseOrder(
                orderNumber = orderNumber,
                customerName = buyer,
                status = "Pending",
                itemsDescription = items,
                category = category,
                totalAmount = total
            )
            repository.insertOrder(order)
            addSimulationLog("Manual Order Placed: $orderNumber")
        }
    }

    // Update order state along picking lifecycle
    fun advanceOrderState(order: WarehouseOrder) {
        viewModelScope.launch {
            val nextStatus = when (order.status) {
                "Pending" -> "Picking"
                "Picking" -> "Packed"
                "Packed" -> "Dispatched"
                else -> "Dispatched"
            }
            
            val updated = order.copy(
                status = nextStatus,
                pickerName = if (nextStatus == "Picking") _activePickerName.value else order.pickerName
            )
            
            repository.updateOrder(updated)
            addSimulationLog("Order ${order.orderNumber} advanced to: $nextStatus")
            
            // Sync current active order picker view if open
            if (_activePickingOrder.value?.id == order.id) {
                _activePickingOrder.value = updated
            }
        }
    }

    fun cancelOrder(order: WarehouseOrder) {
        viewModelScope.launch {
            repository.deleteOrder(order)
            addSimulationLog("Order Deleted/Cancelled: ${order.orderNumber}")
            if (_activePickingOrder.value?.id == order.id) {
                _activePickingOrder.value = null
            }
        }
    }

    private fun getRandomCustomerName(): String {
        val names = listOf(
            "Sophia Rossi", "Lucas Becker", "Harpreet Kaur", "Yuki Tanaka", 
            "Aisha Ibrahim", "Gabriel Fonseca", "Amir Mansour", "Chloe Dupont",
            "Mateo Wilson", "Emma Smith", "Zoe Clark", "David Miller"
        )
        return names[Random.nextInt(names.size)]
    }

    private suspend fun prePopulateData() {
        val initialProducts = listOf(
            // Fashion
            Product(name = "Luxe Cropped Denim Jacket", category = "Fashion", sku = "FASH-001", stockLevel = 14, minRequiredStock = 5, price = 89.99, binLocation = "F-04"),
            Product(name = "Signature stretch Slim-fit Jeans", category = "Fashion", sku = "FASH-002", stockLevel = 4, minRequiredStock = 8, price = 59.99, binLocation = "F-08"),
            Product(name = "Premium Cotton Crewneck Tee", category = "Fashion", sku = "FASH-003", stockLevel = 38, minRequiredStock = 12, price = 24.99, binLocation = "F-01"),
            Product(name = "Heritage fleece Jogger Pants", category = "Fashion", sku = "FASH-004", stockLevel = 18, minRequiredStock = 6, price = 44.99, binLocation = "F-02"),
            Product(name = "Active Waterproof Windbreaker", category = "Fashion", sku = "FASH-005", stockLevel = 3, minRequiredStock = 5, price = 79.99, binLocation = "F-11"),
            
            // Beauty & Care
            Product(name = "Rose Hydrating Glow Serum", category = "Beauty & Care", sku = "BEAU-001", stockLevel = 22, minRequiredStock = 8, price = 32.00, binLocation = "B-01"),
            Product(name = "Activated Charcoal Facial Scrub", category = "Beauty & Care", sku = "BEAU-002", stockLevel = 5, minRequiredStock = 10, price = 18.50, binLocation = "B-03"),
            Product(name = "Whipped Shea Body Butter", category = "Beauty & Care", sku = "BEAU-003", stockLevel = 19, minRequiredStock = 6, price = 22.00, binLocation = "B-07"),
            Product(name = "Matte Absolute Velvet Red Lipstick", category = "Beauty & Care", sku = "BEAU-004", stockLevel = 2, minRequiredStock = 5, price = 15.00, binLocation = "B-02"),
            Product(name = "Organics Tea Tree Purifying Shampoo", category = "Beauty & Care", sku = "BEAU-005", stockLevel = 15, minRequiredStock = 10, price = 19.99, binLocation = "B-12"),

            // Grocery
            Product(name = "Organic Whole Cow Milk (1 Gallon)", category = "Grocery", sku = "GROC-001", stockLevel = 42, minRequiredStock = 20, price = 4.49, binLocation = "G-01"),
            Product(name = "Artisanal Sourdough Bread", category = "Grocery", sku = "GROC-002", stockLevel = 6, minRequiredStock = 15, price = 3.99, binLocation = "G-03"),
            Product(name = "Premium Roasted Arabica Coffee Bag", category = "Grocery", sku = "GROC-003", stockLevel = 28, minRequiredStock = 10, price = 14.99, binLocation = "G-09"),
            Product(name = "Organic Hass Avocados (Pack of 4)", category = "Grocery", sku = "GROC-004", stockLevel = 9, minRequiredStock = 15, price = 5.95, binLocation = "G-05"),
            Product(name = "Cold-Pressed Extra Virgin Olive Oil", category = "Grocery", sku = "GROC-005", stockLevel = 17, minRequiredStock = 8, price = 12.49, binLocation = "G-07")
        )

        val initialOrders = listOf(
            WarehouseOrder(orderNumber = "ORD-4831", customerName = "Sophia Rossi", status = "Pending", itemsDescription = "1x Luxe Cropped Denim Jacket, 1x Rose Hydrating Glow Serum", category = "Fashion", totalAmount = 121.99),
            WarehouseOrder(orderNumber = "ORD-4832", customerName = "Lucas Becker", status = "Picking", itemsDescription = "2x Organic Whole Cow Milk (1 Gallon), 1x Artisanal Sourdough Bread", category = "Grocery", totalAmount = 12.97, pickerName = "Picker-01"),
            WarehouseOrder(orderNumber = "ORD-4833", customerName = "Emma Smith", status = "Packed", itemsDescription = "1x Matte Absolute Velvet Red Lipstick, 1x Premium Cotton Crewneck Tee", category = "Beauty & Care", totalAmount = 39.99, pickerName = "Picker-02"),
            WarehouseOrder(orderNumber = "ORD-4834", customerName = "Gabriel Fonseca", status = "Dispatched", itemsDescription = "2x Premium Roasted Arabica Coffee Bag", category = "Grocery", totalAmount = 29.98, pickerName = "Picker-01")
        )

        repository.insertProducts(initialProducts)
        repository.insertOrders(initialOrders)
    }
}

class DarkStoreViewModelFactory(private val repository: DarkStoreRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DarkStoreViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DarkStoreViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
