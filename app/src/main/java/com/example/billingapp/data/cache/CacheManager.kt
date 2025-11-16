package com.example.billingapp.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import kotlin.apply

class CacheManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("billing_cache", Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_INITIAL_SYNC_COMPLETE = "initial_sync_complete"
        private const val KEY_APP_CLOSE_TIMESTAMP = "app_close_timestamp"
        private const val KEY_APP_OPEN_TIMESTAMP = "app_open_timestamp"
        private const val KEY_USER_ADMIN_UID = "user_admin_uid"
        private const val KEY_CACHED_AREAS = "cached_areas"
        private const val KEY_LAST_CUSTOMER_SYNC = "last_customer_sync"
        private const val KEY_LAST_TRANSACTION_SYNC = "last_transaction_sync"
        private const val KEY_LAST_USER_SYNC = "last_user_sync"
        private const val KEY_CACHE_VERSION = "cache_version"

        private const val CURRENT_CACHE_VERSION = 1
    }

    // --- Timestamp Management ---
    fun saveLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
            .apply()
    }

    fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    }

    fun saveAppCloseTimestamp(timestamp: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putLong(KEY_APP_CLOSE_TIMESTAMP, timestamp)
            .apply()
    }

    fun getAppCloseTimestamp(): Long {
        return sharedPreferences.getLong(KEY_APP_CLOSE_TIMESTAMP, 0L)
    }

    fun saveAppOpenTimestamp(timestamp: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putLong(KEY_APP_OPEN_TIMESTAMP, timestamp)
            .apply()
    }

    fun getAppOpenTimestamp(): Long {
        return sharedPreferences.getLong(KEY_APP_OPEN_TIMESTAMP, 0L)
    }

    // --- Sync Status ---
    fun setInitialSyncComplete(complete: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_INITIAL_SYNC_COMPLETE, complete)
            .apply()
    }

    fun isInitialSyncComplete(): Boolean {
        return sharedPreferences.getBoolean(KEY_INITIAL_SYNC_COMPLETE, false)
    }

    // --- User Data ---
    fun saveUserAdminUid(adminUid: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_ADMIN_UID, adminUid)
            .apply()
    }

    fun getUserAdminUid(): String? {
        return sharedPreferences.getString(KEY_USER_ADMIN_UID, null)
    }

    // --- Individual Sync Timestamps ---
    fun saveLastCustomerSync(timestamp: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_CUSTOMER_SYNC, timestamp)
            .apply()
    }

    fun getLastCustomerSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_CUSTOMER_SYNC, 0L)
    }

    fun saveLastTransactionSync(timestamp: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_TRANSACTION_SYNC, timestamp)
            .apply()
    }

    fun getLastTransactionSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_TRANSACTION_SYNC, 0L)
    }

    fun saveLastUserSync(timestamp: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_USER_SYNC, timestamp)
            .apply()
    }

    fun getLastUserSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_USER_SYNC, 0L)
    }

    // --- Cache Areas ---
    fun saveCachedAreas(areas: List<String>) {
        val json = gson.toJson(areas)
        sharedPreferences.edit()
            .putString(KEY_CACHED_AREAS, json)
            .apply()
    }

    fun getCachedAreas(): List<String> {
        val json = sharedPreferences.getString(KEY_CACHED_AREAS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // --- Cache Validation ---

    fun shouldPerformIncrementalSync(): Boolean {
        val lastCloseTime = getAppCloseTimestamp()
        val currentTime = System.currentTimeMillis()

        // If app was closed more than 5 minutes ago, do incremental sync
        return lastCloseTime > 0 && (currentTime - lastCloseTime) > (5 * 60 * 1000)
    }

    private fun getCacheVersion(): Int {
        return sharedPreferences.getInt(KEY_CACHE_VERSION, 0)
    }

    private fun setCacheVersion(version: Int) {
        sharedPreferences.edit()
            .putInt(KEY_CACHE_VERSION, version)
            .apply()
    }

    // --- Clear Cache ---
    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }

    fun clearUserSpecificCache() {
        sharedPreferences.edit()
            .remove(KEY_LAST_SYNC_TIMESTAMP)
            .remove(KEY_INITIAL_SYNC_COMPLETE)
            .remove(KEY_USER_ADMIN_UID)
            .remove(KEY_CACHED_AREAS)
            .remove(KEY_LAST_CUSTOMER_SYNC)
            .remove(KEY_LAST_TRANSACTION_SYNC)
            .remove(KEY_LAST_USER_SYNC)
            .apply()
    }

    fun initializeCache() {
        setCacheVersion(CURRENT_CACHE_VERSION)
        saveAppOpenTimestamp()
    }

    // --- Utility Methods ---
    fun getIncrementalSyncTimestamp(): Long {
        return getAppCloseTimestamp()
    }
    fun hasValidCache(): Boolean {
        val hasInitialSync = isInitialSyncComplete()
        val hasAdminUid = getUserAdminUid() != null
        val correctVersion = getCacheVersion() == CURRENT_CACHE_VERSION
        val hasRecentData = getLastCustomerSync() > 0

        // **CRITICAL**: Check if cache is not too old (more than 24 hours)
        val lastSync = getLastSyncTimestamp()
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - lastSync
        val maxCacheAge = 24 * 60 * 60 * 1000L // 24 hours

        val cacheNotTooOld = cacheAge < maxCacheAge

        val isValid = hasInitialSync && hasAdminUid && correctVersion && hasRecentData && cacheNotTooOld

        Log.d("CacheManager", """
        Cache validity check:
        - hasInitialSync: $hasInitialSync
        - hasAdminUid: $hasAdminUid
        - correctVersion: $correctVersion
        - hasRecentData: $hasRecentData
        - cacheNotTooOld: $cacheNotTooOld (age: ${cacheAge / 1000}s)
        - RESULT: $isValid
    """.trimIndent())

        return isValid
    }
    fun getDatabaseSize(context: Context): Long {
        val dbFile = context.getDatabasePath("billing_database")
        return if (dbFile.exists()) dbFile.length() else 0L
    }

    fun shouldPerformFullSync(context: Context): Boolean {
        val dbSize = getDatabaseSize(context)
        return isFreshInstall() ||
                !isInitialSyncComplete() ||
                dbSize < 1 * 1024 * 1024 // Less than 1MB
    }
    // NEW: Check if this is a fresh app install
    fun isFreshInstall(): Boolean {
        val hasRunBefore = sharedPreferences.getBoolean("HAS_RUN_BEFORE", false)
        if (!hasRunBefore) {
            sharedPreferences.edit().putBoolean("HAS_RUN_BEFORE", true).apply()
            return true
        }
        return false
    }
    fun markSyncCompleted() {
        saveLastSyncTimestamp()
        setInitialSyncComplete(true)
    }
}