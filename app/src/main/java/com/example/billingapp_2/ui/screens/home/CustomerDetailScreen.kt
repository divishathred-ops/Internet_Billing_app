
package com.example.billingapp_2.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.billingapp_2.model.CustomerModel
import com.example.billingapp_2.model.TransactionModel
import com.example.billingapp_2.ui.theme.AppColors
import com.example.billingapp_2.ui.theme.BillingAppTheme
import com.example.billingapp_2.utils.PdfReceiptGenerator
import com.example.billingapp_2.viewmodel.AuthViewModel
import com.example.billingapp_2.viewmodel.CustomerViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    navController: NavController,
    customerId: String,
    authViewModel: AuthViewModel,
    customerViewModel: CustomerViewModel
) {
    val context = LocalContext.current
    val pdfReceiptGenerator = remember { PdfReceiptGenerator(context) }

    val customer by customerViewModel.selectedCustomer.collectAsState()
    val permissions by authViewModel.userPermissions.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoading by customerViewModel.isLoading.collectAsState()
    val userRole by authViewModel.userRole.collectAsState()

    // Dialog states
    var showCollectDialog by remember { mutableStateOf(false) }
    var showGenerateBillDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showChangeBalanceDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showEditHardwareDialog by remember { mutableStateOf(false) }
    val selectedTransactions by customerViewModel.selectedCustomerTransactions.collectAsState()
    fun generateAndShareReceipt(
        customer: CustomerModel,
        transactions: List<TransactionModel>
    ) {  data class ReceiptData(val prevBalance: Double, val paidAmount: Double, val netAmount: Double, val remainingAmount: Double, val mode: String)
        try {
            val receiptNumber = "RCP${System.currentTimeMillis()}"
            val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val currentTime = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

            // Find latest payment
            val latestPayment = transactions
                .filter { it.type == "collectedPayment" }
                .maxByOrNull { it.date.toDate() }

            val (prevBalance, paidAmount, netAmount, remainingAmount, mode) = if (latestPayment != null) {
                val paid = latestPayment.amount
                val currentBal = customer.balance
                val prevBal = currentBal + paid
                ReceiptData(prevBal, paid, currentBal, currentBal, "Cash Payment")
            } else {
                val currentBal = customer.balance
                ReceiptData(currentBal, 0.0, currentBal, currentBal, "Balance Statement")
            }

            val receiptFile = pdfReceiptGenerator.generateReceipt(
                customerName = customer.name,
                receiptNumber = receiptNumber,
                recordTime = currentTime,
                billDate = currentDate,
                prevBalance = prevBalance,
                paidAmount = paidAmount,
                netAmount = netAmount,
                remainingAmount = remainingAmount,
                mode = mode
            )

            pdfReceiptGenerator.shareReceipt(receiptFile)

            val message = if (paidAmount > 0.0) {
                "Payment receipt generated (₹${String.format("%.2f", paidAmount)})!"
            } else {
                "Balance statement generated!"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate receipt: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("CustomerDetailScreen", "Receipt generation failed", e)
        }
    }



    // Helper data class



    LaunchedEffect(customerId) {
        customerViewModel.selectCustomer(customerId)
    }

    DisposableEffect(Unit) {
        onDispose {
            customerViewModel.resetCustomerSelection()
        }
    }


    BillingAppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Customer Details", fontWeight = FontWeight.Bold, color = AppColors.PrimaryText) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.SecondaryText)
                        }
                    },
                    actions = {
                        if (permissions["editCustomer"] == true || userRole == "admin") {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = AppColors.SecondaryText)
                            }
                        }
                        if (permissions["deleteCustomer"] == true || userRole == "admin") {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Customer", tint = AppColors.SecondaryText)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.CardBackground)
                )
            },
            containerColor = AppColors.ScreenBackground
        ) { padding ->
            if (isLoading && customer == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                customer?.let { cust ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = cust.name,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.PrimaryText,
                            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                        )

                        InfoCard(cust)
                        ActionButtons(
                            permissions = permissions,
                            userRole = userRole,
                            onCollectClick = { showCollectDialog = true },
                            onGenerateBillClick = { showGenerateBillDialog = true },
                            onChangeBalanceClick = { showChangeBalanceDialog = true },
                            onShareReceiptClick = {
                                generateAndShareReceipt(cust, selectedTransactions)  // ← Fixed!
                            }
                        )




                        Spacer(Modifier.height(24.dp))

                        // New Expandable Hardware Details Card
                        ExpandableHardwareCard(
                            stbNumber = cust.stbNumber,
                            hasPermission = (permissions["editHardware"] == true || userRole == "admin"),
                            onEditClick = { showEditHardwareDialog = true }
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedActionButton(
                            text = "View Balance Sheet",
                            icon = Icons.Default.Description,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate("balanceSheet/${cust.id}") }
                        )

                        Spacer(Modifier.height(16.dp))
                    }

                    // --- DIALOGS ---
                    if (showCollectDialog) {
                        CollectPaymentDialog(isLoading, userRole ?: "agent", { showCollectDialog = false }) { amount, date ->
                            customerViewModel.collectPayment(cust.id, cust.name, amount, currentUser?.uid ?: "", date) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Payment collected!", Toast.LENGTH_SHORT).show()
                                    showCollectDialog = false
                                } else {
                                    Toast.makeText(context, "Failed: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    if (showGenerateBillDialog) {
                        GenerateBillDialog(isLoading, { showGenerateBillDialog = false }) { amount, description, period ->
                            customerViewModel.generateBill(cust.id, cust.name, amount, description, period, currentUser?.uid ?: "") { success ->
                                if (success) {
                                    Toast.makeText(context, "Bill generated!", Toast.LENGTH_SHORT).show()
                                    showGenerateBillDialog = false
                                } else {
                                    Toast.makeText(context, "Failed to generate bill", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    if (showChangeBalanceDialog) {
                        ChangeBalanceDialog(cust.balance, { showChangeBalanceDialog = false }) { newBalance ->
                            customerViewModel.changeBalance(cust.id, newBalance, currentUser?.uid ?: "") { success ->
                                if (success) Toast.makeText(context, "Balance updated!", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Failed to update balance", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (showEditDialog) {
                        EditCustomerDialog(cust, { showEditDialog = false }) { name, phone, area ->
                            customerViewModel.updateCustomerDetails(cust.id, name, phone, area) { success ->
                                if (success) {
                                    Toast.makeText(context, "Customer updated!", Toast.LENGTH_SHORT).show()
                                    showEditDialog = false
                                } else Toast.makeText(context, "Failed to update customer.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (showEditHardwareDialog) {
                        EditHardwareDetailsDialog(
                            currentStbNumber = cust.stbNumber,
                            onDismiss = { showEditHardwareDialog = false },
                            onConfirm = { newStbNumber ->
                                // Note: This function signature in the ViewModel must be updated
                                customerViewModel.updateHardwareDetails(cust.id, newStbNumber) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Hardware details updated!", Toast.LENGTH_SHORT).show()
                                        showEditHardwareDialog = false
                                    } else {
                                        Toast.makeText(context, "Update failed.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Confirm Deletion") },
                            text = { Text("Are you sure you want to delete this customer? This action cannot be undone.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        customerViewModel.deleteCustomer(cust.id) { success, error ->
                                            if (success) {
                                                Toast.makeText(context, "Customer deleted.", Toast.LENGTH_SHORT).show()
                                                navController.popBackStack()
                                            } else {
                                                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        showDeleteDialog = false
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableHardwareCard(
    stbNumber: String,
    hasPermission: Boolean,
    onEditClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hardware Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Divider()
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("STB Number", stbNumber, valueColor = AppColors.Accent)
                        Row {
                            if (hasPermission) {
                                IconButton(onClick = onEditClick) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Hardware", tint = AppColors.SecondaryText)
                                }
                            }
                            IconButton(onClick = {
                                val detailsText = "STB Number: $stbNumber"
                                val clip = ClipData.newPlainText("Hardware Details", detailsText)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Details copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Details", tint = AppColors.SecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EditHardwareDetailsDialog(
    currentStbNumber: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var stbNumber by remember { mutableStateOf(currentStbNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Hardware Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = stbNumber,
                    onValueChange = { stbNumber = it },
                    label = { Text("STB Number") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(stbNumber)
            }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun InfoCard(customer: CustomerModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Customer ID", customer.customerId, Modifier.weight(1f))
                DetailItem("Phone", customer.phone, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Area", customer.area, Modifier.weight(1f))
                DetailItem("Balance", "₹${"%.2f".format(customer.balance)}", Modifier.weight(1f), valueColor = AppColors.BalanceText)
            }
            // NEW: Monthly Recurring Charge field
            Row(Modifier.fillMaxWidth()) {
                DetailItem(
                    "Monthly Charge",
                    "₹${"%.2f".format(customer.recurringCharge)}",
                    Modifier.weight(1f),
                    valueColor = AppColors.Accent
                )
                Spacer(Modifier.weight(1f)) // Empty space for second column
            }
        }
    }
}
@Composable
private fun DetailItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = AppColors.PrimaryText) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 14.sp, color = AppColors.SecondaryText, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 16.sp, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionButtons(
    permissions: Map<String, Boolean>,
    userRole: String?,
    onCollectClick: () -> Unit,
    onGenerateBillClick: () -> Unit,
    onChangeBalanceClick: () -> Unit,
    onShareReceiptClick: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (permissions["collectPayment"] == true || userRole == "admin") {
                ActionButton(
                    "Collect Payment",
                    Icons.Default.Payments,
                    AppColors.ButtonGreen,
                    Modifier.weight(1f),
                    onCollectClick
                )
            }
            if (permissions["renewSubscription"] == true || userRole == "admin") {
                ActionButton(
                    "Generate Bill",
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    AppColors.ButtonBlue,
                    Modifier.weight(1f),
                    onGenerateBillClick
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (permissions["changeBalance"] == true || userRole == "admin") {
                ActionButton(
                    "Change Balance",
                    Icons.Default.CurrencyRupee,
                    AppColors.ButtonOrange,
                    Modifier.weight(1f),
                    onChangeBalanceClick
                )
            }
            ActionButton(
                "Share Receipt",
                Icons.Default.Share,
                AppColors.ButtonPurple,
                Modifier.weight(1f),
                onClick = {
                    // Generate and share receipt directly
                    onShareReceiptClick()
                }
            )
        }
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, backgroundColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = AppColors.WhiteText),
        modifier = modifier.height(60.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun OutlinedActionButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.PrimaryText),
        border = BorderStroke(1.dp, AppColors.ButtonBorder),
        modifier = modifier.height(60.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun CollectPaymentDialog(
    isLoading: Boolean,
    userRole: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, Timestamp) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current
    var selectedDateTime by remember { mutableStateOf(Calendar.getInstance()) }

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
            selectedDateTime.set(Calendar.MINUTE, minute)
        },
        selectedDateTime.get(Calendar.HOUR_OF_DAY),
        selectedDateTime.get(Calendar.MINUTE),
        false
    )

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            selectedDateTime.set(year, month, dayOfMonth)
            if (userRole == "admin") {
                timePickerDialog.show()
            }
        },
        selectedDateTime.get(Calendar.YEAR),
        selectedDateTime.get(Calendar.MONTH),
        selectedDateTime.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collect Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Received") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (userRole == "admin") {
                    Spacer(Modifier.height(8.dp))
                    Text("Payment Date & Time (Admin Only)", style = MaterialTheme.typography.labelMedium)
                    Button(onClick = { datePickerDialog.show() }) {
                        Text(
                            "Date: ${
                                SimpleDateFormat(
                                    "dd/MM/yyyy HH:mm",
                                    Locale.US
                                ).format(selectedDateTime.time)
                            }"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull()
                    if (parsedAmount == null || parsedAmount <= 0) {
                        Toast.makeText(context, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                    } else {
                        val timestamp = if (userRole == "admin") {
                            Timestamp(selectedDateTime.time)
                        } else {
                            Timestamp.now()
                        }
                        onConfirm(parsedAmount, timestamp)
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") } }
    )
}

@Composable
private fun EditCustomerDialog(
    customer: CustomerModel,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, area: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phone) }
    var area by remember { mutableStateOf(customer.area) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("Billing Area") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || phone.isBlank() || area.isBlank()) {
                    Toast.makeText(context, "All fields are required.", Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(name, phone, area.uppercase())
                }
            }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun GenerateBillDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showMonthPicker by remember { mutableStateOf(false) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }

    val monthYearText = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, selectedMonth, 1)
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYear = selectedYear,
            initialMonth = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { year, month ->
                selectedYear = year
                selectedMonth = month
                showMonthPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate New Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Bill Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Button(onClick = { showMonthPicker = true }) {
                    Text("Billing Period: $monthYearText")
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull()
                    if (parsedAmount == null || parsedAmount <= 0) {
                        Toast.makeText(context, "Enter a valid amount.", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(parsedAmount, description, monthYearText)
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Generate")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") } }
    )
}

@Composable
private fun ChangeBalanceDialog(
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newBalance by remember { mutableStateOf(currentBalance.toString()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Balance") },
        text = {
            Column {
                Text("Current Balance: ₹${"%.2f".format(currentBalance)}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newBalance,
                    onValueChange = { newBalance = it },
                    label = { Text("New Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val balanceValue = newBalance.toDoubleOrNull()
                if (balanceValue != null) {
                    onConfirm(balanceValue)
                    onDismiss()
                } else {
                    Toast.makeText(context, "Invalid balance amount", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val months = remember {
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        (0..11).map {
            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, it)
            monthFormat.format(cal.time)
        }
    }
    var year by remember { mutableStateOf(initialYear) }
    var month by remember { mutableStateOf(initialMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Month and Year") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPicker(
                    range = (2020..2030),
                    currentValue = year,
                    onValueChange = { year = it }
                )
                NumberPicker(
                    range = (0..11),
                    currentValue = month,
                    onValueChange = { month = it },
                    displayValues = months
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(year, month) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NumberPicker(
    range: IntRange,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    displayValues: List<String>? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { if (currentValue > range.first) onValueChange(currentValue - 1) }) {
            Icon(Icons.Default.ArrowDropUp, contentDescription = "Increase")
        }
        Text(
            text = displayValues?.getOrNull(currentValue) ?: currentValue.toString(),
            style = MaterialTheme.typography.headlineMedium
        )
        IconButton(onClick = { if (currentValue < range.last) onValueChange(currentValue + 1) }) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Decrease")
        }
    }
}
