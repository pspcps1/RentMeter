package com.example.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.InvoiceEntity
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.UnitEntity
import com.example.data.model.InvoiceStatus
import com.example.data.preferences.OwnerSettings
import com.example.data.repository.CheckOutResult
import com.example.data.repository.RentLedgerRepository
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class PdfInvoiceGenerator(private val context: Context) {

    private val pdfDir: File by lazy {
        File(context.filesDir, "invoices").apply { mkdirs() }
    }

    fun generateRentInvoicePdf(
        invoice: InvoiceEntity,
        property: PropertyEntity,
        unit: UnitEntity,
        lease: LeaseEntity,
        settings: OwnerSettings
    ): File {
        val document = PdfDocument()
        val pageWidth = 595 // A4 @ 72dpi
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val tealColor = Color.rgb(15, 76, 92)
        val slateColor = Color.rgb(55, 71, 79)
        val mutedColor = Color.rgb(100, 116, 139)
        val lineColor = Color.rgb(226, 232, 240)
        val surfaceTint = Color.rgb(241, 245, 249)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 233, 240)
            textSize = 11f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 31)
            textSize = 11.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 31)
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 10f
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 31)
            textSize = 11.5f
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val bgPaint = Paint().apply { color = tealColor }

        // Top Brand Header Banner
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 110f, bgPaint)
        val brandTitle = settings.businessName.ifBlank { property.name }
        canvas.drawText(brandTitle, 36f, 44f, titlePaint)
        val ownerLine = buildString {
            if (settings.ownerName.isNotBlank()) append("Owner: ${settings.ownerName}")
            if (settings.ownerPhone.isNotBlank()) {
                if (isNotEmpty()) append("  •  ")
                append(settings.ownerPhone)
            }
        }
        if (ownerLine.isNotBlank()) canvas.drawText(ownerLine, 36f, 66f, headerSubPaint)
        if (settings.ownerUpiId.isNotBlank()) {
            canvas.drawText("UPI ID: ${settings.ownerUpiId}", 36f, 84f, headerSubPaint)
        }

        // Invoice Status Badge on right side of banner
        val statusColor = when (invoice.status) {
            InvoiceStatus.PAID -> Color.rgb(4, 120, 87)
            InvoiceStatus.PARTIAL -> Color.rgb(217, 119, 6)
            InvoiceStatus.DUE -> Color.rgb(220, 38, 38)
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(RectF(450f, 32f, 559f, 62f), 8f, 8f, badgePaint)
        canvas.drawText(invoice.status.label, 504.5f, 51f, badgeTextPaint)
        val invRightSub = Paint(headerSubPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(invoice.invoiceNumber, 559f, 84f, invRightSub)

        var y = 142f
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(invoice.createdAt))
        val monthStr = RentLedgerRepository.formatMonthDisplay(invoice.billingMonth)

        // Tenant & Unit Details Box
        val boxPaint = Paint().apply { color = surfaceTint }
        canvas.drawRoundRect(RectF(36f, y, 559f, y + 78f), 10f, 10f, boxPaint)
        canvas.drawText("BILLED TO", 50f, y + 20f, sectionTitlePaint)
        canvas.drawText("${lease.tenantName} (${lease.tenantPhone})", 50f, y + 38f, boldBodyPaint)
        canvas.drawText("${property.name} — ${unit.unitName}", 50f, y + 56f, bodyPaint)

        val rightSection = Paint(sectionTitlePaint).apply { textAlign = Paint.Align.RIGHT }
        val rightMuted = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("BILLING MONTH", 545f, y + 20f, rightSection)
        canvas.drawText(monthStr, 545f, y + 38f, rightPaint)
        canvas.drawText("Issued: $dateStr", 545f, y + 56f, rightMuted)

        y += 104f

        // Itemized Rent & Utility Charges Table
        canvas.drawText("CHARGES BREAKDOWN", 36f, y, sectionTitlePaint)
        y += 14f
        val dividerPaint = Paint().apply {
            color = lineColor
            strokeWidth = 1f
        }

        fun drawChargeRow(label: String, sub: String?, amount: Double) {
            y += 22f
            canvas.drawText(label, 36f, y, bodyPaint)
            if (!sub.isNullOrBlank()) {
                canvas.drawText(sub, 200f, y, mutedPaint)
            }
            canvas.drawText(formatInr(amount), 559f, y, rightPaint)
            y += 8f
            canvas.drawLine(36f, y, 559f, y, dividerPaint)
        }

        drawChargeRow("Monthly Base Rent", unit.unitName, invoice.baseRent)
        drawChargeRow(
            "Electricity (${formatUnits(invoice.unitsConsumed)} units × ₹${formatUnits(invoice.ratePerUnit)})",
            "Prev ${formatUnits(invoice.previousReading)} → Curr ${formatUnits(invoice.currentReading)}",
            invoice.electricityCharge
        )
        if (invoice.waterCharge > 0.0) drawChargeRow("Water Charge", null, invoice.waterCharge)
        if (invoice.maintenanceCharge > 0.0) drawChargeRow("Maintenance / Wi-Fi", null, invoice.maintenanceCharge)
        if (invoice.otherCharge > 0.0) drawChargeRow("Other Charges", null, invoice.otherCharge)
        if (invoice.previousDue > 0.0) drawChargeRow("Previous Balance Due", "Carried forward", invoice.previousDue)

        // Totals Summary Box
        y += 18f
        val summaryRect = RectF(280f, y, 559f, y + 86f)
        canvas.drawRoundRect(summaryRect, 10f, 10f, boxPaint)

        canvas.drawText("Total Payable", 296f, y + 24f, boldBodyPaint)
        canvas.drawText(formatInr(invoice.totalAmount), 543f, y + 24f, rightBoldPaint)

        canvas.drawText("Amount Paid", 296f, y + 48f, bodyPaint)
        canvas.drawText(formatInr(invoice.paidAmount), 543f, y + 48f, rightPaint)

        val balance = max(0.0, invoice.totalAmount - invoice.paidAmount)
        val balancePaint = Paint(rightBoldPaint).apply {
            color = if (balance <= 0.5) Color.rgb(4, 120, 87) else Color.rgb(220, 38, 38)
        }
        canvas.drawText("Balance Due", 296f, y + 72f, boldBodyPaint)
        canvas.drawText(formatInr(balance), 543f, y + 72f, balancePaint)

        // Embedded Cropped Meter Proof Photo
        val photoPath = invoice.meterPhotoPath
        if (!photoPath.isNullOrBlank()) {
            val photoFile = File(photoPath)
            if (photoFile.exists()) {
                val bmp = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (bmp != null) {
                    val photoTop = y + 108f
                    if (photoTop + 140f < pageHeight - 60f) {
                        canvas.drawText("METER READING PHOTO PROOF", 36f, photoTop, sectionTitlePaint)
                        val maxW = 320f
                        val maxH = 110f
                        val ratio = minOf(maxW / bmp.width, maxH / bmp.height)
                        val drawW = bmp.width * ratio
                        val drawH = bmp.height * ratio
                        val destRect = RectF(36f, photoTop + 10f, 36f + drawW, photoTop + 10f + drawH)
                        canvas.drawBitmap(bmp, null, destRect, null)
                        val borderPaint = Paint().apply {
                            color = lineColor
                            style = Paint.Style.STROKE
                            strokeWidth = 1.5f
                        }
                        canvas.drawRect(destRect, borderPaint)
                    }
                    bmp.recycle()
                }
            }
        }

        // Footer
        val footerPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.CENTER }
        val upiNote = if (settings.ownerUpiId.isNotBlank()) "Pay via UPI: ${settings.ownerUpiId}  •  " else ""
        canvas.drawText(
            "${upiNote}Generated offline by RentLedger",
            pageWidth / 2f,
            pageHeight - 28f,
            footerPaint
        )

        document.finishPage(page)

        val outFile = File(pdfDir, "${invoice.invoiceNumber}.pdf")
        FileOutputStream(outFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()
        return outFile
    }

    fun generateCheckoutSettlementPdf(
        result: CheckOutResult,
        settings: OwnerSettings
    ): File {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val tealColor = Color.rgb(15, 76, 92)
        val bgPaint = Paint().apply { color = tealColor }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 233, 240)
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 31)
            textSize = 12f
        }
        val boldPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val divider = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, bgPaint)
        canvas.drawText("DEPOSIT SETTLEMENT RECEIPT", 36f, 44f, titlePaint)
        canvas.drawText(
            "${settings.businessName.ifBlank { result.propertyName }} • ${result.unitName}",
            36f,
            68f,
            subPaint
        )

        var y = 136f
        canvas.drawText("TENANT CHECK-OUT SUMMARY", 36f, y, sectionPaint)
        y += 24f
        canvas.drawText("Tenant: ${result.lease.tenantName} (${result.lease.tenantPhone})", 36f, y, boldPaint)
        y += 20f
        val checkOutDateStr = SimpleDateFormat("dd MMM yyyy", Locale.US)
            .format(Date(result.lease.checkOutDate ?: System.currentTimeMillis()))
        canvas.drawText("Check-Out Date: $checkOutDateStr", 36f, y, bodyPaint)
        y += 28f
        canvas.drawLine(36f, y, 559f, y, divider)

        fun row(label: String, value: String) {
            y += 26f
            canvas.drawText(label, 36f, y, bodyPaint)
            canvas.drawText(value, 559f, y, rightPaint)
            y += 10f
            canvas.drawLine(36f, y, 559f, y, divider)
        }

        row("Security Deposit Held", formatInr(result.lease.securityDeposit))
        row(
            "Final Electricity (${formatUnits(result.unitsUsed)} units × ₹${formatUnits(result.ratePerUnit)})",
            "- ${formatInr(result.finalElectricity)}"
        )
        row("Unpaid Rent / Bills Cleared", "- ${formatInr(result.unpaidDues)}")
        row(
            "Damages / Cleaning (${result.lease.deductionNotes.ifBlank { "None" }})",
            "- ${formatInr(result.damageDeduction)}"
        )

        y += 32f
        val boxPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRoundRect(RectF(36f, y, 559f, y + 60f), 10f, 10f, boxPaint)
        val refundLabel = if (result.netRefund >= 0) "NET DEPOSIT REFUNDABLE TO TENANT" else "BALANCE PAYABLE BY TENANT"
        val refundPaint = Paint(boldPaint).apply {
            textSize = 15f
            textAlign = Paint.Align.RIGHT
            color = if (result.netRefund >= 0) Color.rgb(4, 120, 87) else Color.rgb(220, 38, 38)
        }
        canvas.drawText(refundLabel, 50f, y + 36f, boldPaint)
        canvas.drawText(formatInr(kotlin.math.abs(result.netRefund)), 543f, y + 36f, refundPaint)

        document.finishPage(page)
        val outFile = File(pdfDir, "SETTLEMENT_${result.lease.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        return outFile
    }

    fun generateTenantReportPdf(
        lease: LeaseEntity,
        property: PropertyEntity?,
        unit: UnitEntity?,
        invoices: List<InvoiceEntity>,
        settings: OwnerSettings
    ): File {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val tealColor = Color.rgb(15, 76, 92)
        val mutedColor = Color.rgb(100, 116, 139)
        val surfaceTint = Color.rgb(241, 245, 249)
        val bgPaint = Paint().apply { color = tealColor }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 233, 240)
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 31)
            textSize = 11.5f
        }
        val boldPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 10f
        }
        val rightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val divider = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, bgPaint)
        canvas.drawText("TENANT PROFILE & LEDGER REPORT", 36f, 44f, titlePaint)
        val propLabel = property?.name ?: "Property"
        val unitLabel = unit?.unitName ?: "Unit"
        canvas.drawText(
            "${settings.businessName.ifBlank { propLabel }} • $unitLabel",
            36f,
            68f,
            subPaint
        )

        var y = 124f
        val boxPaint = Paint().apply { color = surfaceTint }
        canvas.drawRoundRect(RectF(36f, y, 559f, y + 98f), 10f, 10f, boxPaint)
        canvas.drawText("TENANT DETAILS", 50f, y + 20f, sectionPaint)
        canvas.drawText("${lease.tenantName} • ${lease.tenantPhone}", 50f, y + 40f, boldPaint)
        val demoLine = buildString {
            if (lease.gender.name != "UNSPECIFIED") append("Gender: ${lease.gender.label}  •  ")
            if (lease.maritalStatus.name != "UNSPECIFIED") append("Status: ${lease.maritalStatus.label}  •  ")
            append("Persons (5+y): ${lease.occupantCount}")
            if (lease.childCountBelow5 > 0) append("  •  Children (<5y): ${lease.childCountBelow5}")
        }
        canvas.drawText(demoLine, 50f, y + 58f, bodyPaint)
        val checkInStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(lease.checkInDate))
        val idLabel = if (lease.tenantIdNumber.isNotBlank()) "${lease.tenantIdType}: ${lease.tenantIdNumber}" else lease.tenantIdType
        canvas.drawText("Checked In: $checkInStr  •  $idLabel", 50f, y + 76f, mutedPaint)
        if (lease.childNotes.isNotBlank()) {
            canvas.drawText("Child Details (<5y): ${lease.childNotes}", 50f, y + 91f, mutedPaint)
        }

        y += 120f
        val totalBilled = invoices.sumOf { it.totalAmount }
        val totalPaid = invoices.sumOf { it.paidAmount }
        val totalDue = max(0.0, totalBilled - totalPaid)
        val totalUnits = invoices.sumOf { it.unitsConsumed }

        canvas.drawText("FINANCIAL SUMMARY", 36f, y, sectionPaint)
        y += 16f
        fun summaryRow(label: String, valStr: String) {
            y += 20f
            canvas.drawText(label, 36f, y, bodyPaint)
            canvas.drawText(valStr, 559f, y, rightPaint)
            y += 8f
            canvas.drawLine(36f, y, 559f, y, divider)
        }
        summaryRow("Total Billed (${invoices.size} invoices)", formatInr(totalBilled))
        summaryRow("Total Paid", formatInr(totalPaid))
        summaryRow("Pending Balance Due", formatInr(totalDue))
        summaryRow("Security Deposit Held", formatInr(lease.securityDeposit))
        summaryRow("Electricity Consumed", "${formatUnits(totalUnits)} kWh")

        y += 26f
        canvas.drawText("INVOICE HISTORY", 36f, y, sectionPaint)
        y += 14f
        val rows = invoices.sortedByDescending { it.createdAt }.take(12)
        if (rows.isEmpty()) {
            y += 20f
            canvas.drawText("No invoices generated yet.", 36f, y, mutedPaint)
        } else {
            rows.forEach { inv ->
                if (y > pageHeight - 70f) return@forEach
                y += 20f
                val mLabel = RentLedgerRepository.formatMonthDisplay(inv.billingMonth)
                canvas.drawText("${inv.invoiceNumber} ($mLabel) • ${inv.status.label}", 36f, y, bodyPaint)
                canvas.drawText("${formatInr(inv.paidAmount)} / ${formatInr(inv.totalAmount)}", 559f, y, rightPaint)
                y += 7f
                canvas.drawLine(36f, y, 559f, y, divider)
            }
        }

        val footerPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("Generated offline by RentLedger", pageWidth / 2f, pageHeight - 28f, footerPaint)

        document.finishPage(page)
        val outFile = File(pdfDir, "TENANT_REPORT_${lease.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        return outFile
    }

    fun openPdf(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            sharePdf(file, "RentLedger PDF Document", preferWhatsApp = false)
            false
        }
    }

    fun sharePdf(file: File, messageText: String, preferWhatsApp: Boolean = true) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, messageText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (preferWhatsApp) {
            val pm = context.packageManager
            val waPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
            for (pkg in waPackages) {
                val targeted = Intent(shareIntent).apply { setPackage(pkg) }
                if (targeted.resolveActivity(pm) != null) {
                    targeted.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(targeted)
                    return
                }
            }
        }

        val chooser = Intent.createChooser(shareIntent, "Share Invoice PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun buildWhatsAppMessage(
        invoice: InvoiceEntity,
        propertyName: String,
        unitName: String,
        tenantName: String,
        upiId: String
    ): String {
        val balance = max(0.0, invoice.totalAmount - invoice.paidAmount)
        return buildString {
            appendLine("Hello $tenantName,")
            appendLine("Rent & Electricity Bill for *$propertyName - $unitName* (${RentLedgerRepository.formatMonthDisplay(invoice.billingMonth)}):")
            appendLine("• Base Rent: ${formatInr(invoice.baseRent)}")
            appendLine("• Electricity: ${formatUnits(invoice.unitsConsumed)} units (${formatUnits(invoice.previousReading)} → ${formatUnits(invoice.currentReading)}) = ${formatInr(invoice.electricityCharge)}")
            if (invoice.waterCharge + invoice.maintenanceCharge + invoice.otherCharge > 0.0) {
                appendLine("• Extras: ${formatInr(invoice.waterCharge + invoice.maintenanceCharge + invoice.otherCharge)}")
            }
            if (invoice.previousDue > 0.0) {
                appendLine("• Previous Due: ${formatInr(invoice.previousDue)}")
            }
            appendLine("*Total: ${formatInr(invoice.totalAmount)}* | Paid: ${formatInr(invoice.paidAmount)} | *Balance: ${formatInr(balance)}*")
            if (upiId.isNotBlank() && balance > 0) {
                appendLine("Pay via UPI: $upiId")
            }
        }.trim()
    }

    companion object {
        fun formatInr(amount: Double): String {
            val rounded = amount.roundToInt()
            return if (kotlin.math.abs(amount - rounded) < 0.01) {
                "₹$rounded"
            } else {
                String.format(Locale.US, "₹%.2f", amount)
            }
        }

        fun formatUnits(v: Double): String =
            if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
    }
}
