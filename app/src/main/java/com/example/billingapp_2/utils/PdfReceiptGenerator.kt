package com.example.billingapp_2.utils

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.TextPaint
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PdfReceiptGenerator( val context: Context) {

    fun generateReceipt(
        customerName: String,
        receiptNumber: String,
        recordTime: String,
        billDate: String,
        prevBalance: Double,
        paidAmount: Double,
        netAmount: Double,
        remainingAmount: Double,
        mode: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // --- Page Border ---
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 3f
        }
        val borderRect = RectF(30f, 30f, 565f, 812f)
        canvas.drawRect(borderRect, borderPaint)

        // --- Paints Setup (Increased font sizes) ---
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f  // Increased from 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val normalCenterPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f  // Increased from 12f
            textAlign = Paint.Align.CENTER
        }
        val normalLeftPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f  // Increased from 12f
            textAlign = Paint.Align.LEFT
        }
        val boldLeftPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f  // Increased from 12f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val amountPaint = Paint(normalLeftPaint).apply {
            textAlign = Paint.Align.RIGHT
            textSize = 18f  // Larger for amounts
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1.5f
        }

        // --- Coordinates (Tighter spacing) ---
        val centerX = pageInfo.pageWidth / 2f
        val leftMargin = 50f
        val rightMargin = pageInfo.pageWidth - 50f
        var currentY = 80f

        // --- Draw Header (More compact) ---
        canvas.drawText("Sri Sai Broad Band Network", centerX, currentY, titlePaint)
        currentY += 25f  // Reduced spacing

        val contactPaint = Paint(normalCenterPaint).apply { textSize = 14f }
        canvas.drawText("Menda Magbul Moses 57-1-18, Santhapet, Ongole", centerX, currentY, contactPaint)
        currentY += 20f
        canvas.drawText("9848748883", centerX, currentY, contactPaint)
        currentY += 30f

        // Horizontal line
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, linePaint)
        currentY += 30f  // Reduced spacing

        // --- Draw Customer Details (Tighter layout) ---
        val labelX = leftMargin + 10f
        val valueX = leftMargin + 150f  // Wider for values

        fun drawRow(label: String, value: String, isBold: Boolean = false) {
            canvas.drawText("$label:", labelX, currentY, normalLeftPaint)
            canvas.drawText(value, valueX, currentY, if (isBold) boldLeftPaint else normalLeftPaint)
            currentY += 25f  // Reduced line height
        }

        drawRow("Name", customerName, true)
        drawRow("Bill Date", billDate)
        drawRow("Receipt No", receiptNumber)
        drawRow("Record Time", recordTime)
        drawRow("MAC No", "") // Placeholder

        currentY += 15f  // Reduced spacing
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, linePaint)
        currentY += 30f  // Reduced spacing

        // --- Draw Payment Details ---
        val amountX = rightMargin - 10f

        fun drawAmountRow(label: String, amount: Double) {
            canvas.drawText(label, labelX, currentY, boldLeftPaint)
            canvas.drawText("₹${"%.2f".format(amount)}", amountX, currentY, amountPaint)
            currentY += 30f  // Slightly more spacing for amounts
        }

        drawAmountRow("Prev Balance", prevBalance)
        drawAmountRow("Paid Amount", paidAmount)
        drawAmountRow("Net", netAmount)

        currentY += 15f
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, linePaint)
        currentY += 30f

        drawAmountRow("Remaining Amount", remainingAmount)
        currentY += 40f  // Reduced spacing

        // --- Draw Footer (Compact) ---
        canvas.drawText("Mode: $mode", leftMargin, currentY, normalLeftPaint)
        currentY += 30f
        canvas.drawText("Collected By: SRI SAI BROAD BAND NETWORK", leftMargin, currentY, normalLeftPaint)
        currentY += 50f

        // Footer note (smaller font)
        val footerPaint = TextPaint().apply {
            textSize = 12f
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "This is a computer generated receipt and does not require signature",
            centerX, currentY, footerPaint
        )
        currentY += 25f

        // Sent date
        val datePaint = Paint(normalCenterPaint).apply { textSize = 14f }
        canvas.drawText(
            "Sent on: ${SimpleDateFormat("dd-MMM-yy").format(Date())}",
            centerX, currentY, datePaint
        )

        document.finishPage(page)

        // --- Save to file ---
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "Receipt_$receiptNumber.pdf")
        try {
            file.outputStream().use { document.writeTo(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
        return file
    }

    fun shareReceipt(receiptFile: File) {
        val authority = "${context.packageName}.provider"
        val contentUri = FileProvider.getUriForFile(context, authority, receiptFile)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Receipt via"))
    }
    fun generateReceiptWithContext(
        context: Context,
        customerName: String,
        receiptNumber: String,
        recordTime: String,
        billDate: String,
        prevBalance: Double,
        paidAmount: Double,
        netAmount: Double,
        remainingAmount: Double,
        mode: String
    ): File {
        return generateReceipt(
            customerName, receiptNumber, recordTime, billDate,
            prevBalance, paidAmount, netAmount, remainingAmount, mode
        )
    }
}
