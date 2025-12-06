package com.example.billingapp_2.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp_2.model.UserModel
import com.example.billingapp_2.ui.components.AppTopBar
import com.example.billingapp_2.viewmodel.AgentViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPermissionsScreen(
    navController: NavController,
    agentViewModel: AgentViewModel,
    agentUid: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agents by agentViewModel.agents.collectAsState()
    val allBillingAreas by agentViewModel.allBillingAreas.collectAsState()

    var selectedPermissions by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var selectedAreas by remember { mutableStateOf(emptyList<String>()) }
    val agent = remember(agentUid, agents) { agents.firstOrNull { it.uid == agentUid } }

    LaunchedEffect(agent) {
        agent?.let {
            selectedPermissions = it.permissions
            selectedAreas = it.assignedAreas
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Permissions for ${agent?.name ?: "Agent"}",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        if (agent == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp) // Increased spacing
            ) {
                AgentInfoSection(agent)
                PermissionsSection(
                    selectedPermissions = selectedPermissions,
                    onPermissionChange = { key, value ->
                        selectedPermissions = selectedPermissions.toMutableMap().apply { put(key, value) }
                    }
                )
                BillingAreasSection(
                    allAreas = allBillingAreas,
                    selectedAreas = selectedAreas,
                    onAreaChange = { area, selected ->
                        selectedAreas = if (selected) selectedAreas + area else selectedAreas - area
                    }
                )
                SaveButton(
                    onClick = {
                        scope.launch {
                            agentViewModel.updateAgentPermissionsAndAreas(
                                agentUid,
                                selectedPermissions,
                                selectedAreas
                            ) { success ->
                                if (success) {
                                    Toast.makeText(context, "Permissions updated!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AgentInfoSection(agent: UserModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Agent Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider()
            InfoRow(label = "Name", value = agent.name)
            InfoRow(label = "Phone", value = agent.phone)
            InfoRow(label = "Role", value = agent.role)
        }
    }
}

@Composable
private fun PermissionsSection(
    selectedPermissions: Map<String, Boolean>,
    onPermissionChange: (String, Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // The master list of all possible permissions in the app
            val permissions = listOf(
                "Add Customers" to "addCustomer",
                "Edit Customers" to "editCustomer",
                "Delete Customers" to "deleteCustomer",
                "Collect Payments" to "collectPayment",
                "Delete Transactions" to "deleteTransaction",
                "Renew Subscriptions" to "renewSubscription",
                "Change Balance" to "changeBalance",
                "Edit Hardware" to "editHardware", // <-- ADDED
                "Export Data" to "exportData"
            ).sortedBy { it.first } // Sort alphabetically for consistency

            permissions.forEach { (label, key) ->
                PermissionCheckbox(
                    label = label,
                    isChecked = selectedPermissions[key] ?: false,
                    onCheckedChange = { onPermissionChange(key, it) }
                )
            }
        }
    }
}

@Composable
private fun BillingAreasSection(
    allAreas: List<String>,
    selectedAreas: List<String>,
    onAreaChange: (String, Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Assigned Billing Areas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (allAreas.isEmpty()) {
                Text("No billing areas found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                // Use a fixed height to make the list scrollable within the Column
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(allAreas.sorted()) { area ->
                        AreaCheckbox(
                            area = area,
                            isChecked = selectedAreas.contains(area),
                            onCheckedChange = { onAreaChange(area, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckbox(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AreaCheckbox(
    area: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(area, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text("Save Changes", style = MaterialTheme.typography.titleMedium)
    }
}
