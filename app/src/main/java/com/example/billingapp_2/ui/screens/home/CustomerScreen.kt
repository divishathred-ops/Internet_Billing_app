
package com.example.billingapp_2.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp_2.model.CustomerModel
import com.example.billingapp_2.ui.components.AppDrawer
import com.example.billingapp_2.utils.FileTemplateGenerator
import com.example.billingapp_2.viewmodel.AuthViewModel
import com.example.billingapp_2.viewmodel.CustomerViewModel
import kotlinx.coroutines.launch

private val PrimaryGreen = Color(0xFF54D22D)
private val LightGreenSurface = Color(0xFFFFFFFF)
private val LightGreenBackground = Color(0xFFEEFCE9)
private val SurfaceColor = Color(0xFFFFFFFF)
private val SurfaceVariant = Color(0xFFF1F5F1)
private val DarkTextColor = Color(0xFF323431)
private val MediumTextColor = Color(0xFF494D48)
private val OutlineColor = Color(0xFFD0D7D1)
private val IdStbTextColor = Color(0xFFCC5500)

private enum class PaymentStatusFilter { ALL, PAID, UNPAID }
private enum class TemplateFormat { CSV, EXCEL, GOOGLE_SHEETS }
private enum class FabAction { ADD_CUSTOMER, IMPORT_CSV, DOWNLOAD_TEMPLATE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomerScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    customerViewModel: CustomerViewModel,
    // This parameter controls the UI. When true, it shows a simplified view.
    isViewOnlyMode: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current
    val customers by customerViewModel.customers.collectAsState()
    val availableBillingAreas by customerViewModel.allBillingAreas.collectAsState()
    val currentUserRole by authViewModel.userRole.collectAsState()
    val currentUserAssignedAreas by authViewModel.assignedAreas.collectAsState()
    val isLoading by customerViewModel.isLoading.collectAsState()
    val isAuthReady by authViewModel.isAuthReady.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(PaymentStatusFilter.ALL) }
    var selectedAreaFilter by remember { mutableStateOf<String?>(null) }
    var showAreaFilterMenu by remember { mutableStateOf(false) }

    // FAB and Dialog States
    var isFabExpanded by remember { mutableStateOf(false) }
    var showTemplateFormatDialog by remember { mutableStateOf(false) }

    // Check if we can navigate back
    val canPop by remember(navController) { mutableStateOf(navController.previousBackStackEntry != null) }

    val filteredCustomers = remember(customers, searchQuery, statusFilter, selectedAreaFilter, currentUserRole, currentUserAssignedAreas) {
        customers
            .filter { customer ->
                val hasAreaAccess = currentUserRole != "agent" || currentUserAssignedAreas.contains(customer.area)
                // Searching is disabled in view-only mode, so we don't need to check isViewOnlyMode here
                val matchesSearch = searchQuery.isBlank() ||
                        customer.name.contains(searchQuery, ignoreCase = true) ||
                        customer.customerId.contains(searchQuery, ignoreCase = true) ||
                        customer.stbNumber.contains(searchQuery, ignoreCase = true)
                val matchesStatus = when (statusFilter) {
                    PaymentStatusFilter.ALL -> true
                    PaymentStatusFilter.PAID -> customer.balance <= 0.0
                    PaymentStatusFilter.UNPAID -> customer.balance > 0.0
                }
                val matchesArea = selectedAreaFilter == null || customer.area == selectedAreaFilter
                hasAreaAccess && matchesSearch && matchesStatus && matchesArea
            }
            .groupBy { it.area }
            .toSortedMap()
    }

    // The navigation drawer is only available in the full-featured mode
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isViewOnlyMode, // Disable swipe-to-open in view-only mode
        drawerContent = {
            AppDrawer(
                navController = navController,
                drawerState = drawerState,
                scope = scope,
                authViewModel = authViewModel,
                onItemSelected = { route ->
                    navController.navigate(route)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Customers", fontWeight = FontWeight.Bold, color = DarkTextColor) },
                    navigationIcon = {
                        // In view-only mode or if we can pop back, show the back arrow
                        if (isViewOnlyMode || canPop) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkTextColor)
                            }
                        } else {
                            // Otherwise, show the menu icon for the drawer
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = DarkTextColor)
                            }
                        }
                    },
                    actions = {
                        // Actions are hidden in view-only mode
                        if (!isViewOnlyMode) {
                            Box {
                                IconButton(onClick = { showAreaFilterMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Filter by Area", tint = DarkTextColor)
                                }
                                DropdownMenu(
                                    expanded = showAreaFilterMenu,
                                    onDismissRequest = { showAreaFilterMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Areas") },
                                        onClick = {
                                            selectedAreaFilter = null
                                            showAreaFilterMenu = false
                                        }
                                    )
                                    availableBillingAreas.sorted().forEach { area ->
                                        DropdownMenuItem(
                                            text = { Text(area) },
                                            onClick = {
                                                selectedAreaFilter = area
                                                showAreaFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = LightGreenSurface
                    )
                )
            },
            floatingActionButton = {
                // FAB is hidden in view-only mode
                if (!isViewOnlyMode) {
                    if (currentUserRole == "admin") {
                        ExpandableFAB(
                            isExpanded = isFabExpanded,
                            onToggle = { isFabExpanded = !isFabExpanded },
                            onActionClick = { action ->
                                isFabExpanded = false
                                when (action) {
                                    FabAction.ADD_CUSTOMER -> navController.navigate("addCustomer")
                                    FabAction.IMPORT_CSV -> navController.navigate("importCustomers")
                                    FabAction.DOWNLOAD_TEMPLATE -> showTemplateFormatDialog = true
                                }
                            }
                        )
                    } else {
                        FloatingActionButton(
                            onClick = { navController.navigate("addCustomer") },
                            shape = CircleShape,
                            containerColor = PrimaryGreen
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Customer", tint = Color.White)
                        }
                    }
                }
            },
            containerColor = LightGreenSurface
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Search and filter chips are hidden in view-only mode
                if (!isViewOnlyMode) {
                    SearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    )
                    FilterChips(
                        selectedFilter = statusFilter,
                        onFilterSelected = { statusFilter = it },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (!isAuthReady || (isLoading && customers.isEmpty())) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading customers...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceColor)
                    ) {
                        if (filteredCustomers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (customers.isEmpty()) "No customers added yet." else "No customers match the filter.",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (customers.isEmpty()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Add your first customer to get started!",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            filteredCustomers.forEach { (area, customersInArea) ->
                                stickyHeader {
                                    AreaHeader(area = area)
                                }
                                items(customersInArea, key = { it.id }) { customer ->
                                    CustomerListItem(customer = customer) {
                                        navController.navigate("customerDetail/${customer.id}")
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 1.4.dp,
                                        color = OutlineColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTemplateFormatDialog) {
        TemplateFormatDialog(
            onDismiss = { showTemplateFormatDialog = false },
            onFormatSelected = { format ->
                showTemplateFormatDialog = false
                scope.launch {
                    try {
                        val fileTemplateGenerator = FileTemplateGenerator(context)
                        val success = when (format) {
                            TemplateFormat.CSV -> fileTemplateGenerator.downloadCsvTemplateWithShare()
                            TemplateFormat.EXCEL -> fileTemplateGenerator.downloadCsvTemplateWithShare()
                            TemplateFormat.GOOGLE_SHEETS -> fileTemplateGenerator.downloadCsvTemplateWithShare()
                        }
                        if (success) {
                            Toast.makeText(context, "${format.name.replace('_', ' ')} template shared successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to generate template", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ExpandableFAB(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onActionClick: (FabAction) -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        label = "fab_rotation"
    )
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isExpanded) {
            SmallFAB(
                icon = Icons.Default.CloudDownload,
                text = "Download Template",
                onClick = { onActionClick(FabAction.DOWNLOAD_TEMPLATE) }
            )
            SmallFAB(
                icon = Icons.Default.Upload,
                text = "Import CSV",
                onClick = { onActionClick(FabAction.IMPORT_CSV) }
            )
            SmallFAB(
                icon = Icons.Default.PersonAdd,
                text = "Add Customer",
                onClick = { onActionClick(FabAction.ADD_CUSTOMER) }
            )
        }
        FloatingActionButton(
            onClick = onToggle,
            shape = CircleShape,
            containerColor = PrimaryGreen
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun SmallFAB(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkTextColor.copy(alpha = 0.8f))
        ) {
            Text(
                text = text,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            containerColor = PrimaryGreen,
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TemplateFormatDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (TemplateFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Template Format") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the format for your customer template:")
                Spacer(modifier = Modifier.height(8.dp))
                TemplateFormat.values().forEach { format ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFormatSelected(format) },
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (format) {
                                    TemplateFormat.CSV -> Icons.Default.Description
                                    TemplateFormat.EXCEL -> Icons.Default.TableChart
                                    TemplateFormat.GOOGLE_SHEETS -> Icons.Default.Cloud
                                },
                                contentDescription = null,
                                tint = PrimaryGreen
                            )
                            Text(
                                text = when (format) {
                                    TemplateFormat.CSV -> "CSV Format"
                                    TemplateFormat.EXCEL -> "Excel Format"
                                    TemplateFormat.GOOGLE_SHEETS -> "Google Sheets"
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search customers", color = MediumTextColor) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = PrimaryGreen) },
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = LightGreenBackground,
            unfocusedContainerColor = LightGreenBackground,
            cursorColor = PrimaryGreen,
            focusedTextColor = DarkTextColor
        ),
        singleLine = true
    )
}

@Composable
private fun FilterChips(
    selectedFilter: PaymentStatusFilter,
    onFilterSelected: (PaymentStatusFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PaymentStatusFilter.values()) { filter ->
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SurfaceVariant,
                    labelColor = DarkTextColor,
                    selectedContainerColor = PrimaryGreen,
                    selectedLabelColor = Color.White
                ),
                border = null
            )
        }
    }
}

@Composable
private fun AreaHeader(area: String) {
    Text(
        text = area.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MediumTextColor,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun CustomerListItem(customer: CustomerModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(customer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = DarkTextColor)
            Text("ID: ${customer.customerId}", style = MaterialTheme.typography.bodySmall, color = IdStbTextColor)
            Text("STB: ${customer.stbNumber}", style = MaterialTheme.typography.bodySmall, color = IdStbTextColor)
        }
        Text(
            text = "₹${"%.2f".format(customer.balance)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = DarkTextColor
        )
    }
}
