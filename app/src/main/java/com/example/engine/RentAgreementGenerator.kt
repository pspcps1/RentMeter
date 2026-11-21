package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import com.example.data.model.ElectricityBillingMode
import com.example.data.model.MaritalStatus
import com.example.data.model.TenantGender
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RentAgreementInput(
    val landlordName: String,
    val landlordPhone: String,
    val businessName: String,
    val tenantName: String,
    val tenantPhone: String,
    val tenantIdType: String,
    val tenantIdNumber: String,
    val gender: TenantGender,
    val maritalStatus: MaritalStatus,
    val occupantCount: Int,
    val childCountBelow5: Int,
    val childNotes: String,
    val propertyName: String,
    val unitName: String,
    val premisesAddress: String,
    val cityOrPlace: String,
    val monthlyRent: Double,
    val maintenanceCharge: Double,
    val otherCharge: Double,
    val otherChargeLabel: String,
    val securityDeposit: Double,
    val electricityBillingMode: ElectricityBillingMode,
    val electricityRatePerUnit: Double,
    val waterSummary: String,
    val rentDueDay: Int,
    val termMonths: Int = 11,
    val noticePeriodMonths: Int = 1,
    val agreementDateText: String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()),
    val witness1: String = "",
    val witness2: String = "",
    val customClauses: String = "",
    /**
     * Normalized 0f..1f signature stroke coordinates so pads render cleanly at high resolution.
     */
    val landlordSignatureStrokes: List<List<Offset>>,
    val tenantSignatureStrokes: List<List<Offset>>
)

object RentAgreementGenerator {

