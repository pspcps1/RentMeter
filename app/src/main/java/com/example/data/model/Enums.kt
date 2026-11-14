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
    OWNER_ONLY("Owner Only", "Do not split with tenants"),
    EQUAL_PER_ROOM("Split Per Room", "Equal share for each occupied room"),
    PER_PERSON("Split Per Person", "Share based on persons living in each room")
}
