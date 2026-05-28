package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlin.random.Random
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core Room database setup
        val database = DarkStoreDatabase.getDatabase(this)
        val repository = DarkStoreRepository(database.darkStoreDao())

        setContent {
            MyApplicationTheme {
                val viewModel: DarkStoreViewModel = viewModel(
                    factory = DarkStoreViewModelFactory(repository)
                )
                DarkStoreRootScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DarkStoreRootScreen(viewModel: DarkStoreViewModel) {
    var currentTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Inventory, 2 = Orders
    val activePickingOrder by viewModel.activePickingOrder.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (activePickingOrder == null) {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 10.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_dashboard_tab")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Inventory, contentDescription = "Inventory") },
                        label = { Text("Inventory", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_inventory_tab")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { 
                            val orders by viewModel.orders.collectAsStateWithLifecycle()
                            val pendingCount = orders.count { it.status == "Pending" || it.status == "Picking" }
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                            Text(pendingCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AssignmentTurnedIn, contentDescription = "Fulfillment Orders")
                            }
                        },
                        label = { Text("Fulfillment", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_orders_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = activePickingOrder != null,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "HUDTransition"
            ) { isPickingActive ->
                if (isPickingActive) {
                    activePickingOrder?.let { order ->
                        PickingHUDView(
                            order = order,
                            viewModel = viewModel,
                            onClose = { viewModel.setActivePickingOrder(null) }
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title Bar
                        StoreAppBar(viewModel)

                        // Action Ticker summary
                        LowStockMarquee(viewModel)

                        // Main sections switcher
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                0 -> DashboardPane(viewModel)
                                1 -> InventoryPane(viewModel)
                                2 -> OrdersFulfillmentPane(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreAppBar(viewModel: BlackBoxPlaceholder = null, viewModelActual: DarkStoreViewModel) {
    // Overloaded safely
    StoreAppBar(viewModelActual)
}

@Composable
fun StoreAppBar(viewModel: DarkStoreViewModel) {
    val isSimulating by viewModel.isSimulationActive.collectAsStateWithLifecycle()
    val pickerName by viewModel.activePickerName.collectAsStateWithLifecycle()
    var showPickerEdit by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Dark Store",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Warehouse 04 • Sector B",
                fontSize = 13.sp,
                color = TextMuted,
                fontWeight = FontWeight.Normal
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Simulator Status Pin
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSimulating) Color(0xFF1B3320) else Color(0xFF331B1F))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSimulating) NeonBlue else ErrorRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSimulating) "LIVE SIM" else "PAUSED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSimulating) NeonBlue else ErrorRed
                    )
                }
            }

            // Custom Geometric Profile Trigger
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeonMint)
                    .clickable { showPickerEdit = true }
                    .testTag("picker_chip"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Picker configuration profile",
                    tint = Color(0xFF381E72),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showPickerEdit) {
        var tempName by remember { mutableStateOf(pickerName) }
        AlertDialog(
            onDismissRequest = { showPickerEdit = false },
            title = { Text("Configure Active Picker") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Picker Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("picker_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setPickerName(tempName)
                        showPickerEdit = false
                    },
                    modifier = Modifier.testTag("save_picker_button")
                ) {
                    Text("Apply Profile")
                }
            }
        )
    }
}

@Composable
fun LowStockMarquee(viewModel: DarkStoreViewModel) {
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    if (lowStockProducts.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C1C13)) // Warm alerts banner
                .padding(vertical = 6.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Alert",
                tint = WarningOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = "CRITICAL REORDER ALERTS: " + lowStockProducts.joinToString(" | ") { "${it.name} (${it.stockLevel} left, Bin ${it.binLocation})" },
                    fontSize = 11.sp,
                    color = WarningOrange,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

typealias BlackBoxPlaceholder = Nothing?

@Composable
fun DashboardPane(viewModel: DarkStoreViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val isSimulating by viewModel.isSimulationActive.collectAsStateWithLifecycle()
    val logs by viewModel.simulationLog.collectAsStateWithLifecycle()

    val totalStockSum = products.sumOf { it.stockLevel }
    val lowStockCount = products.count { it.stockLevel <= it.minRequiredStock }
    val pendingOrdersCount = orders.count { it.status == "Pending" || it.status == "Picking" }
    val dispatchedCount = orders.count { it.status == "Dispatched" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Stats row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Dark Store Real-Time Metrics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "TOTAL STOCK",
                        value = totalStockSum.toString(),
                        subtitle = " across ${products.size} SKUs",
                        icon = Icons.Default.Inventory,
                        tint = NeonBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "CRITICAL STOCK",
                        value = lowStockCount.toString(),
                        subtitle = "requires replenishing",
                        icon = Icons.Default.PriorityHigh,
                        tint = if (lowStockCount > 0) ErrorRed else NeonMint,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "PENDING PICK",
                        value = pendingOrdersCount.toString(),
                        subtitle = "SLA countdown active",
                        icon = Icons.Default.PendingActions,
                        tint = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "DISPATCHED",
                        value = dispatchedCount.toString(),
                        subtitle = "completed under SLA",
                        icon = Icons.Default.LocalShipping,
                        tint = NeonMint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Geometric Inventory Category Grid
        item {
            GeometricCategorySection(products)
        }

        // Segmented Category Chart
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Warehouse Stock Allocation",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stock volumetric ratio between your three primary store categories",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CategoryBarChart(products)
                }
            }
        }

        // Simulator controls and logs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isSimulating) Color(0xFF1F4C39) else DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automated Live Order simulator",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Simulates online buyers checking out products & real-time inventory decreases",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Switch(
                            checked = isSimulating,
                            onCheckedChange = { viewModel.toggleSimulation() },
                            modifier = Modifier.testTag("simulation_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SYSTEM EVENTS ACTIVITY FEED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131114)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (logs.isEmpty()) {
                                item {
                                    Text(
                                        "No activity logged yet.",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                items(logs) { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("LOW STOCK") || log.contains("WARNING")) ErrorRed 
                                                else if (log.contains("New Order")) NeonBlue 
                                                else if (log.contains("Restock") || log.contains("replenishment")) NeonMint
                                                else DarkOnBg,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeometricCategorySection(products: List<Product>) {
    val groceryCount = products.count { it.category == "Grocery" }
    val beautyCount = products.count { it.category == "Beauty & Care" }
    val fashionCount = products.count { it.category == "Fashion" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Inventory Categories",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = neonMintHighlight,
            letterSpacing = 0.5.sp
        )

        // Top Row: Large Grocery Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4F378B)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Large decoration icon of Shopping Cart
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-10).dp)
                )

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Grocery",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$groceryCount active SKUs",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ACTIVE SINK",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Row of two blocks (Teal Emerald + Rose Burgundy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Beauty (Teal Emerald)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF005049)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(70.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-5).dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Beauty",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$beautyCount SKUs",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Fashion (Rose Burgundy)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7D5260)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(70.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-5).dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Fashion",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$fashionCount SKUs",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// Accent tracking reference helper
private val neonMintHighlight: Color get() = NeonMint

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
                Icon(icon, contentDescription = null, sizeX = 16.dp, tint = tint)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Light, color = tint)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = TextMuted)
        }
    }
}

