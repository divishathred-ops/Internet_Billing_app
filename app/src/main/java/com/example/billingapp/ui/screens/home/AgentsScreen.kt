package com.example.billingapp.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.billingapp.model.UserModel
import com.example.billingapp.ui.components.AppTopBar
import com.example.billingapp.viewmodel.AgentViewModel
import com.example.billingapp.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    navController: NavController,
    agentViewModel: AgentViewModel,
    authViewModel: AuthViewModel
) {
    val agents by agentViewModel.agents.collectAsState()
    var showAddAgentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Manage Agents",
                onBackClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddAgentDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Agent")
            }
        }
    ) { padding ->
        if (agents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No agents found. Add one to get started.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(agents, key = { it.uid }) { agent ->
                    AgentCard(
                        agent = agent,
                        onDelete = {
                            agentViewModel.deleteAgent(agent.uid) { success, message ->
                                if (success) {
                                    Toast.makeText(context, "Agent deleted", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onClick = {
                            navController.navigate("agentPermissions/${agent.uid}")
                        }
                    )
                }
            }
        }
    }

    if (showAddAgentDialog) {
        AddAgentDialog(
            onDismiss = { showAddAgentDialog = false },
            agentViewModel = agentViewModel,
            authViewModel = authViewModel
        )
    }
}

@Composable
private fun AgentCard(agent: UserModel, onDelete: () -> Unit, onClick: () -> Unit) {
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Agent", tint = MaterialTheme.colorScheme.error)
            }
            Text(
                text = "${agent.assignedAreas.size} areas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AddAgentDialog(
    onDismiss: () -> Unit,
    agentViewModel: AgentViewModel,
    authViewModel: AuthViewModel
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showAdminPassDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (isLoading) {
        Dialog(onDismissRequest = {}) {
            Card {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Creating Agent...")
                }
            }
        }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) phone = it }, label = { Text("10-Digit Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "") } })
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm Password") }, singleLine = true, visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Icon(if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "") } })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.length != 10 || password.length < 6) {
                        Toast.makeText(context, "Please fill all fields correctly (password min 6 chars).", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (password != confirmPassword) {
                        Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    showAdminPassDialog = true
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAdminPassDialog) {
        AdminPasswordPromptDialog(
            onDismiss = { showAdminPassDialog = false },
            onConfirm = { adminPassword ->
                showAdminPassDialog = false
                isLoading = true
                agentViewModel.orchestrateAgentCreation(name, phone, password, adminPassword, authViewModel) { success, message ->
                    isLoading = false
                    if (success) {
                        Toast.makeText(context, "Agent created successfully!", Toast.LENGTH_SHORT).show()
                        onDismiss() // Close the main dialog on success
                    } else {
                        Toast.makeText(context, "Failed: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun AdminPasswordPromptDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var adminPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Verification") },
        text = { OutlinedTextField(value = adminPassword, onValueChange = { adminPassword = it }, label = { Text("Enter Admin Password") }, singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "") } }) },
        confirmButton = { Button(onClick = { onConfirm(adminPassword) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
