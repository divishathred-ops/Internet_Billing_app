package com.example.billingapp_2.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.billingapp_2.ui.auth.SignInScreen
import com.example.billingapp_2.ui.auth.SignUpScreen
import com.example.billingapp_2.ui.screens.home.*
import com.example.billingapp_2.viewmodel.*

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    viewModelFactory: ViewModelFactory,
) {
    val navController = rememberNavController()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    // This effect handles the navigation between auth and home screens.
    LaunchedEffect(isLoggedIn) {
        val authRoute = "auth"
        val homeRoute = "home"
        val currentRoute = navController.currentBackStackEntry?.destination?.route

        if (isLoggedIn) {
            // If logged in and not on the home screen, navigate to home.
            if (currentRoute?.startsWith(homeRoute) == false) {
                navController.navigate(homeRoute) {
                    popUpTo(authRoute) { inclusive = true }
                }
            }
        } else {
            // If not logged in and not on an auth screen, navigate to auth.
            if (currentRoute?.startsWith(authRoute) == false) {
                navController.navigate(authRoute) {
                    popUpTo(homeRoute) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "home" else "auth"
    ) {
        // Auth Graph
        composable("auth") {
            SignInScreen(navController, authViewModel)
        }
        composable("signUp") {
            SignUpScreen(navController, authViewModel)
        }

        // Main App Graph
        composable("home") {
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)
            val expenseViewModel: ExpenseViewModel = viewModel(factory = viewModelFactory)
            val paymentViewModel: PaymentCollectionViewModel = viewModel(factory = viewModelFactory)

            HomeScreen(
                navController = navController,
                authViewModel = authViewModel,
                customerViewModel = customerViewModel,
                agentViewModel = agentViewModel,
                expenseViewModel = expenseViewModel,
                paymentViewModel = paymentViewModel
            )
        }

        // ADDED: Route for the Customer List Screen
        composable("customers") {
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            CustomerScreen(
                navController = navController,
                authViewModel = authViewModel,
                customerViewModel = customerViewModel
            )
        }

        composable("addCustomer") {
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)
            AddCustomerScreen(navController, customerViewModel, agentViewModel)
        }

        composable(
            "customerDetail/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")
            if (customerId != null) {
                val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
                CustomerDetailScreen(navController, customerId, authViewModel, customerViewModel)
            }
        }

        composable(
            "balanceSheet/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")
            if (customerId != null) {
                val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
                BalanceSheetScreen(navController, customerId, authViewModel, customerViewModel)
            }
        }

        composable("agents") {
            val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)
            AgentsScreen(navController, agentViewModel, authViewModel)
        }

        composable(
            "agentPermissions/{agentId}",
            arguments = listOf(navArgument("agentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
            val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)
            AgentPermissionsScreen(
                navController = navController,
                agentViewModel = agentViewModel,
                agentUid = agentId
            )
        }

        composable("agentExpenses") {
            val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)
            AgentExpensesScreen(navController, agentViewModel)
        }

        composable(
            "agentExpenseDetail/{agentId}/{agentName}",
            arguments = listOf(
                navArgument("agentId") { type = NavType.StringType },
                navArgument("agentName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
            val agentName = backStackEntry.arguments?.getString("agentName") ?: ""
            val expenseViewModel: ExpenseViewModel = viewModel(factory = viewModelFactory)
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            AgentExpenseDetailScreen(
                navController = navController,
                agentId = agentId,
                agentName = agentName,
                expenseViewModel = expenseViewModel,
                customerViewModel = customerViewModel,
                authViewModel = authViewModel
            )
        }

        composable("profileSettings") {
            ProfileSettingsScreen(navController)
        }
        composable(
            "customers?isViewOnlyMode={isViewOnlyMode}",
            arguments = listOf(navArgument("isViewOnlyMode") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val isViewOnlyMode = backStackEntry.arguments?.getBoolean("isViewOnlyMode") ?: false
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            CustomerScreen(
                navController = navController,
                authViewModel = authViewModel,
                customerViewModel = customerViewModel,
                isViewOnlyMode = isViewOnlyMode // Pass the argument to the screen
            )
        }
        composable(
            "monthlyCollection?autoSelect={autoSelect}&start={start}&end={end}",
            arguments = listOf(
                navArgument("autoSelect") { type = NavType.StringType; nullable = true },
                navArgument("start") { type = NavType.LongType; defaultValue = -1L },
                navArgument("end") { type = NavType.LongType; defaultValue = -1L }
            ))
        {
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            MonthlyCollectionScreen(navController, customerViewModel, authViewModel)
        }
        composable("importCustomers") {
            val customerViewModel: CustomerViewModel = viewModel(factory = viewModelFactory)
            ImportCustomersScreen(navController, customerViewModel)
        }
    }
}