// Safely draw loaded icon size
@Composable
fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    sizeX: androidx.compose.ui.unit.Dp,
    tint: Color
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(sizeX)
    )
}

@Composable
fun CategoryBarChart(products: List<Product>) {
    val allocationMap = products.groupBy { it.category }
    val fashionStock = allocationMap["Fashion"]?.sumOf { it.stockLevel } ?: 0
    val beautyStock = allocationMap["Beauty & Care"]?.sumOf { it.stockLevel } ?: 0
    val groceryStock = allocationMap["Grocery"]?.sumOf { it.stockLevel } ?: 0

    val total = (fashionStock + beautyStock + groceryStock).toFloat()

    if (total == 0f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xFF151921), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No Stock Available", sizeX = 11.sp, color = TextMuted)
        }
        return
    }

    val fWeight = fashionStock / total
    val bWeight = beautyStock / total
    val gWeight = groceryStock / total

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Multi-color rounded bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.DarkGray)
        ) {
            if (fashionStock > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(fWeight.coerceAtLeast(0.01f))
                        .background(CategoryFashion)
                )
            }
            if (beautyStock > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(bWeight.coerceAtLeast(0.01f))
                        .background(CategoryBeauty)
                )
            }
            if (groceryStock > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(gWeight.coerceAtLeast(0.01f))
                        .background(CategoryGrocery)
                )
            }
        }

        // Legends
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(label = "Fashion", count = fashionStock, color = CategoryFashion)
            LegendItem(label = "Beauty & Care", count = beautyStock, color = CategoryBeauty)
            LegendItem(label = "Grocery", count = groceryStock, color = CategoryGrocery)
        }
    }
}

