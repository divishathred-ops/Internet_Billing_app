package com.example.billingapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billingapp.data.cache.CacheManager
import com.example.billingapp.data.local.AppDatabase
import com.example.billingapp.data.repository.CustomerRepository
import com.example.billingapp.model.TransactionModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class PaymentCollectionViewModel(
    private val authViewModel: AuthViewModel,
    private val repository: CustomerRepository
) : ViewModel() {    private val db = FirebaseFirestore.getInstance()

    private val _adminUid = MutableStateFlow<String?>(null)
    private val _collections = MutableStateFlow<List<TransactionModel>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val collections: StateFlow<List<TransactionModel>> = _collections.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var collectionListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            authViewModel.adminUid.collect { uid ->
                if (_adminUid.value != uid) {
                    _adminUid.value = uid
                    collectionListener?.remove() // Always remove old listener
                    if (uid == null) {
                        _collections.value = emptyList() // Clear data on logout
                    }
                }
            }
        }
    }

    fun fetchCollections(startDate: Long, endDate: Long) {
        val adminId = _adminUid.value ?: return

        _isLoading.value = true
        collectionListener?.remove()

        collectionListener = db.collectionGroup("balanceSheet")
            .whereEqualTo("adminUid", adminId)
            .whereEqualTo("type", "collectedPayment")
            .whereGreaterThanOrEqualTo("date", Timestamp(Date(startDate)))
            .whereLessThanOrEqualTo("date", Timestamp(Date(endDate)))
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false
                if (error != null) {
                    _collections.value = emptyList()
                    return@addSnapshotListener
                }
                _collections.value = snapshots?.toObjects(TransactionModel::class.java) ?: emptyList()
            }
    }

    fun fetchCollectionsForDateRange(startDate: Date, endDate: Date) {
        fetchCollections(startDate.time, endDate.time)
    }

    fun clearCollections() {
        _collections.value = emptyList()
        collectionListener?.remove()
    }

    // Get collections for current month
    fun fetchCurrentMonthCollections() {
        val calendar = Calendar.getInstance()

        // Start of month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // End of month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        fetchCollectionsForDateRange(startDate, endDate)
    }

    // Get collections for today
    fun fetchTodayCollections() {
        val calendar = Calendar.getInstance()

        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // End of today
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        fetchCollectionsForDateRange(startDate, endDate)
    }

    override fun onCleared() {
        super.onCleared()
        collectionListener?.remove()
    }
}