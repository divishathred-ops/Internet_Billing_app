package com.example.billingapp_2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.billingapp_2.model.CustomerPaymentDetail
import androidx.compose.ui.tooling.preview.Preview

/**
 * A dialog to display a list of customer payment details.
 * Each item shows customer name, collected amount, and the collecting agent/admin.
 * Clicking an item navigates to the customer's detail screen.
 *
 * @param customerPaymentDetails The list of [CustomerPaymentDetail] to display.
 * @param onDismiss Request to dismiss the dialog.
 * @param onCustomerClick Lambda to handle navigation when a customer item is clicked,
 * receives the customerId as a parameter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPaymentListDialog(
    customerPaymentDetails: List<CustomerPaymentDetail>,
    onDismiss: () -> Unit,
    onCustomerClick: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f) // Take up most of the width
                .fillMaxHeight(0.85f) // Take up most of the height
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Collection Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (customerPaymentDetails.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No collections found for the selected criteria.")
                    }
                } else {
                    LazyColumn {
                        items(customerPaymentDetails) { detail ->
                            CustomerPaymentListItem(detail = detail, onCustomerClick = onCustomerClick)
                            Spacer(modifier = Modifier.height(8.dp)) // Spacing between items
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable for a single customer payment item in the list.
 */
@Composable
fun CustomerPaymentListItem(
    detail: CustomerPaymentDetail,
    onCustomerClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCustomerClick(detail.customerId) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = detail.customerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f) // Allows text to take available space
                )
                Text(
                    text = "₹%.2f".format(detail.collectedAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary // Highlight amount
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Collected by: ${detail.agentName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) // Lighter text for agent
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CustomerPaymentListDialogPreview() {
    val sampleDetails = listOf(
        CustomerPaymentDetail("cust001", "Alice Smith", 1500.0, "Agent John", "bs1"),
        CustomerPaymentDetail("cust002", "Bob Johnson", 2200.0, "Admin Jane", "bs2"),
        CustomerPaymentDetail("cust003", "Charlie Brown", 500.0, "Agent John", "bs3")
    )
    MaterialTheme {
        CustomerPaymentListDialog(
            customerPaymentDetails = sampleDetails,
            onDismiss = {},
            onCustomerClick = { customerId -> println("Clicked $customerId") }
        )
    }
}
