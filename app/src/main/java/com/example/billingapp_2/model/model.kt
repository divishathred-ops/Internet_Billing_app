
package com.example.billingapp_2.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Core Data Models
data class CustomerModel(
    val adminUid: String = "",
    val id: String = "",
    val customerId: String = "",
    val name: String = "",
    val phone: String = "",
    val area: String = "",
    val stbNumber: String = "",
    val recurringCharge: Double = 0.0,
    val balance: Double = 0.0,
    val lastPaymentDate: Timestamp? = null,
    val assignedAgentId: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    @ServerTimestamp // This annotation tells Firestore to set the timestamp on the server
    val lastUpdated: Timestamp? = null // Add this field
)

data class TransactionModel(
    val adminUid: String = "",
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val date: Timestamp = Timestamp.now(),
    val description: String = "",
    val billPeriod: String? = null,
    val createdBy: String? = null,
    var notes: String = "",
    @ServerTimestamp
    val lastModified: Timestamp? = null // Add this field
)

data class ExpenseModel(
    val adminUid: String = "",
    val id: String = "",
    val agentId: String = "",
    val area: String = "",
    val date: Timestamp = Timestamp.now(),
    val type: String = "", // e.g., "food", "petrol", "salary", "collectedByBoss"
    val amount: Double = 0.0,
    val description: String = ""
)

data class HardwareDetailsModel(
    val stbNumber: String = ""
)

data class UserModel(
    val adminUid: String = "", // For an admin, this is their own UID. For an agent, it's their admin's UID.
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "", // "admin" or "agent"
    @get:PropertyName("permissions")
    val permissions: Map<String, Boolean> = emptyMap(),
    @get:PropertyName("assignedAreas")
    val assignedAreas: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val createdBy: String? = null // UID of the admin who created this user
)

// Permission structure, not stored directly but converted to a Map in UserModel.
data class Permission(
    val addCustomer: Boolean = false,
    val editCustomer: Boolean = false,
    val deleteCustomer: Boolean = false,
    val collectPayment: Boolean = false,
    val deleteTransaction: Boolean = false,
    val renewSubscription: Boolean = false,
    val editHardware: Boolean = false,
    val exportData: Boolean = false,
    val changeBalance: Boolean = false
) {
    companion object {
        fun adminPermissions(): Map<String, Boolean> {
            return mapOf(
                "editCustomer" to true,
                "deleteCustomer" to true,
                "collectPayment" to true,
                "renewSubscription" to true,
                "viewAgents" to true,
                "editAgents" to true,
                "changeBalance" to true // Admins have this permission by default.
            )
        }

        fun defaultAgentPermissions(): Map<String, Boolean> {
            return mapOf(
                "editCustomer" to true,
                "deleteCustomer" to false,
                "collectPayment" to true,
                "renewSubscription" to true,
                "viewAgents" to false,
                "editAgents" to false,
                "changeBalance" to false // Agents DO NOT have this permission by default.
            )
        }
    }
}

// Models for Reporting and Data Import

data class ReportRowData(
    val customerName: String,
    val customerId: String,
    val stbNumber: String,
    val amount: Double,
    val transactionType: String,
    val timestamp: Date,
    val area: String,
    val userId: String,
    val agentName: String
)

data class CsvCustomerData(
    val name: String,
    val phone: String,
    val area: String,
    val stbNumber: String,
    val recurringCharge: Double,
    val initialPayment: Double
)

data class InitialStateSnapshot(
    val customersLoaded: Boolean,
    val transactionsLoaded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)


// Model for PaymentListScreen
data class CustomerPaymentDetail(
    val customerId: String,
    val customerName: String,
    val collectedAmount: Double,
    val agentName: String,
    val balanceSheetId: String
)

data class AdminSummary(
    val totalMonthlyCollection: Double = 0.0,
    val totalTodayCollection: Double = 0.0,
    val totalUnpaidBalance: Double = 0.0,
    val activeCustomerCount: Long = 0L, // Use Long for FieldValue.increment
    @ServerTimestamp val lastUpdated: Date? = null
)
data class MonthlyCollectionData(
    val customerId: String,
    val customerName: String,
    val stbNumber: String,
    val area: String,
    val amount: Double,
    val agentId: String,
    val timestamp: Date
)
sealed class ImportResult {
    data class Success(val customerName: String) : ImportResult()
    data class Error(val line: String, val error: String) : ImportResult()
}
