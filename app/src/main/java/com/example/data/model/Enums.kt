package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class PropertyType(val label: String) {
    PG("PG / Hostel"),
    HOUSE("House / Flat"),
    SHOP("Shop"),
    OFFICE("Office")
}

@Serializable
enum class MeterType(val label: String, val subtitle: String) {
    DIGITAL_LCD("Digital LCD (7-Segment)", "Backlit digital display (e.g. HPL, L&T)"),
    ANALOG_DRUM("Analog Drum", "Mechanical rotating digit wheels")
}

@Serializable
enum class InvoiceStatus(val label: String) {
    DUE("DUE"),
    PARTIAL("PARTIAL"),
    PAID("PAID")
}

@Serializable
enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer")
}

@Serializable
enum class ExpenseCategory(val label: String) {
    WATER_TANKER("Water Tanker"),
    PLUMBER("Plumber"),
    ELECTRICIAN("Electrician"),
    PAINTING("Painting"),
    TAX("Property Tax"),
    CLEANING("Cleaning"),
    REPAIR("Repairs"),
    OTHER("Other")
}

@Serializable
enum class WaterRateMode(val label: String) {
    INHERIT("Default"),
    NONE("None"),
    PER_ROOM("Fixed / Month"),
    PER_PERSON("Per Person")
}

@Serializable
enum class ExpenseSplitMode(val label: String, val subtitle: String) {
    OWNER_ONLY("Owner Only", "Owner pays (recorded for tracking)"),
    SINGLE_ROOM("Charge Selected Room", "Collect full amount from this room's tenant"),
    EQUAL_PER_ROOM("Split Per Room", "Equal share for each occupied room"),
    PER_PERSON("Split Per Person", "Share based on persons living in each room")
}

@Serializable
enum class TenantGender(val label: String) {
    UNSPECIFIED("Not Set"),
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other")
}

@Serializable
enum class MaritalStatus(val label: String) {
    UNSPECIFIED("Not Set"),
    SINGLE("Single"),
    MARRIED("Married")
}

@Serializable
enum class TenantDocumentType(val label: String) {
    ID_PROOF("ID Proof"),
    AGREEMENT("Rent Agreement"),
    POLICE_VERIFICATION("Police Verification"),
    OFFICE_ID("Work / College ID"),
    CHILD_PROOF("Child Proof (<5y)"),
    OTHER("Other Document")
}

@Serializable
enum class ElectricityBillingMode(val label: String, val subtitle: String) {
    INHERIT("Default", "Use Property or Common default"),
    METERED("Metered (Sub-meter)", "Scan/enter kWh reading every month"),
    SEPARATE_CONNECTION("Separate EB Meter", "Tenant pays utility directly (₹0 on rent bill)"),
    INCLUDED_IN_RENT("Included in Rent", "Electricity included in monthly rent (₹0 extra)")
}
