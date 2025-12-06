package com.example.billingapp_2.utils

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

import com.example.billingapp_2.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123

class FileTemplateGenerator(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "template_download_channel"
        private const val NOTIFICATION_ID = 1001
    }

    /**
     * Creates the content for the CSV template file.

     * @return A string containing the CSV header and sample data.
     */
    private fun createCsvTemplateContent(): String {
        val header = "Customer ID,Customer Name,Phone Number,Billing Area,STB Number,Monthly Charge,Initial Balance"
        val sample1 = "123456789,John Doe,9876543210,DOWNTOWN,STB001,299.00,299.00"
        val sample2 = ",Jane Smith,9123456789,UPTOWN,STB002,399.00,0.00"
        val sample3 = "987654321,Bob Johnson,9555123456,SUBURB,STB003,499.00,150.00"
        return listOf(header, sample1, sample2, sample3).joinToString("\n")
    }

    /**
     * Saves the CSV template into the Downloads directory and posts a notification with a Share action.
     * This function handles different Android versions to ensure correct file saving and notification behavior.
     * @return True if the file was saved and notification was posted successfully, false otherwise.
     */
    fun downloadCsvTemplateWithShare(): Boolean {
        // For Android 13 (TIRAMISU) and above, check for notification permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
                return false // Abort if permission is not granted.
            }
        }
        ensureNotificationChannel() // Ensure the notification channel exists.

        val fileName = "customer_import_template_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val content = createCsvTemplateContent()

        val fileUri: Uri?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (Q) and above, use MediaStore for better file management.
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1) // Mark file as pending
            }
            val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)

            if (uri == null) return false

            try {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0) // Mark file as complete
                resolver.update(uri, values, null, null)
                fileUri = uri
            } catch (e: Exception) {
                e.printStackTrace()
                // Clean up the pending entry if something goes wrong
                resolver.delete(uri, null, null)
                return false
            }
        } else {
            // For older versions (below Android 10), save to public Downloads directory directly.
            // Note: This requires WRITE_EXTERNAL_STORAGE permission.
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(content)

                // Get a content URI using FileProvider for sharing.
                fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider", // This MUST match the authority in AndroidManifest.xml
                    file
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        // Post the notification with the URI of the saved file.
        if (fileUri != null) {
            postNotificationWithShare(fileUri, fileName)
            return true
        }

        return false
    }

    /**
     * Creates the notification channel for template downloads on Android Oreo and higher.
     * The channel is configured with high importance to allow for heads-up notifications.
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Template Downloads",
                    NotificationManager.IMPORTANCE_HIGH // Crucial for heads-up notifications
                ).apply {
                    description = "Alerts when CSV template is downloaded"
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Builds and displays a notification indicating the template has been downloaded.
     * The notification includes a "Share" action.
     * @param fileUri The URI of the downloaded file.
     * @param fileName The name of the downloaded file.
     */
    private fun postNotificationWithShare(fileUri: Uri, fileName: String) {
        // Intent for the "Share" action
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val sharePendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent.createChooser(shareIntent, "Share CSV Template"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.vantix_logo) // Replace with your app's icon
            .setContentTitle("Template Downloaded")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Set priority for heads-up
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
            .build()

        // Use NotificationManagerCompat to show the notification
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notification)
        }
    }
}
