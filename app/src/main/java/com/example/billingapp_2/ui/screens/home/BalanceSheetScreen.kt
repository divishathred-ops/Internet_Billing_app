
package com.example.billingapp_2.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp_2.model.TransactionModel
import com.example.billingapp_2.viewmodel.AuthViewModel
import com.example.billingapp_2.viewmodel.CustomerViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetScreen(
    navController: NavController,
    customerId: String,
    authViewModel: AuthViewModel,
    customerViewModel: CustomerViewModel
) {
    val context = LocalContext.current
    val customer by customerViewModel.selectedCustomer.collectAsState()
    val transactions by customerViewModel.selectedCustomerTransactions.collectAsState()
    val isLoading by customerViewModel.isLoading.collectAsState()
    val userRole by authViewModel.userRole.collectAsState()
    val agentNames by customerViewModel.agentNamesFlow.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<TransactionModel?>(null) }

    LaunchedEffect(customerId) {
        customerViewModel.selectCustomer(customerId) // This now uses Room flows
    }

    DisposableEffect(Unit) {
        onDispose {
            customerViewModel.resetCustomerSelection()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: "Balance Sheet") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // **FIX:** Show loading indicator whenever isLoading is true, even if there's old data
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions, key = { it.id }) { transaction ->
                        val collectorName = agentNames[transaction.createdBy]
                        TransactionCard(
                            transaction = transaction,
                            isAdmin = userRole == "admin",
                            onDeleteClick = {
                                transactionToDelete = it
                                showDeleteDialog = true
                            },
                            agentName = collectorName
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                transactionToDelete = null
            },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction? This action cannot be undone and will update the customer's balance.") },
            confirmButton = {
                Button(
                    onClick = {
                        transactionToDelete?.let { tx ->
                            customerViewModel.deleteTransaction(customerId, tx) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        showDeleteDialog = false
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    transactionToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun TransactionCard(
    transaction: TransactionModel,
    isAdmin: Boolean,
    onDeleteClick: (TransactionModel) -> Unit,
    agentName: String?
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Determine amount prefix and color based on transaction type
    val (amountPrefix, amountColor) = when (transaction.type) {
        "collectedPayment" -> "- ₹" to Color.Red
        "initial", "generatedDue" -> "+ ₹" to Color(0xFF008000) // green
        "balanceUpdate" -> "" to Color.Gray
        else -> "" to Color.Black
    }

    val displayAmount = "$amountPrefix${"%.2f".format(transaction.amount)}"

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val desc = if (transaction.description.isBlank()) {
                    transaction.type.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                } else transaction.description

                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                val subtitle = if (transaction.type == "generatedDue" && !transaction.billPeriod.isNullOrBlank()) {
                    "For: ${transaction.billPeriod}"
                } else {
                    dateFormat.format(transaction.date.toDate())
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                if (transaction.type == "collectedPayment" && agentName != null) {
                    Text(
                        text = "Collected by: $agentName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayAmount,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                if (isAdmin) {
                    IconButton(onClick = { onDeleteClick(transaction) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Transaction",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

