
package com.example.billingapp_2.ui.screens.home
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp_2.model.UserModel
import com.example.billingapp_2.viewmodel.AgentViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentExpensesScreen(
    navController: NavController,
    agentViewModel: AgentViewModel
) {
    val agents by agentViewModel.agents.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents Expenses") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (agents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No agents found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(agents, key = { it.uid }) { agent ->
                    AgentExpenseCard(
                        agent = agent,
                        onClick = {
                            navController.navigate("agentExpenseDetail/${agent.uid}/${agent.name}")
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun AgentExpenseCard(agent: UserModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.titleMedium)
                Text(agent.phone, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
