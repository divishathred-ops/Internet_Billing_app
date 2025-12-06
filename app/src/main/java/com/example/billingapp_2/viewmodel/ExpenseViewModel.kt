
package com.example.billingapp_2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billingapp_2.data.cache.CacheManager
import com.example.billingapp_2.data.local.AppDatabase
import com.example.billingapp_2.model.ExpenseModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class ExpenseViewModel(
    private val authViewModel: AuthViewModel,
    private val database: AppDatabase,
    private val cacheManager: CacheManager
) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // --- Private StateFlows: The Core Data Engine ---

    // 1. A single source of truth for all expenses within the user's organization.
    private val _allOrgExpenses = MutableStateFlow<List<ExpenseModel>>(emptyList())

    // 2. Holds the ID of the agent currently being viewed in the detail screen.
    private val _viewingAgentId = MutableStateFlow<String?>(null)

    // Firestore listener registration for proper cleanup.
    private var expensesListener: ListenerRegistration? = null

    // --- Intermediate StateFlow for User-Specific Data ---
    // This aligns the architecture with other working ViewModels, ensuring reactivity.
    private val userSpecificExpenses: StateFlow<List<ExpenseModel>> = combine(
        _allOrgExpenses,
        authViewModel.userRole,
        authViewModel.currentUser
    ) { allExpenses, role, user ->
        if (user == null) {
            emptyList()
        } else {
            when (role) {
                "admin" -> allExpenses
                "agent" -> allExpenses.filter { it.agentId == user.uid }
                else -> emptyList()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Public StateFlows: Tailored Data for the UI ---

    // 1. For SummaryScreen: Calculates current month expenses from the user-specific list.
    val summaryScreenCurrentMonthExpenses: StateFlow<Double> = userSpecificExpenses.map { list ->
        val (start, end) = getMonthDateRange(0)
        list.filter { expense ->
            val expenseDate = expense.date.toDate()
            // CRITICAL: Exclude "collectedByBoss" type for summary calculations.
            expense.type != "collectedByBoss" && !expenseDate.before(start) && !expenseDate.after(end)
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 2. For SummaryScreen: Calculates previous month expenses from the user-specific list.
    val summaryScreenPreviousMonthExpenses: StateFlow<Double> = userSpecificExpenses.map { list ->
        val (start, end) = getMonthDateRange(-1) // Offset by -1 for the previous month
        list.filter { expense ->
            val expenseDate = expense.date.toDate()
            expense.type != "collectedByBoss" && !expenseDate.before(start) && !expenseDate.after(end)
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 3. For AgentDetailExpensesScreen: Provides a list of expenses for the selected agent.
    // This flow is derived directly from the master list.
    val viewingAgentExpenses: StateFlow<List<ExpenseModel>> = combine(
        _allOrgExpenses,
        _viewingAgentId
    ) { allExpenses, agentId ->
        if (agentId == null) {
            emptyList()
        } else {
            allExpenses.filter { it.agentId == agentId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Initialization and Data Fetching ---

    init {
        viewModelScope.launch {
            authViewModel.isAuthReady
                .filter { it }
                .map { authViewModel.adminUid.value }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { adminUid ->
                    listenForExpenses(adminUid)
                }
        }
    }

    private fun listenForExpenses(adminUid: String) {
        expensesListener?.remove()
        val query = db.collection("expenses").whereEqualTo("adminUid", adminUid)

        expensesListener = query.orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _allOrgExpenses.value = emptyList()
                    return@addSnapshotListener
                }
                _allOrgExpenses.value = snapshot?.toObjects<ExpenseModel>() ?: emptyList()
            }
    }

    // --- Public Functions for UI Interaction ---

    fun selectAgentForViewing(agentId: String?) {
        _viewingAgentId.value = agentId
    }

    fun addExpense(amount: Double, type: String, description: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val agentId = auth.currentUser?.uid
            val adminUid = authViewModel.adminUid.value

            if (agentId == null || adminUid == null) {
                onComplete(false)
                return@launch
            }

            val expense = ExpenseModel(
                id = UUID.randomUUID().toString(),
                adminUid = adminUid,
                agentId = agentId,
                date = Timestamp.now(),
                type = type,
                amount = amount,
                description = description
            )

            try {
                db.collection("expenses").document(expense.id).set(expense).await()
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun deleteExpense(expenseId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                db.collection("expenses").document(expenseId).delete().await()
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    // --- Utility and Cleanup ---

    private fun getMonthDateRange(monthOffset: Int): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, monthOffset)
        val start = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.time
        val end = calendar.apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.time
        return start to end
    }

    override fun onCleared() {
        super.onCleared()
        expensesListener?.remove()
    }
}
