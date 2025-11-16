
package com.example.billingapp.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class BalanceSheetRepository {
    private val db = FirebaseFirestore.getInstance()

    /**
     * Query "collectPayment" type documents for a given customer, optional date range and agent filter.
     */
    suspend fun queryCollectPayments(
        customerId: String,
        start: Timestamp? = null,
        end: Timestamp? = null,
        agentId: String? = null
    ): List<Map<String, Any>> {
        var query: Query = db.collection("customers")
            .document(customerId)
            .collection("balancesheet")
            .whereEqualTo("type", "collectPayment")

        start?.let { query = query.whereGreaterThanOrEqualTo("date", it) }
        end?.let { query = query.whereLessThanOrEqualTo("date", it) }
        agentId?.let { query = query.whereEqualTo("createdBy", it) }

        val snapshot = query.get().await()
        return snapshot.documents.mapNotNull { it.data }
    }
}