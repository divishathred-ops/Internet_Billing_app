package com.example.billingapp_2.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["adminUid"]),
        Index(value = ["area"]),
        Index(value = ["customerId"])
    ]
)
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val adminUid: String,
    val name: String,
    val phone: String,
    val area: String,
    val stbNumber: String,
    val balance: Double,
    val recurringCharge: Double = 0.0,
    val lastPaymentDateMillis: Long? = null,
    val assignedAgentId: String? = null,
    val createdAtMillis: Long,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["adminUid"]),
        Index(value = ["createdBy"]),
        Index(value = ["dateMillis"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val customerName: String,
    val adminUid: String,
    val amount: Double,
    val type: String,
    val description: String,
    val billPeriod: String? = null,
    val dateMillis: Long,
    val createdBy: String? = null,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "hardware_details",
    indices = [Index(value = ["customerId"])]
)
data class HardwareDetailsEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val stbNumber: String,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["adminUid"]),
        Index(value = ["role"])
    ]
)
data class UserEntity(
    @PrimaryKey
    val uid: String,
    val adminUid: String,
    val name: String,
    val phone: String,
    val role: String,
    val assignedAreasJson: String, // JSON array of areas
    val permissionsJson: String, // JSON map of permissions
    val createdAtMillis: Long,
    val createdBy: String,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val adminUid: String,
    val agentId: String,
    val dateMillis: Long,
    val type: String,
    val amount: Double,
    val description: String,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)