

package com.example.billingapp_2.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp_2.ui.components.AppTopBar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val scope = rememberCoroutineScope()

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Profile Settings",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("User: ${currentUser?.email?: "N/A"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showChangePasswordDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }
            // Add other profile settings options here if needed
            // Button(
            //     onClick = { /* TODO: Implement Edit Profile */ },
            //     modifier = Modifier.fillMaxWidth()
            // ) {
            //     Text("Edit Profile")
            // }
        }

        if (showChangePasswordDialog) {
            AlertDialog(
                onDismissRequest = { showChangePasswordDialog = false },
                title = { Text("Change Password") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it },
                            label = { Text("Confirm New Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (currentPassword.isBlank() || newPassword.isBlank() || confirmNewPassword.isBlank()) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (newPassword!= confirmNewPassword) {
                            Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (newPassword.length < 6) { // Firebase minimum password length
                            Toast.makeText(context, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        scope.launch {
                            currentUser?.let { user ->
                                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                                user.reauthenticate(credential)
                                    .addOnSuccessListener {
                                        user.updatePassword(newPassword)
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                                showChangePasswordDialog = false
                                                currentPassword = ""
                                                newPassword = ""
                                                confirmNewPassword = ""
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Failed to update password: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }) {
                        Text("Change")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePasswordDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}