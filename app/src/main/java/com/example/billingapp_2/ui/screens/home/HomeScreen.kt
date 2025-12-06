package com.example.billingapp_2.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.billingapp_2.viewmodel.*

@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    customerViewModel: CustomerViewModel,
    agentViewModel: AgentViewModel,
    expenseViewModel: ExpenseViewModel,
    paymentViewModel: PaymentCollectionViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Customers", "Summary")

    // If the user is on the "Summary" tab and presses back,
    // it will switch back to the "Customers" tab instead of closing the app.
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Filled.People, contentDescription = title)
                                1 -> Icon(Icons.Filled.Assessment, contentDescription = title)
                            }
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        // The padding from the top is removed, while the bottom padding from the Scaffold
        // is still applied to accommodate the NavigationBar.
        Column(Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            when (selectedTab) {
                0 -> CustomerScreen(
                    navController,
                    authViewModel,
                    customerViewModel,
                    isViewOnlyMode = false // Use the full-featured screen for the main tab
                )
                1 -> SummaryScreen(
                    navController = navController,
                    authViewModel = authViewModel,
                    customerViewModel = customerViewModel,
                    expenseViewModel = expenseViewModel,
                    paymentCollectionViewModel = paymentViewModel,
                    onBack = { selectedTab = 0 } // Pass the lambda to handle back navigation
                )
            }
        }
    }
}
