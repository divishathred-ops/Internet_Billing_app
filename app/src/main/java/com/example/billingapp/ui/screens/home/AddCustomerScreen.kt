package com.example.billingapp.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp.model.CustomerModel
import com.example.billingapp.viewmodel.AgentViewModel
import com.example.billingapp.viewmodel.CustomerViewModel
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerScreen(
    navController: NavController,
    customerViewModel: CustomerViewModel,
    agentViewModel: AgentViewModel
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var stbNumber by remember { mutableStateOf("") }
    var recurringCharge by remember { mutableStateOf("") }
    var initialPayment by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAreaDropdown by remember { mutableStateOf(false) }
    val allAreas by agentViewModel.allBillingAreas.collectAsState()

    // This calculation will react correctly when `area` state changes.
    val filteredAreas = remember(area, allAreas) {
        if (area.length >= 2) {
            allAreas.filter { it.contains(area, ignoreCase = true) }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        agentViewModel.fetchAllBillingAreas()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Customer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = customerId,
                    onValueChange = { customerId = it },
                    label = { Text("Customer ID") },
                    modifier = Modifier.weight(0.7f),
                    enabled = false
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        var newId: String
                        do {
                            newId = (100000000 + Random.nextInt(900000000)).toString()
                        } while (customerViewModel.customers.value.any { it.customerId == newId })
                        customerId = newId
                    },
                    modifier = Modifier.weight(0.3f)
                ) {
                    Text("Generate")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Customer Name*") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number*") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            // FIXED BILLING AREA FIELD
            ExposedDropdownMenuBox(
                expanded = showAreaDropdown,
                onExpandedChange = {
                    // Prevent manual expansion when there's not enough text
                    if (it && area.length < 2) {
                        showAreaDropdown = false
                    } else {
                        showAreaDropdown = it
                    }
                }
            ) {
                OutlinedTextField(
                    value = area,
                    onValueChange = { newText ->
                        area = newText
                        // *** FIX IS HERE ***
                        // Check against the full list (`allAreas`) with the new text.
                        // This avoids the state timing issue and fixes both the delay and the UI lock.
                        showAreaDropdown = if (newText.length >= 2) {
                            allAreas.any { areaItem -> areaItem.contains(newText, ignoreCase = true) }
                        } else {
                            false
                        }
                    },
                    label = { Text("Billing Area*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        // The icon will appear correctly based on the recomposed `filteredAreas`
                        if (filteredAreas.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = showAreaDropdown
                            )
                        }
                    },
                    singleLine = true
                )

                // This part is only composed when there are items to show, which is efficient.
                if (filteredAreas.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = showAreaDropdown,
                        onDismissRequest = { showAreaDropdown = false }
                    ) {
                        filteredAreas.take(4).forEach { areaOption ->
                            DropdownMenuItem(
                                text = { Text(areaOption) },
                                onClick = {
                                    area = areaOption
                                    showAreaDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = stbNumber,
                onValueChange = { stbNumber = it },
                label = { Text("STB Number*") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = recurringCharge,
                onValueChange = { recurringCharge = it },
                label = { Text("Monthly Charge*") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = initialPayment,
                onValueChange = { initialPayment = it },
                label = { Text("Initial Balance*") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val charge = recurringCharge.toDoubleOrNull()
                    val payment = initialPayment.toDoubleOrNull()
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                    if (name.isBlank() || phone.isBlank() || area.isBlank() ||
                        stbNumber.isBlank() || charge == null || payment == null ||
                        customerId.isBlank() || currentUserId == null
                    ) {
                        Toast.makeText(context, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    val newCustomerId = UUID.randomUUID().toString()
                    val customer = CustomerModel(
                        id = newCustomerId,
                        customerId = customerId,
                        name = name.trim(),
                        phone = phone.trim(),
                        area = area.trim().uppercase(),
                        stbNumber = stbNumber.trim(),
                        recurringCharge = charge,
                        balance = payment, // Set balance to initial payment
                        assignedAgentId = currentUserId
                    )

                    // FIXED: Using the correct callback signature that matches ViewModel
                    customerViewModel.addCustomer(customer, payment, currentUserId) { success ->
                        if (success) {
                            Toast.makeText(context, "Customer added successfully!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } else {
                            Toast.makeText(context, "Failed to add customer.", Toast.LENGTH_SHORT).show()
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Create Customer")
                }
            }
        }
    }
}