
package com.example.billingapp_2.data.mappers

import com.example.billingapp_2.data.local.entities.*
import com.example.billingapp_2.model.*
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataMappers {

    private val gson = Gson()

    // --- Customer Mappers ---
    fun CustomerModel.toLocalEntity(): CustomerEntity {
        return CustomerEntity(
            id = this.id,
            customerId = this.customerId,
            adminUid = this.adminUid,
            name = this.name,
            phone = this.phone,
            area = this.area,
            stbNumber = this.stbNumber,
            balance = this.balance,
            recurringCharge = this.recurringCharge,
            lastPaymentDateMillis = this.lastPaymentDate?.toDate()?.time,
            assignedAgentId = this.assignedAgentId,
            createdAtMillis = this.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    fun CustomerEntity.toCustomerModel(): CustomerModel {
        return CustomerModel(
            id = this.id,
            customerId = this.customerId,
            adminUid = this.adminUid,
            name = this.name,
            phone = this.phone,
            area = this.area,
            stbNumber = this.stbNumber,
            balance = this.balance,
            recurringCharge = this.recurringCharge,
            lastPaymentDate = this.lastPaymentDateMillis?.let { Timestamp(java.util.Date(it)) },
            assignedAgentId = this.assignedAgentId,
            createdAt = Timestamp(java.util.Date(this.createdAtMillis))
        )
    }

    // --- Transaction Mappers ---
    fun TransactionModel.toLocalEntity(): TransactionEntity {
        return TransactionEntity(
            id = this.id,
            customerId = this.customerId,
            customerName = this.customerName,
            adminUid = this.adminUid,
            amount = this.amount,
            type = this.type,
            description = this.description,
            billPeriod = this.billPeriod,
            dateMillis = this.date.toDate().time,
            createdBy = this.createdBy,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    fun TransactionEntity.toTransactionModel(): TransactionModel {
        return TransactionModel(
            id = this.id,
            customerId = this.customerId,
            customerName = this.customerName,
            adminUid = this.adminUid,
            amount = this.amount,
            type = this.type,
            description = this.description,
            billPeriod = this.billPeriod,
            date = Timestamp(java.util.Date(this.dateMillis)),
            createdBy = this.createdBy
        )
    }

    // --- Hardware Mappers ---
    fun HardwareDetailsModel.toLocalEntity(customerId: String): HardwareDetailsEntity {
        return HardwareDetailsEntity(
            id = "${customerId}_hardware",
            customerId = customerId,
            stbNumber = this.stbNumber,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    fun HardwareDetailsEntity.toHardwareModel(): HardwareDetailsModel {
        return HardwareDetailsModel(
            stbNumber = this.stbNumber
        )
    }

    // --- User Mappers ---
    fun UserModel.toLocalEntity(): UserEntity {
        return UserEntity(
            uid = this.uid,
            adminUid = this.adminUid,
            name = this.name,
            phone = this.phone,
            role = this.role,
            assignedAreasJson = gson.toJson(this.assignedAreas),
            permissionsJson = gson.toJson(this.permissions),
            createdAtMillis = this.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
            createdBy = gson.toJson(this.createdBy),
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    fun UserEntity.toUserModel(): UserModel {
        val areasType = object : TypeToken<List<String>>() {}.type
        val permissionsType = object : TypeToken<Map<String, Boolean>>() {}.type

        return UserModel(
            uid = this.uid,
            adminUid = this.adminUid,
            name = this.name,
            phone = this.phone,
            role = this.role,
            assignedAreas = gson.fromJson(this.assignedAreasJson, areasType) ?: emptyList(),
            permissions = gson.fromJson(this.permissionsJson, permissionsType) ?: emptyMap(),
            createdAt = Timestamp(java.util.Date(this.createdAtMillis)),
            createdBy = this.createdBy
        )
    }

    // --- Collection Mappers (FIXED - Different names to avoid JVM signature clash) ---
    fun List<CustomerEntity>.toCustomerModels(): List<CustomerModel> {
        return this.map { it.toCustomerModel() }
    }

    fun List<TransactionEntity>.toTransactionModels(): List<TransactionModel> {
        return this.map { it.toTransactionModel() }
    }

    fun List<UserEntity>.toUserModels(): List<UserModel> {
        return this.map { it.toUserModel() }
    }

    // FIXED: Different function names to avoid JVM signature clash
    fun List<CustomerModel>.toCustomerEntities(): List<CustomerEntity> {
        return this.map { it.toLocalEntity() }
    }

    fun List<TransactionModel>.toTransactionEntities(): List<TransactionEntity> {
        return this.map { it.toLocalEntity() }
    }

    fun List<UserModel>.toUserEntities(): List<UserEntity> {
        return this.map { it.toLocalEntity() }
    }
}
