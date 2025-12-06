package com.example.billingapp_2

import android.app.Application
import com.example.billingapp_2.data.cache.CacheManager
import com.example.billingapp_2.data.local.AppDatabase
import com.example.billingapp_2.data.repository.CustomerRepository
import com.example.billingapp_2.data.repository.CustomerRepositoryImpl
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class BillingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ⭐ CRITICAL: Initialize Firebase FIRST
        FirebaseApp.initializeApp(this)

        // Configure Firestore settings
        FirebaseFirestore.getInstance().firestoreSettings =
            FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()

        // Install App Check provider
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }

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
