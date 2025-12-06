package com.example.billingapp_2.viewmodel
// In com.example.billingapp.viewmodel.ViewModelFactory.kt
// In com.example.billingapp.viewmodel.ViewModelFactory.kt

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.billingapp_2.BillingApplication

class ViewModelFactory(
    private val application: Application,
    private val authViewModel: AuthViewModel // Keep this if other VMs need it
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Cast the application to your custom BillingApplication to access its properties
        val billingApp = application as BillingApplication
        val database = billingApp.database
        val cacheManager = billingApp.cacheManager
        val customerRepository = billingApp.customerRepository

        return when {
            // How to create AuthViewModel
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(application, database, cacheManager, customerRepository) as T
            }

            // How to create CustomerViewModel
            modelClass.isAssignableFrom(CustomerViewModel::class.java) -> {
                CustomerViewModel(application,authViewModel, database, cacheManager) as T
            }

            // How to create AgentViewModel, ExpenseViewModel, etc.
            modelClass.isAssignableFrom(AgentViewModel::class.java) -> {
                AgentViewModel(authViewModel) as T
            }
            modelClass.isAssignableFrom(ExpenseViewModel::class.java) -> {
                ExpenseViewModel(authViewModel, database, cacheManager) as T
            }
            modelClass.isAssignableFrom(PaymentCollectionViewModel::class.java) -> {
                PaymentCollectionViewModel(authViewModel, customerRepository) as T
            }
            // Add other ViewModels here...

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
