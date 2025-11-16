package com.example.billingapp.data.local.dao
import androidx.room.*
import com.example.billingapp.data.local.entities.CustomerEntity
import com.example.billingapp.data.local.entities.HardwareDetailsEntity
import com.example.billingapp.data.local.entities.SyncMetadataEntity
import com.example.billingapp.data.local.entities.TransactionEntity
import com.example.billingapp.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao

interface CustomerDao {



// --- Query Operations ---

    @Query("SELECT * FROM customers WHERE adminUid = :adminUid AND isDeleted = 0 ORDER BY name ASC")
    fun getAllCustomersFlow(adminUid: String): Flow<List<CustomerEntity>>

    // Keep all your other DAO functions
    @Query("SELECT * FROM customers WHERE adminUid = :adminUid AND isDeleted = 0")
    suspend fun getAllCustomers(adminUid: String): List<CustomerEntity>




    @Query("SELECT * FROM customers WHERE id = :customerId AND isDeleted = 0")

    suspend fun getCustomerById(customerId: String): CustomerEntity?



    @Query("SELECT * FROM customers WHERE id = :customerId AND isDeleted = 0")

    fun getCustomerByIdFlow(customerId: String): Flow<CustomerEntity?>



    @Query("SELECT * FROM customers WHERE adminUid = :adminUid AND area IN (:areas) AND isDeleted = 0")

    fun getCustomersByAreas(adminUid: String, areas: List<String>): Flow<List<CustomerEntity>>



    @Query("SELECT DISTINCT area FROM customers WHERE adminUid = :adminUid AND isDeleted = 0")

    suspend fun getAllAreas(adminUid: String): List<String>



    @Query("SELECT * FROM customers WHERE adminUid = :adminUid AND lastUpdatedMillis > :timestamp AND isDeleted = 0")

    suspend fun getCustomersUpdatedAfter(adminUid: String, timestamp: Long): List<CustomerEntity>



// --- Insert/Update Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertCustomer(customer: CustomerEntity)



    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertCustomers(customers: List<CustomerEntity>)



    @Update

    suspend fun updateCustomer(customer: CustomerEntity)



    @Query("UPDATE customers SET balance = :balance, lastUpdatedMillis = :timestamp WHERE id = :customerId")

    suspend fun updateCustomerBalance(customerId: String, balance: Double, timestamp: Long = System.currentTimeMillis())



    @Query("UPDATE customers SET lastPaymentDateMillis = :paymentDate, lastUpdatedMillis = :timestamp WHERE id = :customerId")

    suspend fun updateLastPaymentDate(customerId: String, paymentDate: Long?, timestamp: Long = System.currentTimeMillis())



    @Query("UPDATE customers SET stbNumber = :stbNumber, lastUpdatedMillis = :timestamp WHERE id = :customerId")

    suspend fun updateStbNumber(customerId: String, stbNumber: String, timestamp: Long = System.currentTimeMillis())



// --- Delete Operations ---

    @Query("UPDATE customers SET isDeleted = 1, lastUpdatedMillis = :timestamp WHERE id = :customerId")

    suspend fun markCustomerDeleted(customerId: String, timestamp: Long = System.currentTimeMillis())



    @Query("DELETE FROM customers WHERE adminUid = :adminUid")

    suspend fun deleteAllCustomers(adminUid: String)



    @Query("DELETE FROM customers WHERE id = :customerId")

    suspend fun deleteCustomer(customerId: String)
// Add this method to your CustomerDao interface

    @Query("SELECT * FROM customers WHERE customerId = :customerId AND adminUid = :adminUid")
    suspend fun getCustomerByCustomerId(customerId: String, adminUid: String): CustomerEntity?
}

@Dao
interface TransactionDao {

