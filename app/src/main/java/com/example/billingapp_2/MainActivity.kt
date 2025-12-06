package com.example.billingapp_2
// In com.example.billingapp.MainActivity.kt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.billingapp_2.navigation.AppNavigation
import com.example.billingapp_2.ui.theme.BillingAppTheme
import com.example.billingapp_2.viewmodel.AuthViewModel
import com.example.billingapp_2.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {

    // Lazily get the application instance
    private val billingApplication: BillingApplication by lazy { application as BillingApplication }

    // Use a factory to create the AuthViewModel instance with all its dependencies
    private val authViewModel: AuthViewModel by viewModels {
        ViewModelFactory(billingApplication, AuthViewModel(
            billingApplication,
            billingApplication.database,
            billingApplication.cacheManager,
            billingApplication.customerRepository
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize cache manager actions on app start
        billingApplication.cacheManager.saveAppOpenTimestamp()
        billingApplication.cacheManager.initializeCache()

        setContent {
            BillingAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create the ViewModelFactory, which can now build any ViewModel
                    val viewModelFactory = ViewModelFactory(
                        application = billingApplication,
                        authViewModel = authViewModel
                    )

                    // AppNavigation remains the same
                    AppNavigation(
                        authViewModel = authViewModel,
                        viewModelFactory = viewModelFactory,
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Save the app close timestamp when the app goes into the background
        billingApplication.cacheManager.saveAppCloseTimestamp()
    }
}
