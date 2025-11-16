
package com.example.billingapp.ui.screens.home

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.billingapp.model.MonthlyCollectionData
import com.example.billingapp.ui.components.DateRangePickerDialog
import com.example.billingapp.viewmodel.AuthViewModel
import com.example.billingapp.viewmodel.CustomerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyCollectionScreen(
    navController: NavController,
    customerViewModel: CustomerViewModel,
    authViewModel: AuthViewModel
) {
    val backStackEntry = navController.currentBackStackEntry
    val startTime = backStackEntry?.arguments?.getLong("start")
    val endTime = backStackEntry?.arguments?.getLong("end")

    val (defaultStart, defaultEnd) = remember {
        customerViewModel.getTodayRange()
    }

    var startDate by remember {
        mutableStateOf(if (startTime != null && startTime != 0L) Date(startTime) else defaultStart)
    }
    var endDate by remember {
        mutableStateOf(if (endTime != null && endTime != 0L) Date(endTime) else defaultEnd)
    }

    // Set initial date range and trigger backend call
    LaunchedEffect(startDate, endDate) {
        Log.d("MonthlyCollectionScreen", "Setting date range: $startDate to $endDate")
        customerViewModel.setCollectionDateRange(startDate, endDate)
    }

    val agentNames by customerViewModel.agentNamesFlow.collectAsState()
    // --- NOTE: This flow is now correctly pre-filtered by the ViewModel based on user role ---
    val collectionDetails by customerViewModel.collectionDetails.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabTitles = listOf("Details", "Summary")
    var showDateRangeDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd-MMM-yy", Locale.getDefault())
    val screenTitle = "Collection Report"
    val dateSubtitle = "${dateFormat.format(startDate)} To ${dateFormat.format(endDate)}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(screenTitle)
                        Text(dateSubtitle, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangeDialog = true }) {
                        Icon(Icons.Default.CalendarToday, "Select Date Range")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (collectionDetails.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No collection data for the selected period.")
                }
            } else {
                when (selectedTabIndex) {
                    0 -> DetailsView(collectionDetails, navController, agentNames)
                    1 -> SummaryView(collectionDetails, agentNames)
                }
            }
        }
    }

    if (showDateRangeDialog) {
        DateRangePickerDialog(
            onDismiss = { showDateRangeDialog = false },
            onConfirm = { start, end ->
                startDate = start
                endDate = end
                // The LaunchedEffect will automatically trigger the ViewModel update
                showDateRangeDialog = false
            }
        )
    }
}

// ... The rest of MonthlyCollectionScreen.kt (DetailsView, PaymentItemCard, SummaryView, SummaryRow)
// remains unchanged as the logic fix was in the ViewModel.
// I am including it here for completeness.

@Composable
private fun DetailsView(
    collectionDetails: List<MonthlyCollectionData>,
    navController: NavController,
    agentNames: Map<String, String>
) {
    val groupedByDate = collectionDetails
        .groupBy { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it.timestamp) }
        .toList()
        .sortedByDescending { (_, paymentsOnDate) -> paymentsOnDate.firstOrNull()?.timestamp }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groupedByDate.forEach { (date, paymentsOnDate) ->
            item {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Divider()
            }
            items(paymentsOnDate.sortedByDescending { it.timestamp }, key = { it.customerId + it.timestamp.time }) { payment ->
                PaymentItemCard(
                    payment = payment,
                    agentName = agentNames[payment.agentId] ?: "Unknown",
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun PaymentItemCard(
    payment: MonthlyCollectionData,
    agentName: String,
    navController: NavController
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("customerDetail/${payment.customerId}") },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(payment.customerName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(agentName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("₹${"%.2f".format(payment.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryView(collectionDetails: List<MonthlyCollectionData>, agentNames: Map<String, String>) {
    val totalAmount = collectionDetails.sumOf { it.amount }
    val totalCustomers = collectionDetails.distinctBy { it.customerId }.size

    val agentCollections = collectionDetails
        .groupBy { it.agentId }
        .mapValues { (_, transactions) ->
            Triple(
                transactions.distinctBy { it.customerId }.size,
                transactions.sumOf { it.amount },
                transactions.groupBy { it.area }.mapValues { (_, areaTxs) -> areaTxs.sumOf { it.amount } }
            )
        }
        .entries.sortedByDescending { it.value.second }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    SummaryRow("Total Customers", "$totalCustomers")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Total Collection", "₹${"%.2f".format(totalAmount)}", isTotal = true)
                }
            }
        }

        items(agentCollections) { (agentId, agentData) ->
            val (agentCustomerCount, agentTotalAmount, areaData) = agentData
            var expanded by remember { mutableStateOf(false) }

            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(agentNames[agentId] ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = "Expand")
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            SummaryRow("Customers", "$agentCustomerCount")
                            SummaryRow("Collection", "₹${"%.2f".format(agentTotalAmount)}")
                            if (areaData.isNotEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Text("Area Breakdown", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                                areaData.forEach { (area, amount) ->
                                    SummaryRow(area, "₹${"%.2f".format(amount)}")
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
private fun SummaryRow(label: String, value: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isTotal) 18.sp else 16.sp
        )
        Text(
            text = value,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (isTotal) 18.sp else 16.sp
        )
    }
}