    fun generateSignedAgreementPng(
        context: Context,
        leaseId: Long,
        input: RentAgreementInput
    ): ImportedMediaFile {
        val pageWidth = 1240
        val margin = 72f
        val contentWidth = (pageWidth - margin * 2).toInt()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitleBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 236, 245)
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 76, 92)
            textSize = 25f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 30, 36)
            textSize = 22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 26, 32)
            textSize = 22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val smallMutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(92, 104, 118)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val landlordLabel = input.landlordName.ifBlank { input.businessName.ifBlank { "Landlord / Owner" } }
        val placeLabel = input.cityOrPlace.ifBlank { input.premisesAddress.ifBlank { "India" } }
        val demoParts = buildList {
            if (input.gender != TenantGender.UNSPECIFIED) add(input.gender.label)
            if (input.maritalStatus != MaritalStatus.UNSPECIFIED) add(input.maritalStatus.label)
        }.joinToString(", ")
        val householdLine = buildString {
            append("${input.occupantCount} Person(s) (5+ yrs)")
            if (input.childCountBelow5 > 0) {
                append(" + ${input.childCountBelow5} Child (<5 yrs)")
                if (input.childNotes.isNotBlank()) append(" [${input.childNotes}]")
            }
        }
        val idLine = if (input.tenantIdNumber.isNotBlank()) {
            "${input.tenantIdType} No. ${input.tenantIdNumber}"
        } else {
            input.tenantIdType
        }
        val electricityClause = when (input.electricityBillingMode) {
            ElectricityBillingMode.SEPARATE_CONNECTION ->
                "Separate official electricity board meter connection serves the unit; the Tenant shall pay all electricity board bills directly on time."
            ElectricityBillingMode.INCLUDED_IN_RENT ->
                "Standard domestic electricity consumption is included within the monthly rent; no separate meter charge applies."
            else ->
                "Sub-meter electricity consumption shall be billed monthly at ₹${PdfInvoiceGenerator.formatUnits(input.electricityRatePerUnit)} per unit (kWh) as per meter reading."
        }
        val extrasSummary = buildList {
            if (input.maintenanceCharge > 0.0) {
                add("Monthly Maintenance: ${PdfInvoiceGenerator.formatInr(input.maintenanceCharge)}")
            }
            if (input.otherCharge > 0.0) {
                val lbl = input.otherChargeLabel.ifBlank { "Fixed Extras" }
                add("$lbl: ${PdfInvoiceGenerator.formatInr(input.otherCharge)}")
            }
            if (input.waterSummary.isNotBlank()) {
                add("Water: ${input.waterSummary}")
            }
        }.joinToString("  |  ")

        val paragraphs = listOf(
            "EXECUTION & PARTIES" to (
                "This Rental / Leave and License Agreement is executed on ${input.agreementDateText} at $placeLabel " +
                    "between:\n" +
                    "• LESSOR / LANDLORD: $landlordLabel" +
                    (if (input.landlordPhone.isNotBlank()) " (Ph: ${input.landlordPhone})" else "") +
                    (if (input.businessName.isNotBlank()) ", representing ${input.businessName}" else "") +
                    " (hereinafter called the 'LANDLORD').\n" +
                    "• LESSEE / TENANT: ${input.tenantName}" +
                    (if (demoParts.isNotBlank()) " ($demoParts)" else "") +
                    ", Phone: ${input.tenantPhone}, ID Proof: $idLine, Household: $householdLine " +
                    "(hereinafter called the 'TENANT')."
                ),
            "SCHEDULE OF PREMISES" to (
                "${input.unitName} at ${input.propertyName}" +
                    (if (input.premisesAddress.isNotBlank()) ", Address: ${input.premisesAddress}" else "") +
                    ". Let out strictly for ${if (input.propertyName.contains("Shop", true) || input.unitName.contains("Shop", true)) "permitted commercial/residential" else "bonafide residential"} occupation."
                ),
            "FINANCIAL TERMS & UTILITIES" to (
                "• Monthly Base Rent: ${PdfInvoiceGenerator.formatInr(input.monthlyRent)} payable on or before the ${input.rentDueDay}th day of each calendar month.\n" +
                    "• Refundable Security Deposit: ${PdfInvoiceGenerator.formatInr(input.securityDeposit)} held interest-free by Landlord, refundable upon peaceful handover after adjusting unpaid rent, electricity, and damage deductions.\n" +
                    (if (extrasSummary.isNotBlank()) "• Fixed Monthly Utilities & Charges: $extrasSummary.\n" else "") +
                    "• Electricity Arrangement: $electricityClause"
                ),
            "STANDARD STATUTORY TERMS (MODEL TENANCY NORMS)" to buildString {
                appendLine("1. TENANCY TERM: Granted for ${input.termMonths} months commencing from ${input.agreementDateText}, renewable on mutually agreed terms and statutory rent revision.")
                appendLine("2. UPKEEP & REPAIRS: Tenant shall maintain the premises, electrical fixtures, and sanitary fittings in good condition; structural repairs remain Landlord's responsibility, while minor day-to-day/negligent damage shall be borne by Tenant.")
                appendLine("3. NO SUB-LETTING OR NUISANCE: Tenant shall not sub-let, assign, alter permanent structure, or carry out any unlawful activity on the premises.")
                appendLine("4. INSPECTION: Landlord or authorized representative may inspect the premises at reasonable daytime hours with prior notice to Tenant.")
                appendLine("5. TERMINATION & NOTICE: Either party may terminate this Agreement by serving ${input.noticePeriodMonths} month(s) prior written notice. Tenant shall clear all utility dues and surrender keys upon expiry.")
                if (input.customClauses.isNotBlank()) {
                    appendLine("6. ADDITIONAL AGREED TERMS: ${input.customClauses.trim()}")
                }
            }.trim()
        )

        val layouts = paragraphs.map { (heading, body) ->
            heading to StaticLayout.Builder.obtain(body, 0, body.length, bodyTextPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(5f, 1.15f)
                .build()
        }

        val bannerHeight = 165f
        val footerSignBoxHeight = 250f
        val witnessesHeight = 96f
        val bodyTotalHeight = layouts.sumOf { (_, layout) -> 42 + layout.height + 24 }
        val totalHeight = (bannerHeight + 48f + bodyTotalHeight + witnessesHeight + footerSignBoxHeight + 90f).toInt()

        val bitmap = Bitmap.createBitmap(pageWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Subtle outer formal border
        val borderPaint = Paint().apply {
            color = Color.rgb(15, 76, 92)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(24f, 24f, pageWidth - 24f, totalHeight - 24f, borderPaint)

        // Header Banner
        val headerBg = Paint().apply { color = Color.rgb(15, 76, 92) }
        canvas.drawRect(24f, 24f, pageWidth - 24f, 24f + bannerHeight, headerBg)
        canvas.drawText("RENTAL / LEAVE & LICENSE AGREEMENT", pageWidth / 2f, 92f, titlePaint)
        canvas.drawText(
            "Standard Government-Aligned Format (${input.termMonths}-Month Agreement) • Ref #RL-$leaseId",
            pageWidth / 2f,
            134f,
            subtitleBannerPaint
        )
        canvas.drawText(
            "Executed at $placeLabel on ${input.agreementDateText}",
            pageWidth / 2f,
            165f,
            subtitleBannerPaint
        )

        var y = 24f + bannerHeight + 42f
        val dividerPaint = Paint().apply {
            color = Color.rgb(214, 222, 232)
            strokeWidth = 1.5f
        }

        layouts.forEach { (heading, layout) ->
            canvas.drawText(heading, margin, y, sectionHeaderPaint)
            y += 12f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 14f
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 26f
        }

        // Witnesses Row
        canvas.drawText("WITNESSES", margin, y, sectionHeaderPaint)
        y += 28f
        val halfW = (contentWidth - 36f) / 2f
        canvas.drawText(
            "1. ${input.witness1.ifBlank { "____________________________" }}",
            margin,
            y,
            bodyTextPaint
        )
        canvas.drawText(
            "2. ${input.witness2.ifBlank { "____________________________" }}",
            margin + halfW + 36f,
            y,
            bodyTextPaint
        )
        y += 42f

        // Signatures Section
        canvas.drawText("IN WITNESS WHEREOF, BOTH PARTIES SIGN BELOW:", margin, y, sectionHeaderPaint)
        y += 18f

        val sigBoxFill = Paint().apply { color = Color.rgb(248, 250, 252) }
        val sigBoxStroke = Paint().apply {
            color = Color.rgb(180, 194, 210)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val boxTop = y
        val boxBottom = y + 170f
        val leftRect = RectF(margin, boxTop, margin + halfW, boxBottom)
        val rightRect = RectF(margin + halfW + 36f, boxTop, pageWidth - margin, boxBottom)

        canvas.drawRoundRect(leftRect, 14f, 14f, sigBoxFill)
        canvas.drawRoundRect(leftRect, 14f, 14f, sigBoxStroke)
        canvas.drawRoundRect(rightRect, 14f, 14f, sigBoxFill)
        canvas.drawRoundRect(rightRect, 14f, 14f, sigBoxStroke)

        drawSignatureStrokes(canvas, input.landlordSignatureStrokes, leftRect)
        drawSignatureStrokes(canvas, input.tenantSignatureStrokes, rightRect)

        val sigLabelTop = boxBottom + 28f
        canvas.drawText("LANDLORD SIGNATURE", margin, sigLabelTop, boldBodyPaint)
        canvas.drawText("$landlordLabel • ${input.agreementDateText}", margin, sigLabelTop + 26f, smallMutedPaint)

        val rightTextX = margin + halfW + 36f
        canvas.drawText("TENANT SIGNATURE", rightTextX, sigLabelTop, boldBodyPaint)
        canvas.drawText("${input.tenantName} • ${input.agreementDateText}", rightTextX, sigLabelTop + 26f, smallMutedPaint)

        val footerTextPaint = Paint(smallMutedPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(
            "Signed digitally offline via RentLedger & stored securely in App Documents Vault",
            pageWidth / 2f,
            totalHeight - 40f,
            footerTextPaint
        )

        val dir = File(context.filesDir, "media/tenant_docs").apply { mkdirs() }
        val fileName = "agreement_${leaseId}_${System.currentTimeMillis()}.png"
        val outFile = File(dir, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()

        return ImportedMediaFile(
            filePath = outFile.absolutePath,
            mimeType = "image/png"
        )
    }

    private fun drawSignatureStrokes(
        canvas: Canvas,
        normalizedStrokes: List<List<Offset>>,
        bounds: RectF
    ) {
        if (normalizedStrokes.isEmpty()) return
        val pad = 16f
        val drawW = (bounds.width() - pad * 2).coerceAtLeast(10f)
        val drawH = (bounds.height() - pad * 2).coerceAtLeast(10f)
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 42, 92) // Deep fountain-pen ink blue
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        normalizedStrokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            val path = Path()
            val first = stroke.first()
            val fx = bounds.left + pad + first.x.coerceIn(0f, 1f) * drawW
            val fy = bounds.top + pad + first.y.coerceIn(0f, 1f) * drawH
            path.moveTo(fx, fy)
            if (stroke.size == 1) {
                canvas.drawPoint(fx, fy, inkPaint)
            } else {
                for (i in 1 until stroke.size) {
                    val pt = stroke[i]
                    val px = bounds.left + pad + pt.x.coerceIn(0f, 1f) * drawW
                    val py = bounds.top + pad + pt.y.coerceIn(0f, 1f) * drawH
                    path.lineTo(px, py)
                }
                canvas.drawPath(path, inkPaint)
            }
        }
    }
}
