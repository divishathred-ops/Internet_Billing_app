package com.example.billingapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billingapp.data.cache.CacheManager
import com.example.billingapp.data.local.AppDatabase
import com.example.billingapp.data.mappers.DataMappers.toLocalEntity
import com.example.billingapp.data.mappers.DataMappers.toUserModel
import com.example.billingapp.model.Permission
import com.example.billingapp.model.UserModel
import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.billingapp.data.repository.CustomerRepository

import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(
    private val application: Application,
    private val database: AppDatabase,
    private val cacheManager: CacheManager,
    private val repository: CustomerRepository
) : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()


    // --- Core Authentication State ---
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null) // Start with null
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false) // Start with false
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // --- User Details State ---
    private val _userModel = MutableStateFlow<UserModel?>(null)
    val userModel: StateFlow<UserModel?> = _userModel.asStateFlow()

    private val _userRole = MutableStateFlow<String?>(null)
    val userRole: StateFlow<String?> = _userRole.asStateFlow()
    private val _adminUid = MutableStateFlow<String?>(null)
    val adminUid: StateFlow<String?> = _adminUid.asStateFlow()

    private val _assignedAreas = MutableStateFlow<List<String>>(emptyList())
    val assignedAreas: StateFlow<List<String>> = _assignedAreas.asStateFlow()

    private val _userPermissions = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userPermissions: StateFlow<Map<String, Boolean>> = _userPermissions.asStateFlow()

    // --- Sync States ---
    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady.asStateFlow()

    private val _isInitialSyncInProgress = MutableStateFlow(false)
    val isInitialSyncInProgress: StateFlow<Boolean> = _isInitialSyncInProgress.asStateFlow()

    private val _isIncrementalSyncInProgress = MutableStateFlow(false)
    val isIncrementalSyncInProgress: StateFlow<Boolean> = _isIncrementalSyncInProgress.asStateFlow()

    // Auth state listener
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentUser.value = user
        _isLoggedIn.value = user != null

        if (user != null) {
            initializeAuthenticatedSession(user)
        } else {
            clearAuthenticationState()
        }
    }
    private fun clearFirebaseAuthCache() {
        try {
            // Correct: Use the 'application' property passed into the constructor
            val context = application.applicationContext
            val firebaseAuthPrefsName = "com.google.firebase.auth.api.TOKEN_STORE"
            val firebaseAuthPrefs = context.getSharedPreferences(firebaseAuthPrefsName, Context.MODE_PRIVATE)
            firebaseAuthPrefs.edit().clear().apply()
            Log.d("AuthViewModel", "Firebase auth cache cleared successfully.")
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error clearing Firebase auth cache", e)
        }
    }

    // In init block:
    init {
        if (BuildConfig.DEBUG) {
            clearFirebaseAuthCache()
        }
        auth.addAuthStateListener(authStateListener)
        cacheManager.saveAppOpenTimestamp()
    }

    private fun initializeAuthenticatedSession(user: FirebaseUser) {
        viewModelScope.launch {
            try {
                // On session initialization, always fetch fresh user details from Firebase
                // This ensures roles and permissions are up-to-date.
                fetchUserDetailsFromFirebase(user)
                _isAuthReady.value = true

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error initializing session, signing out.", e)
                signOut() // Sign out if session initialization fails
            }
        }
    }

    private suspend fun loadUserFromCache(userModel: UserModel) {
        _userModel.value = userModel
        _userRole.value = userModel.role
        _adminUid.value = userModel.adminUid.ifEmpty {
            if (userModel.role == "admin") userModel.uid else null
        }
        _userPermissions.value = userModel.permissions
        _assignedAreas.value = userModel.assignedAreas

        Log.d("AuthViewModel", "Loaded user from cache: ${userModel.name}, Role: ${userModel.role}")
    }

    private suspend fun performInitialSync(user: FirebaseUser) {
        Log.d("AuthViewModel", "Starting initial sync for user: ${user.uid}")
        _isInitialSyncInProgress.value = true

        try {
            // Fetch fresh user data from Firebase
            fetchUserDetailsFromFirebase(user)

            // Cache this is the first sync
            cacheManager.setInitialSyncComplete(true)
            cacheManager.markSyncCompleted()

            _isAuthReady.value = true
            Log.d("AuthViewModel", "Initial sync completed successfully")

        } catch (e: Exception) {
            Log.e("AuthViewModel", "Initial sync failed", e)
            _isAuthReady.value = false
        } finally {
            _isInitialSyncInProgress.value = false
        }
    }
    private suspend fun performIncrementalSync() {
        Log.d("AuthViewModel", "Starting incremental sync")
        _isIncrementalSyncInProgress.value = true

        try {
            val currentUser = auth.currentUser?: return
            val syncTimestamp = cacheManager.getIncrementalSyncTimestamp()

            // Check for user updates since last sync
            val userDoc = db.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            if (userDoc.exists()) {
                val serverUser = userDoc.toObject(UserModel::class.java)
                serverUser?.let { user ->
                    val serverTimestamp = user.createdAt?.toDate()?.time?: 0L
                    if (serverTimestamp > syncTimestamp) {
                        database.userDao().insertUser(user.toLocalEntity())
                        loadUserFromCache(user)
                        Log.d("AuthViewModel", "User data updated from server during incremental sync")
                    }
                }
            }

            cacheManager.markSyncCompleted()
            _isAuthReady.value = true

        } catch (e: Exception) {
            Log.e("AuthViewModel", "Incremental sync failed", e)
            _isAuthReady.value = true
        } finally {
            _isIncrementalSyncInProgress.value = false
        }
    }
    private suspend fun fetchUserDetailsFromFirebase(user: FirebaseUser) {
        try {
            val userDoc = db.collection("users").document(user.uid).get().await()
            if (!userDoc.exists()) {
                Log.w("AuthViewModel", "User document does not exist for UID: ${user.uid}. Signing out.")
                signOut()
                return
            }

            val userModel = userDoc.toObject(UserModel::class.java)
            if (userModel == null) {
                Log.e("AuthViewModel", "Failed to parse user model for UID: ${user.uid}. Signing out.")
                signOut()
                return
            }

            // Update local cache
            database.userDao().insertUser(userModel.toLocalEntity())

            // Update memory state
            loadUserFromCache(userModel)

            // Save admin UID for future use
            val adminUid = userModel.adminUid.ifEmpty {
                if (userModel.role == "admin") user.uid else null
            }
            adminUid?.let { cacheManager.saveUserAdminUid(it) }

            Log.d("AuthViewModel", "User details fetched from Firebase and cached")

        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error fetching user details from Firebase", e)
            throw e
        }
    }
    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                // The actual Firebase sign-in call was missing. It's now added.
                auth.signInWithEmailAndPassword(email, pass).await()
                // On success, the AuthStateListener will automatically handle the next steps.
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign-in failed", e)
                onResult(false, e.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                // **CRITICAL**: Save app close timestamp before logout
                cacheManager.saveAppCloseTimestamp()

                auth.signOut()

                // Clear user-specific cache on logout
                cacheManager.clearUserSpecificCache()

                _isLoggedIn.value = false
                _currentUser.value = null
                _userModel.value = null
                _userRole.value = null
                _adminUid.value = null
                _assignedAreas.value = emptyList()
                _userPermissions.value = emptyMap()
                _isAuthReady.value = false
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error during sign out", e)
            }
        }
    }

    private fun clearAuthenticationState() {
        _userModel.value = null
        _userRole.value = ""
        _adminUid.value = null
        _userPermissions.value = emptyMap()
        _assignedAreas.value = emptyList()
        _isAuthReady.value = false
        _isInitialSyncInProgress.value = false
        _isIncrementalSyncInProgress.value = false
    }

    // --- Admin & Agent Management Functions (Keep existing) ---
    fun signUpAdmin(name: String, phone: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val email = "$phone@billingapp.com"
                val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
                val uid = authResult.user?.uid ?: throw IllegalStateException("Failed to create user.")

                val adminUser = UserModel(
                    adminUid = uid,
                    uid = uid,
                    name = name,
                    phone = phone,
                    role = "admin",
                    permissions = Permission.adminPermissions(),
                    createdAt = Timestamp.now(),
                    createdBy = uid
                )

                // Save to Firebase
                db.collection("users").document(uid).set(adminUser).await()

                // Cache locally
                database.userDao().insertUser(adminUser.toLocalEntity())

                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    fun verifyAdminPassword(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user?.email == null) {
                onResult(false)
                return@launch
            }
            try {
                val credential = EmailAuthProvider.getCredential(user.email!!, password)
                user.reauthenticate(credential).await()
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun createAuthUserAndReSignInAdmin(
        agentEmail: String,
        agentPass: String,
        adminEmail: String,
        adminPass: String,
        onComplete: (success: Boolean, newAgentUid: String?, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val creationResult = auth.createUserWithEmailAndPassword(agentEmail, agentPass).await()
                val newUid = creationResult.user?.uid

                auth.signInWithEmailAndPassword(adminEmail, adminPass).await()

                if (newUid != null) {
                    onComplete(true, newUid, null)
                } else {
                    onComplete(false, null, "Failed to get new user UID after creation.")
                }
            } catch (e: Exception) {
                try {
                    if (auth.currentUser?.email != adminEmail) {
                        auth.signInWithEmailAndPassword(adminEmail, adminPass).await()
                    }
                } catch (reloginEx: Exception) {
                    Log.e("AuthViewModel", "Failed to re-login admin after agent creation failure.", reloginEx)
                }
                onComplete(false, null, e.message)
            }
        }
    }

    // --- Cache Management Functions ---



    fun forceFullSync() {
        val user = auth.currentUser
        if (user != null) {
            viewModelScope.launch {
                cacheManager.clearUserSpecificCache()
                performInitialSync(user)
            }
        }
    }
    fun onAppBackground() {
        cacheManager.saveAppCloseTimestamp()
        Log.d("AuthViewModel", "App backgrounded, timestamp saved")
    }

    fun onAppForeground() {
        cacheManager.saveAppOpenTimestamp()
        Log.d("AuthViewModel", "App foregrounded, timestamp saved")
    }

    fun logout() {
        // Clear all listeners before logout
        // You'll need to inject CustomerViewModel here or use a shared cleanup mechanism

        // Clear auth state
        _currentUser.value = null
        _isLoggedIn.value = false
        _userRole.value = null

        // Clear cache
        cacheManager.clearUserSpecificCache()

        Log.d("AuthViewModel", "User logged out, cache cleared")
    }
    fun onAppForegrounded() {
        viewModelScope.launch {
            val adminUid = adminUid.value ?: return@launch

            // Decide which sync to perform based on cache state
            if (cacheManager.hasValidCache() && cacheManager.shouldPerformIncrementalSync()) {
                Log.d("AuthViewModel", "App foregrounded. Performing incremental sync.")
                repository.performIncrementalDataSync(adminUid, cacheManager.getIncrementalSyncTimestamp())
            } else if (!cacheManager.hasValidCache()) {
                Log.d("AuthViewModel", "App foregrounded. No valid cache found. Performing full sync.")
                repository.performFullDataSync(adminUid)
            } else {
                Log.d("AuthViewModel", "App foregrounded. Cache is recent, no sync needed immediately.")
            }
            // Mark the sync as complete to update timestamps
            cacheManager.markSyncCompleted()
        }
    }
    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        // Save app close timestamp
        cacheManager.saveAppCloseTimestamp()
    }
}