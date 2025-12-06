package com.example.billingapp_2.ui.components

import android.app.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (start: Date, end: Date) -> Unit
) {
    val context = LocalContext.current

    // A DisposableEffect is used to show and manage the lifecycle of the non-composable DatePickerDialogs.
    DisposableEffect(Unit) {
        val startCalendar = Calendar.getInstance()
        val endCalendar = Calendar.getInstance()

        // This flag helps determine if the initial dialog was dismissed before a date was chosen.
        var startDateSelected = false

        // --- Start Date Picker ---
        val startDatePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                startDateSelected = true
                val selectedStart = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }

                // --- End Date Picker (shown after start date is selected) ---
                val endDatePicker = DatePickerDialog(
                    context,
                    { _, endYear, endMonth, endDay ->
                        val selectedEnd = Calendar.getInstance().apply {
                            set(endYear, endMonth, endDay)
                        }

                        // Adjust the end date to cover the full day, preserving original logic
                        val adjustedEnd = Calendar.getInstance().apply {
                            time = selectedEnd.time
                            add(Calendar.DAY_OF_MONTH, 1)
                            add(Calendar.MILLISECOND, -1)
                        }.time

                        // Confirm the selection with both dates
                        onConfirm(selectedStart.time, adjustedEnd)
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
                )

                // When the end date picker is dismissed (either by OK or Cancel),
                // the entire operation is considered over.
                endDatePicker.setOnDismissListener { onDismiss() }
                endDatePicker.show()
            },
            startCalendar.get(Calendar.YEAR),
            startCalendar.get(Calendar.MONTH),
            startCalendar.get(Calendar.DAY_OF_MONTH)
        )

        // If the start date picker is dismissed without a date being selected,
        // cancel the entire operation.
        startDatePicker.setOnDismissListener {
            if (!startDateSelected) {
                onDismiss()
            }
        }

        // Show the first dialog to begin the process.
        startDatePicker.show()

        // Cleanup: Dismiss the dialog if the composable leaves the composition.
        onDispose {
            if (startDatePicker.isShowing) {
                startDatePicker.dismiss()
            }
        }
    }
}