@Composable
fun Text(text: String, sizeX: androidx.compose.ui.unit.TextUnit, color: Color) {
    Text(text = text, fontSize = sizeX, color = color)
}

@Composable
fun LegendItem(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ($count units)",
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

// ======================= INVENTORY SECTION ==========================

@Composable
fun InventoryPane(viewModel: DarkStoreViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showOnlyLowStock by viewModel.showOnlyLowStock.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editProductTarget by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = products.filter { product ->
        val categoryMatch = selectedCategory == "All" || product.category == selectedCategory
        val searchMatch = product.name.contains(searchQuery, ignoreCase = true) || 
                          product.sku.contains(searchQuery, ignoreCase = true) ||
                          product.binLocation.contains(searchQuery, ignoreCase = true)
        val stockMatch = !showOnlyLowStock || (product.stockLevel <= product.minRequiredStock)

        categoryMatch && searchMatch && stockMatch
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Quick Tabs + Search block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by name, SKU, or Bin location...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextMuted)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonMint,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_search_field")
                )

                // Category selector buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("All", "Fashion", "Beauty & Care", "Grocery")
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCategoryFilter(cat) },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when(cat) {
                                    "Fashion" -> CategoryFashion.copy(alpha = 0.3f)
                                    "Beauty & Care" -> CategoryBeauty.copy(alpha = 0.3f)
                                    "Grocery" -> CategoryGrocery.copy(alpha = 0.3f)
                                    else -> NeonMint.copy(alpha = 0.3f)
                                },
                                selectedLabelColor = Color.White,
                                labelColor = TextMuted
                            ),
                            modifier = Modifier.testTag("filter_chip_${cat.replace(" & ", "_")}")
                        )
                    }
                }

                // Low stock checkbox toggle Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = showOnlyLowStock,
                            onCheckedChange = { viewModel.setOnlyLowStock(it) },
                            modifier = Modifier.testTag("low_stock_checkbox_toggle")
                        )
                        Text(
                            text = "Filter Low Stock Alerts Only",
                            fontSize = 12.sp,
                            color = if (showOnlyLowStock) WarningOrange else Color.White,
                            fontWeight = if (showOnlyLowStock) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Text(
                        text = "Found ${filteredProducts.size} items",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Products grid
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = TextMuted
                        )
                        Text(
                            text = "No SKUs match criteria.",
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Try clearing search filter parameters or add a custom SKU.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProducts) { product ->
                        ProductItemCard(
                            product = product,
                            onAdjust = { delta -> viewModel.adjustStock(product, delta) },
                            onEdit = { editProductTarget = product },
                            onDelete = { viewModel.removeProduct(product) }
                        )
                    }
                }
            }
        }

        // Add Product Floating Action Button
        LargeFloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = NeonMint,
            contentColor = Color.Black,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_product_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product", modifier = Modifier.size(28.dp))
        }
    }

    // Add dialog
    if (showAddDialog) {
        AddSkuDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { name, cat, sku, stock, minStock, price, bin ->
                viewModel.createProduct(name, cat, sku, stock, minStock, price, bin)
                showAddDialog = false
            }
        )
    }

    // Edit dialog
    editProductTarget?.let { target ->
        EditSkuDialog(
            product = target,
            onDismiss = { editProductTarget = null },
            onSubmit = { modified ->
                viewModel.modifyProduct(modified)
                editProductTarget = null
            }
        )
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    onAdjust: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCritical = product.stockLevel <= product.minRequiredStock
    val catColor = when (product.category) {
        "Fashion" -> CategoryFashion
        "Beauty & Care" -> CategoryBeauty
        "Grocery" -> CategoryGrocery
        else -> NeonMint
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (isCritical) ErrorRed.copy(alpha = 0.8f) else DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.sku}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Category label & Sorter badge + Bin locator and Edit trigger buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(catColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = product.category,
                            fontSize = 9.sp,
                            color = catColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bin location locator
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF232835))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(9.dp),
                                tint = NeonBlue
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Bin ${product.binLocation}",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Edit utilities row
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("edit_product_${product.sku}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit product info", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("delete_product_${product.sku}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete from system", tint = ErrorRed.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Product properties
            Column {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SKU: ${product.sku}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", product.price)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stock bar indicator level (StockLevel vs minRequiredStock fraction)
            val maxProgressLimit = (product.minRequiredStock * 2.5).toInt().coerceAtLeast(10)
            val pctFilled = (product.stockLevel.toFloat() / maxProgressLimit.toFloat()).coerceIn(0f, 1f)
            val trackColor = if (isCritical) ErrorRed else NeonMint

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Warehouse Stock Level",
                        fontSize = 10.sp,
                        color = TextMuted
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${product.stockLevel}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCritical) ErrorRed else Color.White
                        )
                        Text(
                            text = " / req min ${product.minRequiredStock}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                // Interactive Progress Bar on Card
                LinearProgressIndicator(
                    progress = { pctFilled },
                    color = trackColor,
                    trackColor = Color(0xFF222835),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )

                if (isCritical) {
                    Text(
                        text = if (product.stockLevel == 0) "OUT OF STOCK - RESTOCK NEEDED IMMEDIATELY" else "LOW STOCK CAUTION - UNDER MIN LIMIT",
                        fontSize = 8.sp,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive restock adjustment action knobs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Adjust",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { onAdjust(-1) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF242B3B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("adjust_down_${product.sku}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease stock level", modifier = Modifier.size(16.dp))
                    }

                    // Multi levels quick buttons (+5, +10)
                    Button(
                        onClick = { onAdjust(10) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF13362E),
                            contentColor = NeonMint
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("adjust_restock_10_${product.sku}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+10 Refill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledIconButton(
                        onClick = { onAdjust(1) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF242B3B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("adjust_up_${product.sku}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add step stock units", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// Dialog window for adding new SKU data
@Composable
fun AddSkuDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, Int, Int, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Grocery") }
    var stockInput by remember { mutableStateOf("15") }
    var minStockInput by remember { mutableStateOf("5") }
    var priceInput by remember { mutableStateOf("9.99") }
    var binInput by remember { mutableStateOf("G-01") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("add_sku_dialog_surface")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Register Custom SKU",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Divider(color = DarkBorder, thickness = 1.dp)

                // Name form description
                Text("Product Name & Details", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g., Hydrating Facial Serum)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_name")
                )

                // Category select radio-row
                Text("Category Selection", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Fashion", "Beauty & Care", "Grocery").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { 
                                category = cat
                                // auto default bin tags
                                binInput = when(cat) {
                                    "Fashion" -> "F-01"
                                    "Beauty & Care" -> "B-01"
                                    "Grocery" -> "G-01"
                                    else -> "X-01"
                                }
                            },
                            label = { Text(cat, fontSize = 11.sp) },
                            modifier = Modifier.testTag("dialog_chip_$cat")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU Code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_sku")
                    )

                    OutlinedTextField(
                        value = binInput,
                        onValueChange = { binInput = it },
                        label = { Text("Bin Locator Slot") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_bin")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = stockInput,
                        onValueChange = { stockInput = it },
                        label = { Text("Initial Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_stock")
                    )

                    OutlinedTextField(
                        value = minStockInput,
                        onValueChange = { minStockInput = it },
                        label = { Text("Min Stock Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_min")
                    )
                }

                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it },
                    label = { Text("Price Unit ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_price")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Final actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_add_sku")
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val priceVal = priceInput.toDoubleOrNull() ?: 0.0
                            val stockVal = stockInput.toIntOrNull() ?: 0
                            val minVal = minStockInput.toIntOrNull() ?: 5
                            val realSku = sku.ifBlank { "SKU-${Random.nextInt(100, 999)}" }
                            onSubmit(name, category, realSku, stockVal, minVal, priceVal, binInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.Black),
                        enabled = name.isNotBlank(),
                        modifier = Modifier.testTag("submit_add_sku")
                    ) {
                        Text("Add Product")
                    }
                }
            }
        }
    }
}

// Dialog window to edit product values
@Composable
fun EditSkuDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSubmit: (Product) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var stockInput by remember { mutableStateOf(product.stockLevel.toString()) }
    var minStockInput by remember { mutableStateOf(product.minRequiredStock.toString()) }
    var priceInput by remember { mutableStateOf(product.price.toString()) }
    var binInput by remember { mutableStateOf(product.binLocation) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("edit_sku_dialog_surface")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit SKU details: ${product.sku}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Divider(color = DarkBorder, thickness = 1.dp)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_product_name")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = binInput,
                        onValueChange = { binInput = it },
                        label = { Text("Bin locator slot") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_product_bin")
                    )

                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Price Unit ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_product_price")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = stockInput,
                        onValueChange = { stockInput = it },
                        label = { Text("Current Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_product_stock")
                    )

                    OutlinedTextField(
                        value = minStockInput,
                        onValueChange = { minStockInput = it },
                        label = { Text("Min Stock point") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonMint),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_product_min")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_edit_sku")
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val modified = product.copy(
                                name = name,
                                stockLevel = stockInput.toIntOrNull() ?: product.stockLevel,
                                minRequiredStock = minStockInput.toIntOrNull() ?: product.minRequiredStock,
                                price = priceInput.toDoubleOrNull() ?: product.price,
                                binLocation = binInput,
                                lastUpdated = System.currentTimeMillis()
                            )
                            onSubmit(modified)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.Black),
                        enabled = name.isNotBlank(),
                        modifier = Modifier.testTag("submit_edit_sku")
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}


// ==================== WAREHOUSE ORDERS LOGISTICS FULFILLMENT =====================

@Composable
fun OrdersFulfillmentPane(viewModel: DarkStoreViewModel) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    var selectedTabFilter by remember { mutableStateOf("Pending") } // Pending, Picking, Packed, Dispatched
    val filterOptions = listOf("Pending", "Picking", "Packed", "Dispatched")

    val filteredOrders = orders.filter { it.status == selectedTabFilter }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal tab states
        TabRow(
            selectedTabIndex = filterOptions.indexOf(selectedTabFilter),
            containerColor = DarkSurface,
            contentColor = NeonMint,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[filterOptions.indexOf(selectedTabFilter)]),
                    color = NeonMint
                )
            },
            modifier = Modifier.testTag("orders_status_tab_row")
        ) {
            filterOptions.forEach { filter ->
                val count = orders.count { it.status == filter }
                Tab(
                    selected = selectedTabFilter == filter,
                    onClick = { selectedTabFilter = filter },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(filter, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (filter == "Pending") ErrorRed else Color(0xFF263042)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        count.toString(),
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("orders_tab_$filter")
                )
            }
        }

        // List representation
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = TextMuted
                    )
                    Text(
                        text = "No $selectedTabFilter Orders",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Incoming simulator orders will populate this page automatically.",
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders) { order ->
                    OrderCardItem(
                        order = order,
                        viewModel = viewModel,
                        onClickCard = {
                            // If order is Pending or Picking, clicking it opens picking walkthrough mode.
                            viewModel.setActivePickingOrder(order)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderCardItem(
    order: WarehouseOrder,
    viewModel: DarkStoreViewModel,
    onClickCard: () -> Unit
) {
    val durationText = getElapsedMinutesText(order.timestamp)
    val pickerName by viewModel.activePickerName.collectAsStateWithLifecycle()

    val accentBorder = when (order.status) {
        "Pending" -> ErrorRed.copy(alpha = 0.5f)
        "Picking" -> NeonBlue
        "Packed" -> WarningOrange
        else -> NeonMint.copy(alpha = 0.5f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accentBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickCard)
            .testTag("order_card_${order.orderNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Code identity, status tag and elapsed timers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = NeonBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = order.orderNumber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // ETA Alert indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = if (order.status == "Dispatched") TextMuted else ErrorRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (order.status == "Dispatched") "Fulfillment Closed" else durationText,
                        fontSize = 10.sp,
                        color = if (order.status == "Dispatched") TextMuted else ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body list details of order contents
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Customer: ${order.customerName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Request payload: ${order.itemsDescription}",
                    fontSize = 12.sp,
                    color = DarkOnBg,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Assign logistics trigger actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL TRANSACTION VALUE",
                        fontSize = 8.sp,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", order.totalAmount)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint
                    )
                }

                // Active Assign/Next buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (order.status != "Dispatched") {
                        Button(
                            onClick = {
                                if (order.status == "Pending") {
                                    // Assign active picker profiles
                                    viewModel.setPickerName(pickerName)
                                }
                                viewModel.advanceOrderState(order)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (order.status) {
                                    "Pending" -> Color(0xFF0D2F40)
                                    "Picking" -> Color(0xFF4C2A0A)
                                    else -> NeonMint.copy(alpha = 0.15f)
                                },
                                contentColor = when(order.status) {
                                    "Pending" -> NeonBlue
                                    "Picking" -> WarningOrange
                                    else -> NeonMint
                                }
                            ),
                            border = BorderStroke(1.dp, when (order.status) {
                                "Pending" -> NeonBlue
                                "Picking" -> WarningOrange
                                else -> NeonMint
                            }),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_action_${order.orderNumber}")
                        ) {
                            val actionLabel = when (order.status) {
                                "Pending" -> "Assign & Pick"
                                "Picking" -> "Mark Packed"
                                "Packed" -> "Dispatch SLA"
                                else -> "Completed"
                            }
                            Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Cancel/Clear button representing picker decline
                    IconButton(
                        onClick = { viewModel.cancelOrder(order) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_cancel_${order.orderNumber}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Warehouse tasks", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Picker assigned attribution
            order.pickerName?.let { name ->
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0xFF1F2530), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(NeonBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Assigned Picker Staff Node: $name",
                        fontSize = 9.sp,
                        color = NeonBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Elapsed timer computation helper
private fun getElapsedMinutesText(startTime: Long): String {
    val deltaMs = System.currentTimeMillis() - startTime
    val seconds = deltaMs / 1000
    val minutes = seconds / 60
    return if (minutes < 1) {
        "Just now"
    } else {
        "${minutes}m ago SLA"
    }
}


// ====================== PICKING GAME HUD MODE VIEW =========================

@Composable
fun PickingHUDView(
    order: WarehouseOrder,
    viewModel: DarkStoreViewModel,
    onClose: () -> Unit
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val activePickerName by viewModel.activePickerName.collectAsStateWithLifecycle()

    // Map order lines into unique pieces to pick based on standard formatting
    // Parsing helper (e.g. "2x Luxe Cropped Denim Jacket" -> qty: 2, item: "Luxe Cropped Denim Jacket")
    val pickItems = remember(order.itemsDescription, products) {
        parseOrderItems(order.itemsDescription, products)
    }

    // Set interactive checklist state tracking pick checkbox confirmation
    val pickCheckList = remember { mutableStateListOf<Int>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("picking_hud_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Picking mode", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PICKING AGENT MISSION HUD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Active Order: ${order.orderNumber}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            IconButton(
                onClick = { /* Help context */ },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = TextMuted)
            }
        }

        Divider(color = DarkBorder)

        // Guide checklist message
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10192C)),
            border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PrecisionManufacturing, // custom logistics icon replacement symbol
                    contentDescription = null,
                    sizeX = 24.dp,
                    tint = NeonBlue
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Warehouse Picker Assignment for $activePickerName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Locate these items immediately on the shelves. Scan/validate bin placements.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Checklist titles
        Text(
            text = "SCAN SHEET ITEMS (${pickCheckList.size} / ${pickItems.size} SECURED)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            letterSpacing = 0.5.sp
        )

        // Item checklist list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(pickItems) { index, pickerItem ->
                val isChecked = pickCheckList.contains(index)
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) Color(0xFF0D251D) else DarkSurface
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (isChecked) NeonMint.copy(alpha = 0.5f) else DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isChecked) {
                                pickCheckList.remove(index)
                            } else {
                                pickCheckList.add(index)
                            }
                        }
                        .testTag("pick_item_${index}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked == true) {
                                    pickCheckList.add(index)
                                } else {
                                    pickCheckList.remove(index)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = NeonMint,
                                checkmarkColor = Color.Black
                            ),
                            modifier = Modifier.testTag("checkbox_pick_${index}")
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${pickerItem.qty}x ${pickerItem.name}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChecked) TextMuted else Color.White
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(3.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STOCK SKU: ${pickerItem.sku}",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF282F3F))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AISLE BIN ${pickerItem.bin}",
                                        fontSize = 10.sp,
                                        color = NeonBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Big shelf location marker badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF9F0A).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pickerItem.bin,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningOrange
                            )
                        }
                    }
                }
            }
        }

        // Action confirmation button matching pick status
        val allChecked = pickCheckList.size == pickItems.size

        Button(
            onClick = {
                // Progress status
                viewModel.advanceOrderState(order)
                if (order.status == "Pending") {
                    // if it was pending and we advanced, it is now Picking. We can stay in Picking screen.
                } else if (order.status == "Picking") {
                    // if it is picking and advanced, it is now Packed! Close modal screen.
                    onClose()
                } else {
                    onClose()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allChecked) NeonMint else Color(0xFF1B2230),
                contentColor = if (allChecked) Color.Black else TextMuted
            ),
            enabled = allChecked || order.status == "Pending",
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_pick_action_button"),
            shape = RoundedCornerShape(10.dp)
        ) {
            val missionActionLabel = when (order.status) {
                "Pending" -> "ASSIGN & INITIATE RUN"
                "Picking" -> if (allChecked) "PACK ORDER & COMPLETE RUN" else "SECURE ALL ITEMS TO CONFIRM RUN"
                "Packed" -> "SHIP VEHICLE SLA STAGGER"
                else -> "CLOSE AGENT SHEET"
            }
            Text(missionActionLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// Order Picker logistics data parser
data class PickerItem(
    val qty: Int,
    val name: String,
    val sku: String,
    val bin: String
)

private fun parseOrderItems(itemsDesc: String, products: List<Product>): List<PickerItem> {
    // itemsDesc format example: "1x Luxe Cropped Denim Jacket, 1x Rose Hydrating Glow Serum"
    val parts = itemsDesc.split(", ")
    return parts.mapNotNull { part ->
        try {
            val match = Regex("""(\d+)x (.+)""").find(part)
            if (match != null) {
                val qty = match.groupValues[1].toInt()
                val prodName = match.groupValues[2].trim()
                val matchedProd = products.find { it.name.contains(prodName, ignoreCase = true) || prodName.contains(it.name, ignoreCase = true) }
                PickerItem(
                    qty = qty,
                    name = prodName,
                    sku = matchedProd?.sku ?: "SKU-UNKNOWN",
                    bin = matchedProd?.binLocation ?: "A-00"
                )
            } else {
                PickerItem(qty = 1, name = part, sku = "SKU-UNKNOWN", bin = "A-01")
            }
        } catch (e: Exception) {
            null
        }
    }
}

// Custom warehouse forklift indicator mapping helper removed
