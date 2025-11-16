

package com.example.billingapp.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.billingapp.model.ImportResult
import com.example.billingapp.ui.theme.AppColors
import com.example.billingapp.utils.FileTemplateGenerator
import com.example.billingapp.viewmodel.CustomerViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCustomersScreen(
    navController: NavController,
    customerViewModel: CustomerViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importResults by remember { mutableStateOf<List<ImportResult>?>(null) }
    var showResults by remember { mutableStateOf(false) }
    var isDownloadingTemplate by remember { mutableStateOf(false) }

    // Fixed file picker launcher with proper MIME types
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = getFileName(uri, context) ?: "Selected File"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Import Customers",
                        fontWeight = FontWeight.Bold,
                        color = AppColors.PrimaryText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.SecondaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.CardBackground
                )
            )
        },
        containerColor = AppColors.ScreenBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (!showResults) {
                // File selection and import UI
                ImportFileSelectionContent(
                    selectedFileUri = selectedFileUri,
                    selectedFileName = selectedFileName,
                    isImporting = isImporting,
                    isDownloadingTemplate = isDownloadingTemplate,
                    onDownloadTemplate = {
                        scope.launch {
                            isDownloadingTemplate = true
                            try {
                                val fileTemplateGenerator = FileTemplateGenerator(context)
                                val success = fileTemplateGenerator.downloadCsvTemplateWithShare()

                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Template downloaded! Check your downloads or shared files.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to generate template. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error generating template: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isDownloadingTemplate = false
                            }
                        }
                    },
                    onSelectFile = {
                        // Launch file picker with all supported types
                        try {
                            filePickerLauncher.launch("*/*")
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error opening file picker: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onImport = {
                        if (selectedFileUri != null) {
                            scope.launch {
                                isImporting = true
                                try {
                                    val csvContent = readFileContent(selectedFileUri!!, context)
                                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                                    // FIXED: Correctly handling the suspend function call and result
                                    val results = customerViewModel.importCustomersFromCsv(csvContent, currentUserId)
                                    importResults = results
                                    showResults = true

                                    val successCount = results.count { it is ImportResult.Success }
                                    val errorCount = results.count { it is ImportResult.Error }

                                    Toast.makeText(
                                        context,
                                        "Import completed! Successfully imported $successCount customers. $errorCount failed.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Import failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isImporting = false
                                }
                            }
                        }
                    }
                )
            } else {
                // Results display
                ImportResultsContent(
                    results = importResults ?: emptyList(),
                    onDismiss = {
                        showResults = false
                        importResults = null
                        selectedFileUri = null
                        selectedFileName = ""
                    },
                    onTryAgain = {
                        showResults = false
                        importResults = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportFileSelectionContent(
    selectedFileUri: Uri?,
    selectedFileName: String,
    isImporting: Boolean,
    isDownloadingTemplate: Boolean,
    onDownloadTemplate: () -> Unit,
    onSelectFile: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Step 1: Download Template Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AppColors.Accent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            text = "1",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        )
                    }
                    Text(
                        "Download Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.PrimaryText
                    )
                }

                Text(
                    "Download the CSV template file to see the required format",
                    color = AppColors.SecondaryText
                )

                Button(
                    onClick = onDownloadTemplate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloadingTemplate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.ButtonGreen
                    )
                ) {
                    if (isDownloadingTemplate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download CSV Template")
                    }
                }
            }
        }

        // Step 2: Fill Template Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AppColors.Accent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            text = "2",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        )
                    }
                    Text(
                        "Fill Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.PrimaryText
                    )
                }

                Text("• Open the downloaded CSV file", color = AppColors.SecondaryText)
                Text("• Delete sample rows (John Doe, Jane Smith)", color = AppColors.SecondaryText)
                Text("• Add your customer data", color = AppColors.SecondaryText)
                Text("• Save as CSV format", color = AppColors.SecondaryText)
            }
        }

        // Step 3: Select File Card
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AppColors.Accent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            text = "3",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        )
                    }
                    Text(
                        "Select CSV File",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.PrimaryText
                    )
                }

                if (selectedFileUri != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "File",
                            tint = AppColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedFileName,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.PrimaryText
                            )
                            Text(
                                "Ready to import",
                                fontSize = 12.sp,
                                color = AppColors.SecondaryText
                            )
                        }
                        IconButton(onClick = onSelectFile) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Change File",
                                tint = AppColors.SecondaryText
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onSelectFile,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.Accent
                        )
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Choose CSV File")
                    }
                }
            }
        }

        // Step 4: Import Button
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AppColors.Accent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            text = "4",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        )
                    }
                    Text(
                        "Import Customers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.PrimaryText
                    )
                }

                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFileUri != null && !isImporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.ButtonGreen
                    )
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importing...")
                    } else {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Customers")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportResultsContent(
    results: List<ImportResult>,
    onDismiss: () -> Unit,
    onTryAgain: () -> Unit
) {
    val successCount = results.count { it is ImportResult.Success }
    val errorCount = results.count { it is ImportResult.Error }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Import Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.PrimaryText
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "$successCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ButtonGreen
                        )
                        Text(
                            "Successful",
                            fontSize = 12.sp,
                            color = AppColors.SecondaryText
                        )
                    }

                    Column {
                        Text(
                            "$errorCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Failed",
                            fontSize = 12.sp,
                            color = AppColors.SecondaryText
                        )
                    }

                    Column {
                        Text(
                            "${results.size}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.PrimaryText
                        )
                        Text(
                            "Total",
                            fontSize = 12.sp,
                            color = AppColors.SecondaryText
                        )
                    }
                }
            }
        }

        // Results List
        if (results.isNotEmpty()) {
            Text(
                "Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.PrimaryText
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    ImportResultItem(result)
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onTryAgain,
                modifier = Modifier.weight(1f)
            ) {
                Text("Import Another")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.ButtonGreen
                )
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ImportResultItem(result: ImportResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is ImportResult.Success -> AppColors.CardBackground
                is ImportResult.Error -> AppColors.CardBackground
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                when (result) {
                    is ImportResult.Success -> Icons.Default.CheckCircle
                    is ImportResult.Error -> Icons.Default.Error
                },
                contentDescription = null,
                tint = when (result) {
                    is ImportResult.Success -> AppColors.ButtonGreen
                    is ImportResult.Error -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (result) {
                        is ImportResult.Success -> result.customerName
                        is ImportResult.Error -> "Import Error"
                    },
                    fontWeight = FontWeight.Medium,
                    color = AppColors.PrimaryText,
                    fontSize = 14.sp
                )

                if (result is ImportResult.Error) {
                    Text(
                        result.error,
                        color = AppColors.SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun getFileName(uri: Uri, context: android.content.Context): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)
                } else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun readFileContent(uri: Uri, context: android.content.Context): String {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.readText()
        }
    } ?: throw Exception("Failed to read file content")
}