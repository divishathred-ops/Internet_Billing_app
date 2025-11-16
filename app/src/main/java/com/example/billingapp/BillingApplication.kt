package com.example.billingapp

// In com.example.billingapp.BillingApplication.kt

import android.app.Application
import com.example.billingapp.data.cache.CacheManager
import com.example.billingapp.data.local.AppDatabase
import com.example.billingapp.data.repository.CustomerRepository
import com.example.billingapp.data.repository.CustomerRepositoryImpl

class BillingApplication : Application() {

    // Lazy initialization ensures these are created only when first needed
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    val cacheManager: CacheManager by lazy { CacheManager(this) }

    // The repository depends on the DAOs from the database
    val customerRepository: CustomerRepository by lazy {
        CustomerRepositoryImpl(
            customerDao = database.customerDao(),
            transactionDao = database.transactionDao(),
            hardwareDao = database.hardwareDao(),
            userDao = database.userDao()
        )
    }
}