    // --- Query Operations ---
    @Query("SELECT * FROM transactions WHERE adminUid = :adminUid AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getAllTransactionsFlow(adminUid: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getCustomerTransactionsFlow(customerId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId AND isDeleted = 0 ORDER BY dateMillis DESC")
    suspend fun getCustomerTransactions(customerId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE adminUid = :adminUid AND dateMillis BETWEEN :startTime AND :endTime AND type = 'collectedPayment' AND isDeleted = 0")
    suspend fun getPaymentTransactionsBetween(adminUid: String, startTime: Long, endTime: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE adminUid = :adminUid AND lastUpdatedMillis > :timestamp AND isDeleted = 0")
    suspend fun getTransactionsUpdatedAfter(adminUid: String, timestamp: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE createdBy = :agentId AND dateMillis BETWEEN :startTime AND :endTime AND type = 'collectedPayment' AND isDeleted = 0")
    suspend fun getAgentCollectionsBetween(agentId: String, startTime: Long, endTime: Long): List<TransactionEntity>

    // --- Insert/Update Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    // --- Delete Operations ---
    @Query("UPDATE transactions SET isDeleted = 1, lastUpdatedMillis = :timestamp WHERE id = :transactionId")
    suspend fun markTransactionDeleted(transactionId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE adminUid = :adminUid")
    suspend fun deleteAllTransactionsForAdmin(adminUid: String)

    @Query("DELETE FROM transactions WHERE customerId = :customerId")
    suspend fun deleteAllTransactionsForCustomer(customerId: String)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransaction(transactionId: String)

    @Query("SELECT MAX(dateMillis) FROM transactions WHERE customerId = :customerId AND isDeleted = 0")
    suspend fun getLatestTransactionTimestamp(customerId: String): Long?
}

@Dao
interface HardwareDao {

    // --- Query Operations ---
    @Query("SELECT * FROM hardware_details WHERE customerId = :customerId AND isDeleted = 0")
    suspend fun getHardwareByCustomerId(customerId: String): HardwareDetailsEntity?

    @Query("SELECT * FROM hardware_details WHERE customerId = :customerId AND isDeleted = 0")
    fun getHardwareByCustomerIdFlow(customerId: String): Flow<HardwareDetailsEntity?>

    // --- Insert/Update Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHardware(hardware: HardwareDetailsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHardwareList(hardwareList: List<HardwareDetailsEntity>)

    @Query("UPDATE hardware_details SET stbNumber = :stbNumber, lastUpdatedMillis = :timestamp WHERE customerId = :customerId")
    suspend fun updateStbNumber(customerId: String, stbNumber: String, timestamp: Long = System.currentTimeMillis())

    // --- Delete Operations ---
    @Query("DELETE FROM hardware_details")
    suspend fun deleteAllHardware()

    @Query("DELETE FROM hardware_details WHERE customerId = :customerId")
    suspend fun deleteHardwareForCustomer(customerId: String)
}

@Dao
interface UserDao {

    // --- Query Operations ---
    @Query("SELECT * FROM users WHERE adminUid = :adminUid AND isDeleted = 0")
    suspend fun getUsersByAdmin(adminUid: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE adminUid = :adminUid AND isDeleted = 0")
    fun getUsersByAdminFlow(adminUid: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE uid = :userId AND isDeleted = 0")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE adminUid = :adminUid AND lastUpdatedMillis > :timestamp AND isDeleted = 0")
    suspend fun getUsersUpdatedAfter(adminUid: String, timestamp: Long): List<UserEntity>

    // --- Insert/Update Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    // --- Delete Operations ---
    @Query("DELETE FROM users WHERE adminUid = :adminUid")
    suspend fun deleteAllUsers(adminUid: String)

    @Query("DELETE FROM users WHERE uid = :userId")
    suspend fun deleteUser(userId: String)
}

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE `key` = :metadataKey")
    suspend fun getMetadata(metadataKey: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE `key` = :metadataKey")
    suspend fun deleteMetadata(metadataKey: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAllMetadata()
}

