
package com.example.billingapp.data.repository

import android.util.Log
import com.example.billingapp.data.cache.CacheManager
import com.example.billingapp.data.local.dao.CustomerDao
import com.example.billingapp.data.local.dao.HardwareDao
import com.example.billingapp.data.local.dao.TransactionDao
import com.example.billingapp.data.local.dao.UserDao
import com.example.billingapp.data.mappers.DataMappers.toCustomerEntities
import com.example.billingapp.data.mappers.DataMappers.toTransactionEntities
import com.example.billingapp.data.mappers.DataMappers.toUserEntities
import com.example.billingapp.model.CustomerModel
import com.example.billingapp.model.TransactionModel
import com.example.billingapp.model.UserModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

// Interface defining the contract for the data layer.
// ViewModels will depend on this interface, not the implementation.
interface CustomerRepository {
    suspend fun performFullDataSync(adminUid: String)
    suspend fun performIncrementalDataSync(adminUid: String, lastSyncTimestamp: Long)
    // Add other data-related function declarations here as needed.
}

// Implementation of the repository. It handles the logic of fetching from
// Firebase and caching into the local Room database.
class CustomerRepositoryImpl(
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
    private val hardwareDao: HardwareDao,
    private val userDao: UserDao,

) : CustomerRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun performFullDataSync(adminUid: String) {
        Log.d("CustomerRepository", "Starting full data sync for admin: $adminUid")

        // Fetch all customers
        val customersSnapshot = db.collection("customers")
            .whereEqualTo("adminUid", adminUid)
            .get()
            .await()
        val customers = customersSnapshot.toObjects(CustomerModel::class.java)
        customerDao.insertCustomers(customers.toCustomerEntities())

        // Fetch all transactions
        val transactionsSnapshot = db.collectionGroup("balanceSheet")
            .whereEqualTo("adminUid", adminUid)
            .get()
            .await()
        val transactions = transactionsSnapshot.toObjects(TransactionModel::class.java)
        transactionDao.insertTransactions(transactions.toTransactionEntities())

        // Fetch all users
        val usersSnapshot = db.collection("users")
            .whereEqualTo("adminUid", adminUid)
            .get()
            .await()
        val users = usersSnapshot.toObjects(UserModel::class.java)
        userDao.insertUsers(users.toUserEntities())

        Log.d("CustomerRepository", "Full sync completed: ${customers.size} customers, ${transactions.size} transactions")
    }

    override suspend fun performIncrementalDataSync(adminUid: String, lastSyncTimestamp: Long) {
        Log.d("CustomerRepository", "Starting incremental sync since: $lastSyncTimestamp")

        // Customers updated since timestamp
        val customersSnapshot = db.collection("customers")
            .whereEqualTo("adminUid", adminUid)
            .whereGreaterThan("lastUpdated", Timestamp(Date(lastSyncTimestamp)))
            .get()
            .await()
        if (!customersSnapshot.isEmpty) {
            val updatedCustomers = customersSnapshot.toObjects(CustomerModel::class.java)
            customerDao.insertCustomers(updatedCustomers.toCustomerEntities())
            Log.d("CustomerRepository", "Incremental sync: ${updatedCustomers.size} customers updated")
        }

        // Transactions created since timestamp
        val transactionsSnapshot = db.collectionGroup("balanceSheet")
            .whereEqualTo("adminUid", adminUid)
            .whereGreaterThan("date", Timestamp(Date(lastSyncTimestamp)))
            .get()
            .await()
        if (!transactionsSnapshot.isEmpty) {
            val newTransactions = transactionsSnapshot.toObjects(TransactionModel::class.java)
            transactionDao.insertTransactions(newTransactions.toTransactionEntities())
            Log.d("CustomerRepository", "Incremental sync: ${newTransactions.size} new transactions")
        }

        // Users updated since timestamp
        val usersSnapshot = db.collection("users")
            .whereEqualTo("adminUid", adminUid)
            .whereGreaterThan("lastUpdated", Timestamp(Date(lastSyncTimestamp)))
            .get()
            .await()
        if (!usersSnapshot.isEmpty) {
            val updatedUsers = usersSnapshot.toObjects(UserModel::class.java)
            userDao.insertUsers(updatedUsers.toUserEntities())
            Log.d("CustomerRepository", "Incremental sync: ${updatedUsers.size} users updated")
        }
    }
}
