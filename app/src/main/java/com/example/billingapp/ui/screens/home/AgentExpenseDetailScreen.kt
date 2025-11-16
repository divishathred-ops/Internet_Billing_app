
package com.example.billingapp.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp.model.ExpenseModel
import com.example.billingapp.ui.components.DateRangePickerDialog
import com.example.billingapp.viewmodel.AuthViewModel
import com.example.billingapp.viewmodel.CustomerViewModel
import com.example.billingapp.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentExpenseDetailScreen(
    navController: NavController,
    agentId: String,
    agentName: String,
    expenseViewModel: ExpenseViewModel,
    customerViewModel: CustomerViewModel,
    authViewModel: AuthViewModel
) {
    // --- Correct Data Fetching and State Consumption ---
    // 1. Consume the specific, pre-filtered list of expenses for the viewing agent.
    val agentExpenses by expenseViewModel.viewingAgentExpenses.collectAsState()
    val agentMonthlyCollection by customerViewModel.viewingAgentMonthlyCollection.collectAsState()
    val userRole by authViewModel.userRole.collectAsState()

    // --- UI State Management ---
    var showDeleteDialog by remember { mutableStateOf<ExpenseModel?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // 2. Set default date range to the current month.
    val (initialStartDate, initialEndDate) = remember {
        val calendar = Calendar.getInstance()
        val start = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.time
        val end = calendar.apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.time
        start to end
    }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }

    // --- Lifecycle Management ---
    // 3. When the screen is first composed (or agentId changes), tell the ViewModels
    // which agent we are looking at. This triggers the data flows.
    LaunchedEffect(agentId) {
        customerViewModel.setViewingAgent(agentId)
        expenseViewModel.selectAgentForViewing(agentId)
    }

    // 4. When the screen is disposed (user navigates away), clean up the state
    // in the ViewModels to prevent stale data on the next visit.
    DisposableEffect(Unit) {
        onDispose {
            customerViewModel.setViewingAgent(null)
            expenseViewModel.selectAgentForViewing(null)
        }
    }

    // 5. Filter the displayed expenses based on the selected date range.
    // This filtering is now done on a much smaller, pre-filtered list.
    val filteredExpenses = remember(agentExpenses, startDate, endDate) {
        agentExpenses.filter {
            val expenseDate = it.date.toDate()
            !expenseDate.before(startDate) &&!expenseDate.after(endDate)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$agentName's Expenses") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date Range")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // This card for monthly collection remains unchanged as its logic was correct.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Monthly Collection", style = MaterialTheme.typography.titleMedium)
                    Text("₹${"%.2f".format(agentMonthlyCollection)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // The "Previous Month" button has been removed as requested.

            if (filteredExpenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No expenses recorded for this period.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // This list now correctly includes ALL expense types, including "collectedByBoss".
                    items(filteredExpenses, key = { it.id }) { expense ->
                        ExpenseCard(
                            expense = expense,
                            // The delete icon is shown only if the user is an admin.
                            showDeleteIcon = userRole == "admin",
                            onDeleteClick = { showDeleteDialog = expense }
                        )
                    }
                }
            }
        }

        if (showDateRangePicker) {
            DateRangePickerDialog(
                onDismiss = { showDateRangePicker = false },
                onConfirm = { start, end ->
                    startDate = start
                    endDate = end
                    showDateRangePicker = false
                }
            )
        }

        // --- Admin Delete Confirmation Dialog ---
        showDeleteDialog?.let { expenseToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to delete this expense? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            expenseViewModel.deleteExpense(expenseToDelete.id) { /* Handle result */ }
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// The ExpenseCard composable remains largely the same, but is included for completeness.
@Composable
private fun ExpenseCard(
    expense: ExpenseModel,
    showDeleteIcon: Boolean,
    onDeleteClick: () -> Unit
) {
    val formattedDate = remember(expense.date) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(expense.date.toDate())
    }

    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.type.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (expense.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = expense.description, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = formattedDate, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "₹${"%.2f".format(expense.amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            // The delete icon is conditionally rendered based on the user's role.
            if (showDeleteIcon) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
