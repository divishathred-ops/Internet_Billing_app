package com.example.billingapp.utils

import android.util.Log

data class CsvCustomerData(
    val customerId: String? = null,
    val name: String,
    val phone: String,
    val area: String,
    val stbNumber: String,
    val recurringCharge: Double,
    val initialPayment: Double = 0.0
)

class CsvParser {

    companion object {
        private const val TAG = "CsvParser"

        fun parseCsvContent(csvContent: String): List<CsvCustomerData> {
            Log.d(TAG, "Starting CSV parsing. Content length: ${csvContent.length}")

            val lines = csvContent.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                Log.w(TAG, "No lines found in CSV content")
                return emptyList()
            }

            Log.d(TAG, "Found ${lines.size} lines in CSV")

            // Parse header to find column indices
            val header = parseCsvLine(lines.first())
            Log.d(TAG, "Header parsed: $header")

            val columnIndices = mapOf(
                "customerId" to findColumnIndex(header, listOf("customer id", "customerid", "id", "cust id")),
                "name" to findColumnIndex(header, listOf("customer name", "name", "customername", "full name")),
                "phone" to findColumnIndex(header, listOf("phone number", "phone", "phonenumber", "mobile", "contact")),
                "area" to findColumnIndex(header, listOf("billing area", "area", "billingarea", "zone", "location")),
                "stbNumber" to findColumnIndex(header, listOf("stb number", "stb", "stbnumber", "hardware details", "hardware", "set top box")),
                "recurringCharge" to findColumnIndex(header, listOf("monthly charge", "recurring charge", "monthlycharge", "charge", "amount", "fee")),
                "initialPayment" to findColumnIndex(header, listOf("initial balance", "initial payment", "initialbalance", "initialpayment", "balance", "starting balance"))
            )

            Log.d(TAG, "Column indices mapped: $columnIndices")

            val customers = mutableListOf<CsvCustomerData>()

            // Parse data rows (skip header)
            for ((index, line) in lines.drop(1).withIndex()) {
                try {
                    val row = parseCsvLine(line)
                    if (row.isNotEmpty() && !isEmptyRow(row)) {
                        Log.d(TAG, "Processing row ${index + 2}: $row")

                        val customer = CsvCustomerData(
                            customerId = columnIndices["customerId"]?.let {
                                if (it < row.size) row[it].takeIf { value -> value.isNotBlank() } else null
                            },
                            name = getRequiredValue(row, columnIndices["name"], "Customer Name"),
                            phone = getRequiredValue(row, columnIndices["phone"], "Phone Number").let { phone ->
                                // Clean phone number - remove spaces, dashes, parentheses
                                phone.replace(Regex("[\\s\\-\\(\\)\\+]"), "")
                            },
                            area = getRequiredValue(row, columnIndices["area"], "Area").uppercase().trim(),
                            stbNumber = getRequiredValue(row, columnIndices["stbNumber"], "STB Number").trim(),
                            recurringCharge = parseDoubleValue(
                                getRequiredValue(row, columnIndices["recurringCharge"], "Monthly Charge")
                            ),
                            initialPayment = columnIndices["initialPayment"]?.let {
                                if (it < row.size && row[it].isNotBlank()) {
                                    parseDoubleValue(row[it])
                                } else 0.0
                            } ?: 0.0
                        )
                        customers.add(customer)
                        Log.d(TAG, "Successfully parsed customer: ${customer.name}")
                    } else {
                        Log.d(TAG, "Skipping empty row at line ${index + 2}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing row ${index + 2}: ${e.message}", e)
                    // Skip invalid rows but continue processing
                    continue
                }
            }

            Log.d(TAG, "Successfully parsed ${customers.size} customers from CSV")
            return customers
        }

        private fun parseCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            var current = StringBuilder()
            var inQuotes = false
            var i = 0

            while (i < line.length) {
                val char = line[i]
                when {
                    char == '"' && !inQuotes -> {
                        inQuotes = true
                    }
                    char == '"' && inQuotes -> {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            // Escaped quote within quoted field
                            current.append('"')
                            i++ // Skip next quote
                        } else {
                            // End of quoted field
                            inQuotes = false
                        }
                    }
                    char == ',' && !inQuotes -> {
                        result.add(current.toString().trim())
                        current = StringBuilder()
                    }
                    else -> {
                        current.append(char)
                    }
                }
                i++
            }

            result.add(current.toString().trim())
            return result
        }

        private fun findColumnIndex(header: List<String>, possibleNames: List<String>): Int? {
            possibleNames.forEach { searchName ->
                header.forEachIndexed { index, columnName ->
                    if (columnName.lowercase().trim().contains(searchName.lowercase())) {
                        Log.d(TAG, "Found column '$searchName' at index $index (header: '${columnName}')")
                        return index
                    }
                }
            }

            // Fallback: try exact matches
            possibleNames.forEach { searchName ->
                header.forEachIndexed { index, columnName ->
                    if (columnName.lowercase().trim() == searchName.lowercase()) {
                        Log.d(TAG, "Found exact column match '$searchName' at index $index")
                        return index
                    }
                }
            }

            Log.w(TAG, "Could not find column for any of: $possibleNames")
            return null
        }

        private fun getRequiredValue(row: List<String>, columnIndex: Int?, fieldName: String): String {
            if (columnIndex == null) {
                throw IllegalArgumentException("Required column '$fieldName' not found in CSV header")
            }
            if (columnIndex >= row.size) {
                throw IllegalArgumentException("Row does not have enough columns for field '$fieldName' (expected column $columnIndex)")
            }

            val value = row[columnIndex].trim()
            if (value.isBlank()) {
                throw IllegalArgumentException("Required field '$fieldName' is empty")
            }
            return value
        }

        private fun parseDoubleValue(value: String): Double {
            val cleanValue = value.trim()
                .replace(",", "") // Remove comma separators
                .replace("₹", "") // Remove currency symbols
                .replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .trim()

            return try {
                cleanValue.toDouble()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid number format: '$value'")
            }
        }

        private fun isEmptyRow(row: List<String>): Boolean {
            return row.all { it.isBlank() }
        }
    }
}