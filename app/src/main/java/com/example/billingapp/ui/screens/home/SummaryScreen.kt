
package com.example.billingapp.ui.screens.home

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.billingapp.ui.components.DateRangePickerDialog
import com.example.billingapp.viewmodel.AuthViewModel
import com.example.billingapp.viewmodel.CustomerViewModel
import com.example.billingapp.viewmodel.ExpenseViewModel
import com.example.billingapp.viewmodel.PaymentCollectionViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.*

// --- UI Color Palette from Screenshot ---
private val cardGreenBg = Color(0xFFDCFCE7)
private val cardGreenText = Color(0xFF166534)
private val cardBlueBg = Color(0xFFE0F2FE)
private val cardBlueText = Color(0xFF075985)
private val cardOrangeBg = Color(0xFFFFF7ED)
private val cardOrangeText = Color(0xFFC2410C)
private val cardRedText = Color(0xFFB91C1C)
private val cardDefaultBg = Color(0xFFF9FAFB)
private val cardDefaultText = Color(0xFF6B7280) // Gray-500 for titles
private val cardValueText = Color(0xFF1F2937)  // Gray-800 for values
private val primaryAction = Color(0xFF16A34A)
private val screenBackground = Color.White

// Helper to format currency and numbers with commas, matching the screenshot
private fun formatCurrency(amount: Double): String {
    return "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount.toInt())}"
}
private fun formatNumber(number: Int): String {
    return NumberFormat.getNumberInstance(Locale.US).format(number)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    customerViewModel: CustomerViewModel,
    expenseViewModel: ExpenseViewModel,
    paymentCollectionViewModel: PaymentCollectionViewModel, // Unused, but kept for signature consistency
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserRole by authViewModel.userRole.collectAsState()
    val userModel by authViewModel.userModel.collectAsState()

    // State values from ViewModels
    val totalCustomers by customerViewModel.totalCustomers.collectAsState()
    val totalMonthlyCollection by customerViewModel.totalMonthlyCollectionAmount.collectAsState()
    val totalTodayCollection by customerViewModel.totalTodayCollectionAmount.collectAsState()
    val totalUnpaid by customerViewModel.totalUnpaid.collectAsState()
    val currentMonthExpenses by expenseViewModel.summaryScreenCurrentMonthExpenses.collectAsState()
    val previousMonthTotalExpenses by expenseViewModel.summaryScreenPreviousMonthExpenses.collectAsState()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showDownloadOptions by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val isAuthReady by authViewModel.isAuthReady.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentUserRole == "admin") {
                        IconButton(onClick = { showDownloadOptions = true }) {
                            Icon(Icons.Default.Download, contentDescription = "Download Report")
                        }
                    } else {
                        // Add a spacer to balance the layout and center the title correctly
                        Spacer(Modifier.width(48.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = screenBackground
                )
            )
        },
        containerColor = screenBackground
    ) { padding ->
        if (!isAuthReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Grid Layout for top cards ---
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Total Customers",
                        value = formatNumber(totalCustomers),
                        containerColor = cardDefaultBg,
                        titleColor = cardDefaultText,
                        valueColor = cardValueText,
                        // Inside the onClick for the "Total Customers" SummaryGridCard
                        onClick = { navController.navigate("customers?isViewOnlyMode=true") }
                    )
                    SummaryGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Monthly Collection",
                        value = formatCurrency(totalMonthlyCollection),
                        containerColor = cardGreenBg,
                        titleColor = cardGreenText,
                        valueColor = cardGreenText,
                        onClick = {
                            val (start, end) = customerViewModel.getCurrentMonthRange()
                            navController.navigate("monthlyCollection?start=${start.time}&end=${end.time}&title=Monthly Collection")
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Today's Collection",
                        value = formatCurrency(totalTodayCollection),
                        containerColor = cardBlueBg,
                        titleColor = cardBlueText,
                        valueColor = cardBlueText,
                        onClick = {
                            val (start, end) = customerViewModel.getTodayRange()
                            navController.navigate("monthlyCollection?start=${start.time}&end=${end.time}&title=Today's Collection")
                        }
                    )
                    SummaryGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Total Unpaid Balance",
                        value = formatCurrency(totalUnpaid),
                        containerColor = cardOrangeBg,
                        titleColor = cardOrangeText,
                        valueColor = cardOrangeText,
                        onClick = { /* Navigate to unpaid customers list if available */ }
                    )
                }

                // --- Full-Width Expense Cards ---
                CurrentMonthExpenseCard(
                    amount = currentMonthExpenses,
                    userRole = currentUserRole,
                    onAddClick = { showAddExpenseDialog = true },
                    onCardClick = {
                        // Navigation logic remains the same
                        if (currentUserRole == "admin") {
                            navController.navigate("agentExpenses")
                        } else {
                            userModel?.let {
                                if (it.uid.isNotBlank() && it.name.isNotBlank()) {
                                    navController.navigate("agentExpenseDetail/${it.uid}/${it.name}")
                                }
                            }
                        }
                    }
                )

                FullWidthSummaryCard(
                    title = "Previous Month Expenses",
                    value = formatCurrency(previousMonthTotalExpenses),
                    valueColor = cardRedText,
                    onClick = {
                        // Navigation logic remains the same
                        if (currentUserRole == "admin") {
                            navController.navigate("agentExpenses")
                        } else {
                            userModel?.let {
                                if (it.uid.isNotBlank() && it.name.isNotBlank()) {
                                    navController.navigate("agentExpenseDetail/${it.uid}/${it.name}")
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // --- Dialogs (Unchanged) ---
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            expenseViewModel = expenseViewModel,
            onDismiss = { showAddExpenseDialog = false }
        )
    }

    if (showDownloadOptions) {
        DownloadReportDialog(
            onDismiss = { showDownloadOptions = false },
            onConfirm = { option ->
                showDownloadOptions = false
                handleReportDownload(option, context, scope, customerViewModel) {
                    showCustomDateDialog = true
                }
            }
        )
    }

    if (showCustomDateDialog) {
        DateRangePickerDialog(
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { startDate, endDate ->
                showCustomDateDialog = false
                scope.launch {
                    generateAndShareReport(context, customerViewModel, startDate, endDate, "custom_report.csv")
                }
            }
        )
    }
}


// --- Reusable UI Components (Refined to match screenshot) ---

@Composable
private fun SummaryGridCard(
    title: String,
    value: String,
    containerColor: Color,
    titleColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), // Slightly more rounded corners
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat design
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun CurrentMonthExpenseCard(
    amount: Double,
    userRole: String?,
    onAddClick: () -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardDefaultBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Current Month Expenses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cardDefaultText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatCurrency(amount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = cardRedText
                )
            }
            if (userRole == "agent") {
                Button(
                    onClick = onAddClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAction),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FullWidthSummaryCard(
    title: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardDefaultBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = cardDefaultText
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}


// --- Dialogs & Helper Functions (Unchanged) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    expenseViewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var expenseAmount by remember { mutableStateOf("") }
    var expenseDescription by remember { mutableStateOf("") }
    var selectedExpenseType by remember { mutableStateOf("food") }
    val expenseTypes = listOf("food", "petrol", "salary", "collectedByBoss", "others")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedExpenseType.replaceFirstChar { it.titlecase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        expenseTypes.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when(type) {
                                            "collectedByBoss" -> "Collected by Boss"
                                            else -> type.replaceFirstChar { it.titlecase() }
                                        }
                                    )
                                },
                                onClick = {
                                    selectedExpenseType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = expenseAmount,
                    onValueChange = { expenseAmount = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expenseDescription,
                    onValueChange = { expenseDescription = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = expenseAmount.toDoubleOrNull()
                if (amount == null || amount <= 0 || expenseDescription.isBlank()) {
                    Toast.makeText(context, "Please fill all fields correctly", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                expenseViewModel.addExpense(amount, selectedExpenseType, expenseDescription) { success ->
                    if (success) {
                        Toast.makeText(context, "Expense added!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Failed to add expense.", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DownloadReportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf("today") }
    val options = listOf(
        "Today's Collection" to "today",
        "Current Month" to "currentMonth",
        "Previous Month" to "previousMonth",
        "Custom Date Range" to "customDate"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Report Period") },
        text = {
            Column {
                options.forEach { (text, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = value }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedOption == value),
                            onClick = { selectedOption = value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedOption) }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun handleReportDownload(
    option: String,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    customerViewModel: CustomerViewModel,
    showCustomDialog: () -> Unit
) {
    scope.launch {
        val reportDetails = when (option) {
            "today" -> Pair(customerViewModel.getTodayRange(), "today_collection_report.csv")
            "currentMonth" -> Pair(customerViewModel.getCurrentMonthRange(), "current_month_report.csv")
            "previousMonth" -> Pair(customerViewModel.getPreviousMonthRange(), "previous_month_report.csv")
            "customDate" -> {
                showCustomDialog()
                null
            }
            else -> null
        }

        reportDetails?.let { (dateRange, fileName) ->
            generateAndShareReport(context, customerViewModel, dateRange.first, dateRange.second, fileName)
        }
    }
}

private suspend fun generateAndShareReport(
    context: Context,
    customerViewModel: CustomerViewModel,
    startDate: Date,
    endDate: Date,
    fileName: String
) {
    val data = customerViewModel.getCollectionDataForRange(startDate, endDate)
    if (data.isNotEmpty()) {
        val csvContent = customerViewModel.generateCollectionCsv(data)
        shareCsv(context, csvContent, fileName)
    } else {
        Toast.makeText(context, "No data for the selected period.", Toast.LENGTH_SHORT).show()
    }
}
// FIXED: Updated shareCsv function in SummaryScreen.kt
private fun shareCsv(context: Context, csvContent: String, fileName: String) {
    try {
        // Create the reports directory if it doesn't exist
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        val file = File(reportsDir, fileName)
        file.writeText(csvContent)

        // FIXED: Use the correct authority that matches the manifest configuration
        val authority = "${context.packageName}.provider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Report via"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error generating report: ${e.message}", Toast.LENGTH_LONG).show()
        Log.e("ShareCsv", "Failed to share CSV", e)
    }
}

