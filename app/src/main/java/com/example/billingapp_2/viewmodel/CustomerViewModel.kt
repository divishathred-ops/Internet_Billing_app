package com.example.billingapp_2.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope
import com.example.billingapp_2.data.cache.CacheManager
import com.example.billingapp_2.data.local.AppDatabase
import com.example.billingapp_2.data.mappers.DataMappers.toCustomerModel
import com.example.billingapp_2.data.mappers.DataMappers.toCustomerModels
import com.example.billingapp_2.data.mappers.DataMappers.toHardwareModel
import com.example.billingapp_2.data.mappers.DataMappers.toCustomerEntities
import com.example.billingapp_2.data.mappers.DataMappers.toTransactionEntities
import com.example.billingapp_2.data.mappers.DataMappers.toUserEntities
import com.example.billingapp_2.data.mappers.DataMappers.toLocalEntity
import com.example.billingapp_2.data.mappers.DataMappers.toTransactionModel
import com.example.billingapp_2.data.mappers.DataMappers.toUserModel
import com.example.billingapp_2.data.mappers.DataMappers.toUserModels
import com.example.billingapp_2.model.*
import com.example.billingapp_2.utils.CsvParser
import com.example.billingapp_2.model.ImportResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class CustomerViewModel(
    application: Application,
    private val authViewModel: AuthViewModel,
    private val database: AppDatabase,
    private val cacheManager: CacheManager
)  : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val _databaseChangesTrigger = MutableStateFlow(0L)

    // --- StateFlows for UI ---
    private val appContext = getApplication<Application>()
    private val _selectedCustomer = MutableStateFlow<CustomerModel?>(null)
    val selectedCustomer: StateFlow<CustomerModel?> = _selectedCustomer.asStateFlow()

    private val _selectedCustomerTransactions = MutableStateFlow<List<TransactionModel>>(emptyList())
    val selectedCustomerTransactions: StateFlow<List<TransactionModel>> = _selectedCustomerTransactions.asStateFlow()

    private val _selectedCustomerHardware = MutableStateFlow<HardwareDetailsModel?>(null)
    val selectedCustomerHardware: StateFlow<HardwareDetailsModel?> = _selectedCustomerHardware.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _agentNamesFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val agentNamesFlow: StateFlow<Map<String, String>> = _agentNamesFlow.asStateFlow()

    private val _collectionDetails = MutableStateFlow<List<MonthlyCollectionData>>(emptyList())
    val collectionDetails: StateFlow<List<MonthlyCollectionData>> = _collectionDetails.asStateFlow()

    private val _collectionStartDate = MutableStateFlow<Date?>(null)
    private val _collectionEndDate = MutableStateFlow<Date?>(null)
    private val _viewingAgentId = MutableStateFlow<String?>(null)

    // --- Session-persistent listeners (only during active session) ---
    private var customersListener: ListenerRegistration? = null
    private var transactionsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var singleCustomerTransactionsListener: ListenerRegistration? = null

    // **FIXED**: This flow now reacts to changes in adminUid.
    // It will re-query the database when the user logs in.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerModel>> = authViewModel.adminUid
        .flatMapLatest { adminUid ->
            if (adminUid.isNullOrEmpty()) {
                // If no adminUid, return a flow with an empty list
                flowOf(emptyList())
            } else {
                // Otherwise, get the actual customer flow from the database
                database.customerDao().getAllCustomersFlow(adminUid)
                    .map { entities -> entities.toCustomerModels() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // Track if listeners are already set up to prevent duplicates
    private var listenersInitialized = false

    init {
        // Initialize data loading when auth is ready
        viewModelScope.launch {
            authViewModel.isAuthReady.filter { it }.collect {
                val adminUid = authViewModel.adminUid.value ?: return@collect
                initializeCustomerData(adminUid)
            }
        }


        // Collection details calculation
        setupCollectionDetailsFlow()
    }

    // **CRITICAL FIX**: Proper initialization with cache-first strategy
    private suspend fun initializeCustomerData(adminUid: String) {
        Log.d("CustomerViewModel", "Initializing customer data for admin: $adminUid")

        try {
            // Always load from local database first for immediate UI response
            loadAgentNamesFromLocal(adminUid)

            // Check if we need full sync or incremental sync
            val context = getApplication<Application>()

            if (cacheManager.shouldPerformFullSync(context)) {
                Log.d("CustomerViewModel", "Performing full sync - fresh install or small DB")
                performFullDataSync(adminUid)
                cacheManager.markSyncCompleted()
            } else if (cacheManager.shouldPerformIncrementalSync()) {
                Log.d("CustomerViewModel", "Performing incremental sync - app was closed")
                val lastCloseTime = cacheManager.getAppCloseTimestamp()
                performIncrementalDataSync(adminUid, lastCloseTime)
                cacheManager.markSyncCompleted()
            } else {
                Log.d("CustomerViewModel", "Skipping sync - using cached data")
            }

            // Setup real-time listeners for live updates during session
            setupRealTimeListeners(adminUid)

        } catch (e: Exception) {
            Log.e("CustomerViewModel", "Error initializing customer data", e)
        }
    }


    private suspend fun performFullDataSync(adminUid: String) {
        Log.d("CustomerViewModel", "Performing full data sync")
        _isLoading.value = true

        try {
            // Fetch all customers
            val customersSnapshot = db.collection("customers")
                .whereEqualTo("adminUid", adminUid)
                .get()
                .await()

            val customers = customersSnapshot.toObjects(CustomerModel::class.java)

            // Cache customers locally
            database.customerDao().insertCustomers(customers.toCustomerEntities())

            // Fetch all transactions using collection group query
            val transactionsSnapshot = db.collectionGroup("balanceSheet")
                .whereEqualTo("adminUid", adminUid)
                .get()
                .await()

            val transactions = transactionsSnapshot.toObjects(TransactionModel::class.java)
            database.transactionDao().insertTransactions(transactions.toTransactionEntities())

            // Fetch all users
            val usersSnapshot = db.collection("users")
                .whereEqualTo("adminUid", adminUid)
                .get()
                .await()

            val users = usersSnapshot.toObjects(UserModel::class.java)
            database.userDao().insertUsers(users.toUserEntities())

            // Update agent names
            val agentNames = users.associate { it.uid to it.name }.toMutableMap()
            agentNames[adminUid] = authViewModel.userModel.value?.name ?: "Admin"
            _agentNamesFlow.value = agentNames

            // **CRITICAL**: Mark sync as completed and cache as valid
            cacheManager.markSyncCompleted()
            cacheManager.saveLastCustomerSync()
            cacheManager.saveLastTransactionSync()
            cacheManager.saveLastUserSync()

            // Trigger initial calculations
            _databaseChangesTrigger.value = System.currentTimeMillis()

            Log.d("CustomerViewModel", "Full sync completed: ${customers.size} customers, ${transactions.size} transactions")

            // Setup real-time listeners ONCE after sync
            setupRealTimeListeners(adminUid)

        } catch (e: Exception) {
            Log.e("CustomerViewModel", "Full data sync failed", e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun performIncrementalDataSync(adminUid: String, lastCloseTime: Long) {
        Log.d("CustomerViewModel", "Performing incremental data sync")

        try {
            val closeTimestamp = cacheManager.getAppCloseTimestamp()

            // Fetch customers updated since app close
            val customersSnapshot = db.collection("customers")
                .whereEqualTo("adminUid", adminUid)
                .whereGreaterThan("lastUpdated", Timestamp(Date(closeTimestamp)))
                .get()
                .await()

            if (!customersSnapshot.isEmpty) {
                val updatedCustomers = customersSnapshot.toObjects(CustomerModel::class.java)
                database.customerDao().insertCustomers(updatedCustomers.toCustomerEntities())
                Log.d("CustomerViewModel", "Incremental sync: ${updatedCustomers.size} customers updated")
            }

            // Fetch new transactions since app close
            val transactionsSnapshot = db.collectionGroup("balanceSheet")
                .whereEqualTo("adminUid", adminUid)
                .whereGreaterThan("date", Timestamp(Date(closeTimestamp)))
                .get()
                .await()

            if (!transactionsSnapshot.isEmpty) {
                val newTransactions = transactionsSnapshot.toObjects(TransactionModel::class.java)
                database.transactionDao().insertTransactions(newTransactions.toTransactionEntities())

                Log.d("CustomerViewModel", "Incremental sync: ${newTransactions.size} new transactions")
            }

            cacheManager.markSyncCompleted()

            // Setup real-time listeners
            setupRealTimeListeners(adminUid)

        } catch (e: Exception) {
            Log.e("CustomerViewModel", "Incremental sync failed", e)
            // Setup listeners anyway with cached data
            setupRealTimeListeners(adminUid)
        }
    }

    // **CRITICAL FIX**: Set up listeners ONCE per session
    private fun setupRealTimeListeners(adminUid: String) {
        if (listenersInitialized) {
            Log.d("CustomerViewModel", "Listeners already initialized, skipping")
            return
        }

        Log.d("CustomerViewModel", "Setting up real-time listeners ONCE for session")

        // **OPTIMIZED**: Enhanced transaction listener with minimal processing
        transactionsListener = db.collectionGroup("balanceSheet")
            .whereEqualTo("adminUid", adminUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CustomerViewModel", "Transaction listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch {
                        // **OPTIMIZED**: Process only document changes, not all documents
                        var hasChanges = false

                        for (change in snapshot.documentChanges) {
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                    val transaction = change.document.toObject(TransactionModel::class.java)
                                    database.transactionDao().insertTransaction(transaction.toLocalEntity())
                                    hasChanges = true
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    Log.d("CustomerViewModel", "Transaction removed: ${change.document.id}")
                                    database.transactionDao().deleteTransaction(change.document.id)
                                    hasChanges = true
                                }
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val transaction = change.document.toObject(TransactionModel::class.java)
                                    database.transactionDao().insertTransaction(transaction.toLocalEntity())
                                    hasChanges = true
                                }
                            }
                        }

                        // **OPTIMIZED**: Only trigger recalculation if there were actual changes
                        if (hasChanges) {
                            _databaseChangesTrigger.value = System.currentTimeMillis()
                            Log.d("CustomerViewModel", "Transaction changes processed, triggering recalculation")
                        }
                    }
                }
            }

        // **OPTIMIZED**: Simplified customer listener
        customersListener = db.collection("customers")
            .whereEqualTo("adminUid", adminUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CustomerViewModel", "Customer listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    viewModelScope.launch {
                        val customers = snapshot.toObjects(CustomerModel::class.java)
                        database.customerDao().insertCustomers(customers.toCustomerEntities())
                    }
                }
            }

        // **OPTIMIZED**: Simplified user listener
        usersListener = db.collection("users")
            .whereEqualTo("adminUid", adminUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CustomerViewModel", "Users listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    viewModelScope.launch {
                        val users = snapshot.toObjects(UserModel::class.java)
                        database.userDao().insertUsers(users.toUserEntities())
                        val agentNames = users.associate { it.uid to it.name }.toMutableMap()
                        agentNames[adminUid] = authViewModel.userModel.value?.name ?: "Admin"
                        _agentNamesFlow.value = agentNames
                    }
                }
            }

        listenersInitialized = true
        Log.d("CustomerViewModel", "Real-time listeners initialized successfully")
    }

    fun selectCustomer(customerId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // --- Step 1: ALWAYS OBSERVE THE LOCAL DATABASE FIRST ---
            // This ensures the UI is always powered by the local Room database for an
            // instant, offline-first experience. These flows will automatically
            // update the UI if new data is inserted in Step 2.
            launch {
                database.customerDao().getCustomerByIdFlow(customerId).collect { entity ->
                    _selectedCustomer.value = entity?.toCustomerModel()
                }
            }
            launch {
                database.transactionDao().getCustomerTransactionsFlow(customerId).collect { entities ->
                    val transactions = entities.map { it.toTransactionModel() }
                        .sortedByDescending { it.date.toDate() }
                    _selectedCustomerTransactions.value = transactions
                }
            }
            launch {
                database.hardwareDao().getHardwareByCustomerIdFlow(customerId).collect { entity ->
                    _selectedCustomerHardware.value = entity?.toHardwareModel()
                }
            }

            // --- Step 2: INTELLIGENTLY SYNC WITH FIRESTORE ---
            try {
                // **NEW OPTIMIZATION**: Check if a global sync happened recently.
                // If a full or incremental sync ran in the last minute, we can assume
                // the local data is fresh and skip this specific Firestore check.
                // Note: This assumes your CacheManager can provide the last sync timestamp.
                val lastGlobalSyncTime = cacheManager.getLastSyncTimestamp()
                val oneMinuteAgo = System.currentTimeMillis() - 60 * 1000

                if (lastGlobalSyncTime > oneMinuteAgo) {
                    Log.d("CustomerViewModel", "A global sync ran recently. Skipping individual Firestore check for customer $customerId.")
                    return@launch // Exit the try block; finally will still run.
                }

                // Get the timestamp of the very last transaction we have stored locally.
                val lastLocalTimestamp = database.transactionDao().getLatestTransactionTimestamp(customerId)

                if (lastLocalTimestamp == null) {
                    // --- SCENARIO A: NO LOCAL DATA ---
                    // If we have no transactions locally for this customer, fetch all of them.
                    Log.d("CustomerViewModel", "No local transactions for $customerId. Fetching all from Firestore.")
                    val allTransactionsSnapshot = db.collection("customers").document(customerId)
                        .collection("balanceSheet").get().await()

                    if (!allTransactionsSnapshot.isEmpty) {
                        val transactions = allTransactionsSnapshot.toObjects(TransactionModel::class.java)
                        // Save all fetched transactions to the local database.
                        database.transactionDao().insertTransactions(transactions.toTransactionEntities())
                    }
                } else {
                    // --- SCENARIO B: LOCAL DATA EXISTS ---
                    // If we have local data, we only fetch transactions that are NEWER than our latest one.
                    Log.d("CustomerViewModel", "Local data exists for $customerId. Checking for newer transactions since timestamp: $lastLocalTimestamp")

                    val newTransactionsSnapshot = db.collection("customers").document(customerId)
                        .collection("balanceSheet")
                        .whereGreaterThan("date", Timestamp(Date(lastLocalTimestamp)))
                        .get()
                        .await()

                    if (!newTransactionsSnapshot.isEmpty) {
                        // If new documents are found, save them to the local database.
                        val newTransactions = newTransactionsSnapshot.toObjects(TransactionModel::class.java)
                        database.transactionDao().insertTransactions(newTransactions.toTransactionEntities())
                        Log.d("CustomerViewModel", "SUCCESS: Fetched and cached ${newTransactions.size} new transactions.")
                    } else {
                        // If no new documents are found, we do nothing. No unnecessary reads or writes.
                        Log.d("CustomerViewModel", "Local data is already up-to-date. No sync needed.")
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "Failed to sync transactions for customer $customerId", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAllListeners() {
        customersListener?.remove()
        customersListener = null

        transactionsListener?.remove()
        transactionsListener = null

        usersListener?.remove()
        usersListener = null

        singleCustomerTransactionsListener?.remove()
        singleCustomerTransactionsListener = null

        Log.d("CustomerViewModel", "All Firebase listeners cleared")
    }


    fun resetCustomerSelection() {
        // **FIX**: Detach the listener when the screen is left
        singleCustomerTransactionsListener?.remove()
        singleCustomerTransactionsListener = null
        _selectedCustomer.value = null
        _selectedCustomerTransactions.value = emptyList()
        _selectedCustomerHardware.value = null
        Log.d("CustomerViewModel", "Customer selection reset and transaction listener detached.")
    }

    // **SIMPLIFIED**: This function only needs to load agent names now.
    // The customer list is handled by the reactive `customers` flow.
    private suspend fun loadAgentNamesFromLocal(adminUid: String) {
        try {
            Log.d("CustomerViewModel", "Loading agent names from local database for admin: $adminUid")

            val userEntities = database.userDao().getUsersByAdmin(adminUid)
            val agentNames = userEntities.toUserModels().associate { it.uid to it.name }.toMutableMap()

            val adminEntity = database.userDao().getUserById(adminUid)
            adminEntity?.let { agentNames[adminUid] = it.toUserModel().name }

            _agentNamesFlow.value = agentNames

            Log.d("CustomerViewModel", "Successfully refreshed agent names from cache.")
        } catch (e: Exception) {
            Log.e("CustomerViewModel", "Error loading agent names from local database", e)
        }
    }

    // Add function to get transaction display info
    fun getTransactionDisplayInfo(transaction: TransactionModel): TransactionDisplayInfo {
        return when (transaction.type) {
            "collectedPayment" -> TransactionDisplayInfo(
                prefix = "-",
                color = Color(0xFFFF0000), // Red
                showSymbol = true
            )
            "generatedDue", "initial" -> TransactionDisplayInfo(
                prefix = "+",
                color = Color(0xFF008000), // Green
                showSymbol = true
            )
            "balanceUpdate" -> TransactionDisplayInfo(
                prefix = "",
                color = Color(0xFF808080), // Gray
                showSymbol = false
            )
            else -> TransactionDisplayInfo(
                prefix = "",
                color = Color(0xFF000000), // Black
                showSymbol = false
            )
        }
    }

    data class TransactionDisplayInfo(
        val prefix: String,
        val color: Color,
        val showSymbol: Boolean
    )

    // --- Customer Operations (Firebase + Local Cache) ---
    fun addCustomer(customer: CustomerModel, initialBalance: Double, createdBy: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val adminUid = authViewModel.adminUid.value ?: throw IllegalStateException("Admin UID not found")
                val customerWithAdmin = customer.copy(adminUid = adminUid)
                val customerRef = db.collection("customers").document(customer.id)
                val hardwareRef = customerRef.collection("hardwareDetails").document("details")

                db.runBatch { batch ->
                    batch.set(customerRef, customerWithAdmin)
                    batch.set(hardwareRef, HardwareDetailsModel(stbNumber = customer.stbNumber))

                    if (initialBalance != 0.0) {
                        val transactionRef = customerRef.collection("balanceSheet").document()
                        val initialTx = TransactionModel(
                            id = transactionRef.id,
                            customerId = customer.id,
                            customerName = customer.name,
                            adminUid = adminUid,
                            amount = initialBalance,
                            type = "initial",
                            description = "Initial balance on account creation",
                            createdBy = createdBy,
                            date = Timestamp.now()
                        )
                        batch.set(transactionRef, initialTx)
                    }
                }.await()

                // Update local cache immediately
                database.customerDao().insertCustomer(customerWithAdmin.toLocalEntity())
                database.hardwareDao().insertHardware(
                    HardwareDetailsModel(stbNumber = customer.stbNumber).toLocalEntity(customer.id)
                )

                if (initialBalance != 0.0) {
                    val initialTx = TransactionModel(
                        id = UUID.randomUUID().toString(),
                        customerId = customer.id,
                        customerName = customer.name,
                        adminUid = adminUid,
                        amount = initialBalance,
                        type = "initial",
                        description = "Initial balance on account creation",
                        createdBy = createdBy,
                        date = Timestamp.now()
                    )
                    database.transactionDao().insertTransaction(initialTx.toLocalEntity())
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "addCustomer failed", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun collectPayment(customerId: String, customerName: String, amount: Double, createdBy: String, paymentDate: Timestamp, onResult: (Boolean, String?) -> Unit) {
        val adminUid = authViewModel.adminUid.value ?: run {
            onResult(false, "User not properly authenticated.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val customerRef = db.collection("customers").document(customerId)
                val transactionRef = customerRef.collection("balanceSheet").document()

                val transaction = TransactionModel(
                    id = transactionRef.id,
                    customerId = customerId,
                    customerName = customerName,
                    amount = amount,
                    type = "collectedPayment",
                    date = paymentDate,
                    description = "Payment collected",
                    createdBy = createdBy,
                    adminUid = adminUid
                )

                // Update Firebase
                db.runTransaction { t ->
                    t.set(transactionRef, transaction)
                    t.update(customerRef, "balance", FieldValue.increment(-amount))
                    t.update(customerRef, "lastPaymentDate", paymentDate)
                }.await()

                // Update local cache immediately
                database.transactionDao().insertTransaction(transaction.toLocalEntity())

                val customerEntity = database.customerDao().getCustomerById(customerId)
                customerEntity?.let { customer ->
                    val updatedBalance = customer.balance - amount
                    database.customerDao().updateCustomerBalance(customerId, updatedBalance)
                    database.customerDao().updateLastPaymentDate(customerId, paymentDate.toDate().time)
                }

                // Refresh selected customer if viewing
                if (_selectedCustomer.value?.id == customerId) {
                    selectCustomer(customerId)
                }

                onResult(true, null)
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "collectPayment failed", e)
                onResult(false, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateHardwareDetails(customerId: String, newStbNumber: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Update Firebase
                db.collection("customers").document(customerId)
                    .update("stbNumber", newStbNumber)
                    .await()

                // Update local cache
                database.customerDao().updateStbNumber(customerId, newStbNumber)
                database.hardwareDao().updateStbNumber(customerId, newStbNumber)

                // Refresh selected customer if viewing
                if (_selectedCustomer.value?.id == customerId) {
                    selectCustomer(customerId)
                }

                callback(true)
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "Failed to update hardware details", e)
                callback(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Other Operations ---
    fun generateBill(customerId: String, customerName: String, amount: Double, description: String, billPeriod: String, agentId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val adminUid = authViewModel.adminUid.value ?: throw IllegalStateException("Admin UID not set")
                val customerRef = db.collection("customers").document(customerId)
                val transactionRef = customerRef.collection("balanceSheet").document()

                val billTransaction = TransactionModel(
                    id = transactionRef.id,
                    customerId = customerId,
                    customerName = customerName,
                    adminUid = adminUid,
                    amount = amount,
                    type = "generatedDue",
                    description = description,
                    billPeriod = billPeriod,
                    createdBy = agentId,
                    date = Timestamp.now()
                )

                // Update Firebase
                db.runTransaction { transaction ->
                    transaction.set(transactionRef, billTransaction)
                    transaction.update(customerRef, "balance", FieldValue.increment(amount))
                }.await()

                // Update local cache
                database.transactionDao().insertTransaction(billTransaction.toLocalEntity())

                val customerEntity = database.customerDao().getCustomerById(customerId)
                customerEntity?.let { customer ->
                    val updatedBalance = customer.balance + amount
                    database.customerDao().updateCustomerBalance(customerId, updatedBalance)
                }

                // Refresh selected customer if viewing
                if (_selectedCustomer.value?.id == customerId) {
                    selectCustomer(customerId)
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "generateBill failed", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateCustomerDetails(customerId: String, name: String, phone: String, area: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updates = mapOf(
                    "name" to name,
                    "phone" to phone,
                    "area" to area.uppercase()
                )

                // Update Firebase
                db.collection("customers").document(customerId).update(updates).await()

                // Update local cache
                val customerEntity = database.customerDao().getCustomerById(customerId)
                customerEntity?.let { customer ->
                    val updatedCustomer = customer.copy(
                        name = name,
                        phone = phone,
                        area = area.uppercase(),
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                    database.customerDao().updateCustomer(updatedCustomer)
                }

                // Refresh selected customer if viewing
                if (_selectedCustomer.value?.id == customerId) {
                    selectCustomer(customerId)
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "updateCustomerDetails failed", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteCustomer(customerId: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val customerRef = db.collection("customers").document(customerId)

                // FIXED: First, delete all transactions in the balanceSheet subcollection
                val balanceSheetQuery = customerRef.collection("balanceSheet").get().await()

                // Use batch operation for consistency
                val batch = db.batch()

                // Delete all transaction documents in the subcollection
                for (document in balanceSheetQuery.documents) {
                    batch.delete(document.reference)
                }

                // Delete hardware details subcollection
                val hardwareRef = customerRef.collection("hardwareDetails").document("details")
                batch.delete(hardwareRef)

                // Finally, delete the customer document
                batch.delete(customerRef)

                // Commit the batch
                batch.commit().await()

                // Delete from local cache
                database.customerDao().deleteCustomer(customerId)
                database.transactionDao().deleteAllTransactionsForCustomer(customerId)
                database.hardwareDao().deleteHardwareForCustomer(customerId)

                // Trigger StateFlow recalculations
                _databaseChangesTrigger.value = System.currentTimeMillis()

                onComplete(true, null)
                Log.d("CustomerViewModel", "Customer and all subcollections deleted successfully")
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "deleteCustomer failed", e)
                onComplete(false, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changeBalance(customerId: String, newBalance: Double, agentId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val adminUid = authViewModel.adminUid.value ?: throw IllegalStateException("Admin UID not set")
                val customerRef = db.collection("customers").document(customerId)
                val transactionRef = customerRef.collection("balanceSheet").document()

                // Get current balance from local cache
                val customerEntity = database.customerDao().getCustomerById(customerId)
                val currentBalance = customerEntity?.balance ?: 0.0
                val balanceDifference = newBalance - currentBalance

                val balanceUpdateTransaction = TransactionModel(
                    id = transactionRef.id,
                    customerId = customerId,
                    customerName = customerEntity?.name ?: "",
                    adminUid = adminUid,
                    amount = balanceDifference,
                    type = "balanceUpdate",
                    description = "Manual balance update.",
                    createdBy = agentId,
                    date = Timestamp.now()
                )

                // Update Firebase
                db.runTransaction { transaction ->
                    transaction.set(transactionRef, balanceUpdateTransaction)
                    transaction.update(customerRef, "balance", newBalance)
                }.await()

                // Update local cache
                database.transactionDao().insertTransaction(balanceUpdateTransaction.toLocalEntity())
                database.customerDao().updateCustomerBalance(customerId, newBalance)

                // Refresh selected customer if viewing
                if (_selectedCustomer.value?.id == customerId) {
                    selectCustomer(customerId)
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "changeBalance failed", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // In CustomerViewModel.kt - Replace existing deleteTransaction
    fun deleteTransaction(customerId: String, transaction: TransactionModel, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (authViewModel.userRole.value != "admin") {
                    throw SecurityException("Only admins can delete transactions.")
                }

                val customerRef = db.collection("customers").document(customerId)
                val transactionRef = customerRef.collection("balanceSheet").document(transaction.id)

                val balanceChange = when (transaction.type) {
                    "collectedPayment", "initial" -> transaction.amount
                    "generatedDue" -> -transaction.amount
                    "balanceUpdate" -> -transaction.amount
                    else -> 0.0
                }

                // Update Firebase
                db.runTransaction { t ->
                    if (balanceChange != 0.0) {
                        t.update(customerRef, "balance", FieldValue.increment(balanceChange))
                    }
                    t.delete(transactionRef)
                }.await()

                // **OPTIMIZED:** Update local cache without extra reads
                database.transactionDao().deleteTransaction(transaction.id)

                if (balanceChange != 0.0) {
                    val customerEntity = database.customerDao().getCustomerById(customerId)
                    customerEntity?.let { customer ->
                        val updatedBalance = customer.balance + balanceChange
                        database.customerDao().updateCustomerBalance(customerId, updatedBalance)
                    }
                }

                // **OPTIMIZED:** Single trigger instead of multiple refreshes
                _databaseChangesTrigger.value = System.currentTimeMillis()

                // Refresh selected customer if viewing
                if (_selectedCustomer.value?.id == customerId) {
                    selectCustomer(customerId)
                }

                onComplete(true, null)
                Log.d("CustomerViewModel", "Transaction deleted successfully, triggering recalculations")
            } catch (e: Exception) {
                Log.e("CustomerViewModel", "deleteTransaction failed", e)
                onComplete(false, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Summary calculations (from local database) ---
    private val summaryTriggers = combine(
        customers, // Use the reactive customers flow
        _databaseChangesTrigger, // NEW: Include database changes trigger
        authViewModel.userRole,
        authViewModel.currentUser,
        authViewModel.adminUid
    ) { customers, changesTrigger, role, user, adminUid ->
        SummaryTrigger(customers, changesTrigger, role, user, adminUid)
    }

    val totalTodayCollectionAmount: StateFlow<Double> = summaryTriggers
        .flatMapLatest { trigger ->
            flow {
                val (role, user, adminUid) = Triple(trigger.role, trigger.user, trigger.adminUid)
                if (user == null || adminUid.isNullOrEmpty()) {
                    emit(0.0)
                    return@flow
                }
                val (start, end) = getTodayRange()
                val transactions = database.transactionDao().getPaymentTransactionsBetween(adminUid, start.time, end.time)
                val total = when (role) {
                    "admin" -> transactions.sumOf { it.amount }
                    "agent" -> transactions.filter { it.createdBy == user.uid }.sumOf { it.amount }
                    else -> 0.0
                }
                emit(total)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalMonthlyCollectionAmount: StateFlow<Double> = summaryTriggers
        .flatMapLatest { trigger ->
            flow {
                val (role, user, adminUid) = Triple(trigger.role, trigger.user, trigger.adminUid)
                if (user == null || adminUid.isNullOrEmpty()) {
                    emit(0.0)
                    return@flow
                }
                val (start, end) = getCurrentMonthRange()
                val transactions = database.transactionDao().getPaymentTransactionsBetween(adminUid, start.time, end.time)
                val total = when (role) {
                    "admin" -> transactions.sumOf { it.amount }
                    "agent" -> transactions.filter { it.createdBy == user.uid }.sumOf { it.amount }
                    else -> 0.0
                }
                emit(total)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val totalCustomers: StateFlow<Int> = combine(
        customers, // Use the reactive customers flow
        authViewModel.userRole,
        authViewModel.assignedAreas
    ) { customers, role, assignedAreas ->
        if (role == "admin") {
            customers.size
        } else {
            customers.count { it.area in assignedAreas }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUnpaid: StateFlow<Double> = customers.map { customerList -> // Use the reactive customers flow
        customerList.sumOf { if (it.balance > 0) it.balance else 0.0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val allBillingAreas: StateFlow<Set<String>> = combine(
        customers, // Use the reactive customers flow
        authViewModel.userRole,
        authViewModel.assignedAreas
    ) { customers, role, assignedAreas ->
        val allAreasFromCustomers = customers.map { it.area.uppercase() }.toSet()
        if (role == "admin") {
            allAreasFromCustomers
        } else {
            allAreasFromCustomers.intersect(assignedAreas.map { it.uppercase() }.toSet())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val viewingAgentMonthlyCollection: StateFlow<Double> = combine(
        customers, // Use the reactive customers flow
        _viewingAgentId
    ) { _, agentId ->
        if (agentId == null) return@combine 0.0

        val (start, end) = getCurrentMonthRange()
        val transactions = database.transactionDao().getAgentCollectionsBetween(agentId, start.time, end.time)
        transactions.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private fun setupCollectionDetailsFlow() {
        viewModelScope.launch {
            combine(
                customers, // Use the reactive customers flow
                _collectionStartDate,
                _collectionEndDate,
                authViewModel.currentUser,
                authViewModel.userRole
            ) { customers, startDate, endDate, user, role ->
                if (startDate == null || endDate == null || user == null) {
                    emptyList<MonthlyCollectionData>()
                } else {
                    val adminUid = authViewModel.adminUid.value ?: ""
                    if (adminUid.isEmpty()) {
                        emptyList<MonthlyCollectionData>()
                    } else {
                        val transactions = database.transactionDao().getPaymentTransactionsBetween(
                            adminUid, startDate.time, endDate.time
                        )

                        val relevantTransactions = when (role) {
                            "agent" -> transactions.filter { it.createdBy == user.uid }
                            else -> transactions
                        }

                        val customerMap = customers.associateBy { it.id }
                        relevantTransactions.mapNotNull { transaction ->
                            customerMap[transaction.customerId]?.let { customer ->
                                MonthlyCollectionData(
                                    customerId = customer.id,
                                    customerName = customer.name,
                                    stbNumber = customer.stbNumber,
                                    area = customer.area,
                                    amount = transaction.amount,
                                    agentId = transaction.createdBy ?: "",
                                    timestamp = Date(transaction.dateMillis)
                                )
                            }
                        }
                    }
                }
            }.collect {
                _collectionDetails.value = it
            }
        }
    }

    // --- Utility Methods ---
    fun setCollectionDateRange(start: Date, end: Date) {
        _collectionStartDate.value = start
        _collectionEndDate.value = end
    }

    fun setViewingAgent(agentId: String?) {
        _viewingAgentId.value = agentId
    }

    suspend fun getCollectionDataForRange(startDate: Date, endDate: Date): List<MonthlyCollectionData> {
        val user = authViewModel.currentUser.value
        val role = authViewModel.userRole.value
        if (user == null) return emptyList()

        val adminUid = authViewModel.adminUid.value ?: ""
        if (adminUid.isEmpty()) return emptyList()

        val transactions = database.transactionDao().getPaymentTransactionsBetween(
            adminUid, startDate.time, endDate.time
        )

        val relevantTransactions = when (role) {
            "agent" -> transactions.filter { it.createdBy == user.uid }
            else -> transactions
        }

        val customerMap = customers.value.associateBy { it.id } // Use the reactive customers flow's current value
        return relevantTransactions.mapNotNull { transaction ->
            customerMap[transaction.customerId]?.let { customer ->
                MonthlyCollectionData(
                    customerId = customer.id,
                    customerName = customer.name,
                    stbNumber = customer.stbNumber,
                    area = customer.area,
                    amount = transaction.amount,
                    agentId = transaction.createdBy ?: "",
                    timestamp = Date(transaction.dateMillis)
                )
            }
        }
    }

    fun generateCollectionCsv(collectionData: List<MonthlyCollectionData>): String {
        // Add hardware details in header
        val header = "CustomerID,CustomerName,PhoneNumber,BillingArea,CollectedPayment,TimeStamp,AgentName,HardwareDetails"
        val csv = StringBuilder()
        csv.appendLine(header)

        val customerDetailsMap = customers.value.associateBy { it.id }
        val agentNamesMap = agentNamesFlow.value
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.getDefault())

        collectionData.forEach { data ->
            val customer = customerDetailsMap[data.customerId]
            val agentName = agentNamesMap[data.agentId] ?: "Unknown"
            val timestamp = dateFormat.format(data.timestamp)
            val customerIdString = customer?.customerId ?: "NA"
            val phone = customer?.phone ?: "NA"
            val hardwareDetails = customer?.stbNumber ?: "NA"

            fun String.csvSafe() = this.replace(",", " ")
            csv.appendLine(
                listOf(
                    customerIdString.csvSafe(),
                    data.customerName.csvSafe(),
                    phone.csvSafe(),
                    data.area.csvSafe(),
                    data.amount.toString(),
                    timestamp.csvSafe(),
                    agentName.csvSafe(),
                    hardwareDetails.csvSafe() // New column
                ).joinToString(",")
            )
        }
        return csv.toString()
    }


    // Date utility functions
    fun getTodayRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.time

        return start to end
    }

    fun getCurrentMonthRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.time

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.time

        return start to end
    }

    fun getPreviousMonthRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.time

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.time

        return start to end
    }

    suspend fun importCustomersFromCsv(csvContent: String, currentUserId: String): List<ImportResult> {
        Log.d("CustomerViewModel", "Starting CSV import. Content length: ${csvContent.length}")
        _isLoading.value = true
        val results = mutableListOf<ImportResult>()
        val adminUid = authViewModel.adminUid.value ?: return listOf(ImportResult.Error("N/A", "Admin UID not found."))

        try {
            // Use CsvParser to parse content
            val parsedCustomers = CsvParser.parseCsvContent(csvContent)
            Log.d("CustomerViewModel", "Parsed ${parsedCustomers.size} customers from CSV")

            var successCount = 0

            for ((index, data) in parsedCustomers.withIndex()) {
                try {
                    Log.d("CustomerViewModel", "Processing customer ${index + 1}: ${data.name}")

                    // Validate required fields
                    if (data.name.isBlank()) {
                        results.add(ImportResult.Error("Row ${index + 2}", "Customer name is required"))
                        continue
                    }
                    if (data.phone.isBlank()) {
                        results.add(ImportResult.Error("Row ${index + 2}", "Phone number is required"))
                        continue
                    }
                    if (data.area.isBlank()) {
                        results.add(ImportResult.Error("Row ${index + 2}", "Billing area is required"))
                        continue
                    }
                    if (data.stbNumber.isBlank()) {
                        results.add(ImportResult.Error("Row ${index + 2}", "STB number is required"))
                        continue
                    }

                    // Check for duplicate STB numbers in existing customers
                    val existingCustomer = customers.value.find { it.stbNumber.equals(data.stbNumber, ignoreCase = true) }
                    if (existingCustomer != null) {
                        results.add(ImportResult.Error("Row ${index + 2}", "STB number already exists: ${data.stbNumber}"))
                        continue
                    }

                    // Generate or use provided customer ID
                    val customerId = if (!data.customerId.isNullOrBlank()) {
                        // Check if provided customer ID already exists
                        val existingId = customers.value.find { it.customerId == data.customerId }
                        if (existingId != null) {
                            results.add(ImportResult.Error("Row ${index + 2}", "Customer ID already exists: ${data.customerId}"))
                            continue
                        }
                        data.customerId
                    } else {
                        // Generate unique 9-digit customer ID
                        generateUniqueCustomerId()
                    }

                    // Create customer model with FIXED balance logic
                    val customer = CustomerModel(
                        id = UUID.randomUUID().toString(),
                        customerId = customerId,
                        adminUid = adminUid,
                        name = data.name.trim(),
                        phone = data.phone.trim(),
                        area = data.area.trim().uppercase(),
                        stbNumber = data.stbNumber.trim(),
                        recurringCharge = data.recurringCharge,
                        balance = data.initialPayment, // This is the starting balance/debt amount
                        lastPaymentDate = if (data.initialPayment > 0.0) Timestamp.now() else null, // FIXED: if there's balance, no payment yet
                        assignedAgentId = currentUserId,
                        createdAt = Timestamp.now(),
                        lastUpdated = Timestamp.now()
                    )

                    Log.d("CustomerViewModel", "Created customer model: ${customer.name}, ID: ${customer.customerId}")

                    // Add customer to Firebase and local cache
                    try {
                        val customerRef = db.collection("customers").document(customer.id)
                        val hardwareRef = customerRef.collection("hardwareDetails").document("details")

                        // Use Firebase batch operation
                        val batch = db.batch()
                        batch.set(customerRef, customer)
                        batch.set(hardwareRef, HardwareDetailsModel(stbNumber = customer.stbNumber))

                        // Create initial transaction if there's an initial balance
                        var initialTransactionId: String? = null
                        if (data.initialPayment != 0.0) {
                            val transactionRef = customerRef.collection("balanceSheet").document()
                            initialTransactionId = transactionRef.id

                            val initialTx = TransactionModel(
                                id = initialTransactionId,
                                customerId = customer.id,
                                customerName = customer.name,
                                adminUid = adminUid,
                                amount = data.initialPayment,
                                type = if (data.initialPayment > 0) "generatedDue" else "collectedPayment", // FIXED: proper transaction type
                                description = "Initial balance from CSV import",
                                createdBy = currentUserId,
                                date = Timestamp.now(),
                                lastModified = Timestamp.now()
                            )
                            batch.set(transactionRef, initialTx)
                        }

                        // Execute Firebase batch
                        batch.commit().await()
                        Log.d("CustomerViewModel", "Successfully saved customer to Firebase: ${customer.name}")

                        // Update local cache with consistent IDs
                        database.customerDao().insertCustomer(customer.toLocalEntity())
                        database.hardwareDao().insertHardware(
                            HardwareDetailsModel(stbNumber = customer.stbNumber).toLocalEntity(customer.id)
                        )

                        // Add transaction to local cache if exists
                        if (initialTransactionId != null && data.initialPayment != 0.0) {
                            val localTransaction = TransactionModel(
                                id = initialTransactionId, // Use same ID as Firebase
                                customerId = customer.id,
                                customerName = customer.name,
                                adminUid = adminUid,
                                amount = data.initialPayment,
                                type = if (data.initialPayment > 0) "generatedDue" else "collectedPayment",
                                description = "Initial balance from CSV import",
                                createdBy = currentUserId,
                                date = Timestamp.now(),
                                lastModified = Timestamp.now()
                            )
                            database.transactionDao().insertTransaction(localTransaction.toLocalEntity())
                        }

                        results.add(ImportResult.Success(customer.name))
                        successCount++
                        Log.d("CustomerViewModel", "Successfully imported customer: ${customer.name}")

                    } catch (e: Exception) {
                        Log.e("CustomerViewModel", "Failed to save customer ${data.name}", e)
                        results.add(ImportResult.Error("Row ${index + 2}", "Failed to save: ${e.message}"))
                    }

                } catch (e: Exception) {
                    Log.e("CustomerViewModel", "Error processing row ${index + 2}", e)
                    results.add(ImportResult.Error("Row ${index + 2}", "Parse error: ${e.message}"))
                }
            }

        } catch (e: Exception) {
            Log.e("CustomerViewModel", "CSV import failed", e)
            results.add(ImportResult.Error("File", "Failed to process CSV: ${e.message}"))
        } finally {
            _isLoading.value = false
        }

        Log.d("CustomerViewModel", "Import finished. ${results.count { it is ImportResult.Success }} success, ${results.count { it is ImportResult.Error }} errors")
        return results
    }

    private fun String.removeQuotes(): String {
        return this.trim().removeSurrounding("\"")
    }

    private fun generateUniqueCustomerId(): String {
        var newId: String
        do {
            newId = (100000000 + kotlin.random.Random.nextInt(900000000)).toString()
        } while (customers.value.any { it.customerId == newId })
        return newId
    }

    // Also add this method to check existing customer IDs in the database
    private suspend fun isCustomerIdExists(customerId: String): Boolean {
        return try {
            val existingCustomer = db.collection("customers")
                .whereEqualTo("customerId", customerId)
                .whereEqualTo("adminUid", authViewModel.adminUid.value)
                .get()
                .await()
            !existingCustomer.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    // **CRITICAL**: Proper cleanup - only remove listeners on logout, not navigation
    override fun onCleared() {
        super.onCleared()
        Log.d("CustomerViewModel", "ViewModel cleared - removing all listeners")
        customersListener?.remove()
        transactionsListener?.remove()
        usersListener?.remove()
        singleCustomerTransactionsListener?.remove()

        // Save app close timestamp for incremental sync
        cacheManager.saveAppCloseTimestamp()
    }
}

// Helper data class for the summary trigger flow
private data class SummaryTrigger(
    val customers: List<CustomerModel>,
    val changesTrigger: Long, // NEW: Database changes trigger
    val role: String?,
    val user: com.google.firebase.auth.FirebaseUser?,
    val adminUid: String?
)
