package com.example.billingapp_2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billingapp_2.model.Permission
import com.example.billingapp_2.model.UserModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AgentViewModel(
    private val authViewModel: AuthViewModel,

) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var adminUid: String? = null

    private val _agents = MutableStateFlow<List<UserModel>>(emptyList())
    val agents: StateFlow<List<UserModel>> = _agents.asStateFlow()

    private val _allBillingAreas = MutableStateFlow<List<String>>(emptyList())
    val allBillingAreas: StateFlow<List<String>> = _allBillingAreas.asStateFlow()

    init {
        viewModelScope.launch {
            authViewModel.adminUid.collect { uid ->
                Log.d("AgentViewModel", "AdminUid changed to: $uid")
                if (adminUid != uid) {
                    adminUid = uid
                    if (uid != null) {
                        // --- ROLE CHECK ADDED ---
                        // Only fetch this data if the current user is an admin.
                        // Agents do not have permission and do not need this overview.
                        if (authViewModel.userRole.value == "admin") {
                            fetchAgents()
                            fetchAllBillingAreas()
                        }
                    } else {
                        // Clear data on logout
                        _agents.value = emptyList()
                        _allBillingAreas.value = emptyList()
                    }
                }
            }
        }
    }

    fun fetchAgents() {
        val currentAdminUid = adminUid
        if (currentAdminUid.isNullOrBlank()) {
            Log.w("AgentViewModel", "AdminUid is null/blank, cannot fetch agents")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("AgentViewModel", "Fetching agents for adminUid: $currentAdminUid")
                val snapshot = db.collection("users")
                    .whereEqualTo("adminUid", currentAdminUid)
                    .whereEqualTo("role", "agent")
                    .get().await()

                val agentsList = snapshot.documents.mapNotNull { it.toObject(UserModel::class.java) }
                Log.d("AgentViewModel", "Loaded ${agentsList.size} agents")

                _agents.value = agentsList
            } catch (e: Exception) {
                Log.e("AgentViewModel", "Error fetching agents", e)
                _agents.value = emptyList()
            }
        }
    }

    /**
     * Orchestrates the entire agent creation flow, from admin verification to final data write.
     */
    fun orchestrateAgentCreation(
        agentName: String,
        agentPhone: String,
        agentPass: String,
        adminPass: String,
        authViewModel: AuthViewModel,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val adminEmail = authViewModel.currentUser.value?.email
            if (adminEmail == null) {
                onResult(false, "Could not find admin email. Please log in again.")
                return@launch
            }

            // Step 1: Verify admin's password to authorize the sensitive action.
            authViewModel.verifyAdminPassword(adminPass) { isVerified ->
                if (!isVerified) {
                    onResult(false, "Admin password verification failed.")
                    return@verifyAdminPassword
                }

                // Step 2: Create the authentication user and immediately re-sign in the admin.
                val agentEmail = "$agentPhone@billingapp.com"
                authViewModel.createAuthUserAndReSignInAdmin(agentEmail, agentPass, adminEmail, adminPass) { success, newAgentUid, error ->
                    if (success && newAgentUid != null) {
                        // Step 3: With admin securely logged in, create the agent's document in Firestore.
                        createAgentInFirestore(agentName, agentPhone, newAgentUid) { fsSuccess, fsError ->
                            if (fsSuccess) {
                                fetchAgents() // Refresh the agent list on the UI
                                onResult(true, null)
                            } else {
                                onResult(false, "Failed to save agent details: $fsError")
                            }
                        }
                    } else {
                        onResult(false, "Failed to create agent account: $error")
                    }
                }
            }
        }
    }

    /**
     * Writes the new agent's data to the Firestore database.
     */
    private fun createAgentInFirestore(
        name: String,
        phone: String,
        newAgentUid: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val currentAdminUid = auth.currentUser?.uid
            if (currentAdminUid == null) {
                onResult(false, "Admin user not identified.")
                return@launch
            }
            try {
                val agent = UserModel(
                    adminUid = currentAdminUid,  // Agent's adminUid is the admin's UID
                    uid = newAgentUid,           // Agent's own UID
                    name = name,
                    phone = phone,
                    role = "agent",
                    permissions = Permission.defaultAgentPermissions(),
                    createdAt = Timestamp.now(),
                    createdBy = currentAdminUid
                )

                Log.d("AgentViewModel", "Creating agent with adminUid: $currentAdminUid, agentUid: $newAgentUid")
                db.collection("users").document(newAgentUid).set(agent).await()
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("AgentViewModel", "Error creating agent in Firestore", e)
                onResult(false, e.message)
            }
        }
    }

    fun deleteAgent(agentId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                db.collection("users").document(agentId).delete().await()
                fetchAgents()
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("AgentViewModel", "Error deleting agent", e)
                onResult(false, e.message)
            }
        }
    }

    fun fetchAllBillingAreas() {
        val currentAdminUid = adminUid
        if (currentAdminUid.isNullOrBlank()) {
            Log.w("AgentViewModel", "AdminUid is null/blank, cannot fetch billing areas")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("AgentViewModel", "Fetching billing areas for adminUid: $currentAdminUid")
                val snapshot = db.collection("customers")
                    .whereEqualTo("adminUid", currentAdminUid)
                    .get().await()

                val areas = snapshot.documents.mapNotNull { it.getString("area") }.distinct()
                Log.d("AgentViewModel", "Loaded ${areas.size} billing areas: $areas")

                _allBillingAreas.value = areas
            } catch (e: Exception) {
                Log.e("AgentViewModel", "Error fetching billing areas", e)
                _allBillingAreas.value = emptyList()
            }
        }
    }

    fun updateAgentPermissionsAndAreas(
        agentId: String,
        permissions: Map<String, Boolean>,
        areas: List<String>,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d("AgentViewModel", "Updating permissions for agent: $agentId with areas: $areas")
                db.collection("users").document(agentId).update(
                    mapOf("permissions" to permissions, "assignedAreas" to areas)
                ).await()
                fetchAgents()
                onComplete(true)
            } catch (e: Exception) {
                Log.e("AgentViewModel", "Error updating agent permissions", e)
                onComplete(false)
            }
        }
    }
}
