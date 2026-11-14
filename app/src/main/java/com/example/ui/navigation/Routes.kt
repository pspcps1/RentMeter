package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Dashboard : Route
    @Serializable data object Properties : Route
    @Serializable data object Expenses : Route
    @Serializable data object Reports : Route
    @Serializable data object Settings : Route

    @Serializable data class CheckIn(val unitId: Long) : Route

    @Serializable data class MeterScanner(
        val unitId: Long,
        val forCheckout: Boolean = false
    ) : Route

    @Serializable data class BillPreview(
        val unitId: Long,
        val detectedReading: String = "",
        val croppedPhotoPath: String = "",
        val ocrRawText: String = "",
        val isSuspicious: Boolean = false,
        val warningMessage: String = ""
    ) : Route

    @Serializable data class InvoicePayment(val invoiceId: Long) : Route

    @Serializable data class CheckOut(
        val unitId: Long,
        val finalReading: String = "",
        val finalPhotoPath: String = ""
    ) : Route
}
