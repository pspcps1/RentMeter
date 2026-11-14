package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.InvoiceEntity
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.RentLedgerDatabase
import com.example.data.local.UnitEntity
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseSplitMode
import com.example.data.model.InvoiceStatus
import com.example.data.model.PropertyType
import com.example.data.model.WaterRateMode
import com.example.data.preferences.OwnerSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.RentLedgerRepository
import com.example.data.repository.UnitWithContext
import com.example.engine.NormalizedRoi
import com.example.ui.components.AppFormBottomSheet
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.FormSectionCard
import com.example.ui.screens.DraggableRoiViewfinderOverlay
import com.example.ui.screens.GalleryManualCropView
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.RentLedgerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun verifyAppNameAndTessdataAssetBundled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RentLedger", appName)

        val assets = context.assets.list("tessdata") ?: emptyArray()
        assertTrue("letsgodigital.traineddata should be bundled", assets.contains("letsgodigital.traineddata"))
    }

    @Test
    fun loadSampleDemoData_andResetDefault_workCleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RentLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsDataStore(context)
        val repo = RentLedgerRepository(db, settingsStore)

        repo.loadSampleDemoData()
        assertEquals(2, repo.propertiesFlow.first().size)
        assertEquals(5, repo.unitsFlow.first().size)
        assertEquals(3, repo.invoicesFlow.first().size)
        assertEquals(3, repo.expensesFlow.first().size)
        assertEquals(2, repo.sharedChargesFlow.first().size)

        repo.resetAllDataToDefault()
        assertEquals(0, repo.propertiesFlow.first().size)
        assertEquals(0, repo.unitsFlow.first().size)
        assertEquals(0, repo.invoicesFlow.first().size)
        assertEquals(0, repo.expensesFlow.first().size)
        assertEquals(0, repo.sharedChargesFlow.first().size)
        db.close()
    }

    @Test
    fun hierarchicalRateResolution_electricityAndWater_fallbackCorrectly() {
        val global = OwnerSettings(
            defaultRatePerUnit = 8.0,
            defaultWaterMode = WaterRateMode.PER_PERSON,
            defaultWaterRate = 150.0
        )
        val propInherit = PropertyEntity(
            id = 1L,
            name = "Sunrise PG",
            electricityRateOverride = null,
            waterMode = WaterRateMode.INHERIT,
            waterRate = null
        )
        val propCustom = PropertyEntity(
            id = 2L,
            name = "Market Shop",
            electricityRateOverride = 10.5,
            waterMode = WaterRateMode.PER_ROOM,
            waterRate = 300.0
        )
        val roomInherit = UnitEntity(
            id = 10L,
            propertyId = 1L,
            unitName = "Room 101",
            baseRent = 8000.0,
            electricityRateOverride = null,
            waterMode = WaterRateMode.INHERIT,
            waterRate = null
        )
        val roomCustom = UnitEntity(
            id = 11L,
            propertyId = 1L,
            unitName = "Room 102",
            baseRent = 9000.0,
            electricityRateOverride = 12.0,
            waterMode = WaterRateMode.PER_ROOM,
            waterRate = 400.0
        )

        // Room inherits from Common Default when Property also inherits
        assertEquals(8.0, RentLedgerRepository.resolveElectricityRate(roomInherit, propInherit, global).rate, 0.001)
        val water4p = RentLedgerRepository.resolveWaterConfig(roomInherit, propInherit, 4, global)
        assertEquals(WaterRateMode.PER_PERSON, water4p.mode)
        assertEquals(600.0, water4p.computedCharge, 0.001)

        // Room inherits from Property Custom when Room override is blank
        assertEquals(10.5, RentLedgerRepository.resolveElectricityRate(roomInherit, propCustom, global).rate, 0.001)
        val waterProp = RentLedgerRepository.resolveWaterConfig(roomInherit, propCustom, 4, global)
        assertEquals(WaterRateMode.PER_ROOM, waterProp.mode)
        assertEquals(300.0, waterProp.computedCharge, 0.001)

        // Room Custom overrides both Property and Common Default
        assertEquals(12.0, RentLedgerRepository.resolveElectricityRate(roomCustom, propCustom, global).rate, 0.001)
        val waterRoom = RentLedgerRepository.resolveWaterConfig(roomCustom, propCustom, 4, global)
        assertEquals(400.0, waterRoom.computedCharge, 0.001)
    }

    @Test
    fun expenseSplitting_perPersonAndPerRoom_calculateAndAttachToInvoice() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RentLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsDataStore(context)
        settingsStore.updateSettings(
            OwnerSettings(
                defaultRatePerUnit = 9.0,
                defaultWaterMode = WaterRateMode.PER_PERSON,
                defaultWaterRate = 100.0
            )
        )
        val repo = RentLedgerRepository(db, settingsStore)

        val propId = repo.saveProperty(PropertyEntity(name = "Test PG", type = PropertyType.PG))
        val u1 = repo.saveUnit(UnitEntity(propertyId = propId, unitName = "Room A", baseRent = 5000.0, lastReading = 100.0))
        val u2 = repo.saveUnit(UnitEntity(propertyId = propId, unitName = "Room B", baseRent = 5000.0, lastReading = 200.0))

        // Room A: 4 persons, Room B: 2 persons (Total 6 persons)
        val l1 = repo.checkInTenant(u1, "Ravi", "9999911111", "Aadhaar", "", null, 5000.0, 5, occupantCount = 4, initialReading = 100.0, initialPhotoPath = null)
        repo.checkInTenant(u2, "Amit", "9999922222", "Aadhaar", "", null, 5000.0, 5, occupantCount = 2, initialReading = 200.0, initialPhotoPath = null)

        // Water Tanker of ₹1200 split PER_PERSON => ₹200/person => Room A gets ₹800, Room B gets ₹400
        repo.saveExpenseWithSplit(
            propertyId = propId,
            appliesToAllProperties = false,
            category = ExpenseCategory.WATER_TANKER,
            title = "Water Tanker",
            amount = 1200.0,
            billingMonth = RentLedgerRepository.currentBillingMonth(),
            notes = "",
            splitMode = ExpenseSplitMode.PER_PERSON
        )

        val unbilledA = repo.getUnbilledSharedChargesForLease(u1, l1)
        assertEquals(1, unbilledA.size)
        assertEquals(800.0, unbilledA.first().amount, 0.01)

        // Create bill for Room A: 10 units @ ₹9 (from Common Default) = ₹90 + base ₹5000 + water ₹400 + tanker ₹800 = ₹6290
        val invId = repo.createBillAndSaveReading(
            unitId = u1,
            currentReading = 110.0,
            croppedPhotoPath = null,
            ocrRawText = "110",
            wasManuallyEdited = false,
            waterCharge = 400.0,
            maintenanceCharge = 0.0,
            otherCharge = 0.0,
            includePreviousDue = true,
            includeSharedCharges = true
        )
        val invoice = repo.getInvoiceById(invId)!!
        assertEquals(9.0, invoice.ratePerUnit, 0.001)
        assertEquals(90.0, invoice.electricityCharge, 0.01)
        assertEquals(1200.0, invoice.waterCharge, 0.01)
        assertEquals(6290.0, invoice.totalAmount, 0.01)

        // Verify shared charge is now marked billed and won't be double billed
        assertTrue(repo.getUnbilledSharedChargesForLease(u1, l1).isEmpty())
        db.close()
    }

    @Test
    fun reportsScreen_withOverlappingPropertyAndInvoiceIds_rendersWithoutCrashing() {
        val month = RentLedgerRepository.currentBillingMonth()
        val prop1 = PropertyEntity(id = 1L, name = "Sunrise PG", type = PropertyType.PG)
        val prop2 = PropertyEntity(id = 2L, name = "Main Market Shop", type = PropertyType.SHOP)
        val unit1 = UnitEntity(id = 1L, propertyId = 1L, unitName = "Room 101", baseRent = 8000.0)
        val lease1 = LeaseEntity(id = 1L, unitId = 1L, propertyId = 1L, tenantName = "Aarav", tenantPhone = "9876543210", occupantCount = 4)
        val inv1 = InvoiceEntity(
            id = 1L,
            invoiceNumber = "INV-1",
            unitId = 1L,
            leaseId = 1L,
            propertyId = 1L,
            billingMonth = month,
            totalAmount = 9500.0,
            paidAmount = 3000.0,
            status = InvoiceStatus.PARTIAL
        )
        val inv2 = InvoiceEntity(
            id = 2L,
            invoiceNumber = "INV-2",
            unitId = 99L,
            leaseId = 1L,
            propertyId = 2L,
            billingMonth = month,
            totalAmount = 12000.0,
            paidAmount = 0.0,
            status = InvoiceStatus.DUE
        )

        composeRule.setContent {
            RentLedgerTheme {
                ReportsScreen(
                    properties = listOf(prop1, prop2),
                    unitsWithContext = listOf(
                        UnitWithContext(
                            unit = unit1,
                            property = prop1,
                            activeLease = lease1,
                            latestInvoice = inv1,
                            unpaidBalance = 6500.0
                        )
                    ),
                    invoices = listOf(inv1, inv2),
                    expenses = emptyList(),
                    leases = listOf(lease1.copy(securityDeposit = 15000.0)),
                    onOpenInvoice = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Financial Reports").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Sunrise PG"))
        composeRule.onNodeWithText("Sunrise PG").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("All Pending Dues"))
        composeRule.onNodeWithText("All Pending Dues").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("INV-2"))
        composeRule.onNodeWithText("INV-2").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Security Deposits Held"))
        composeRule.onNodeWithText("Security Deposits Held").assertIsDisplayed()
    }

    @Test
    fun securityDeposit_hierarchyAndUpdate_workEndToEnd() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RentLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsDataStore(context)
        settingsStore.updateSettings(OwnerSettings(defaultSecurityDeposit = 10000.0))
        val repo = RentLedgerRepository(db, settingsStore)

        val propInheritId = repo.saveProperty(PropertyEntity(name = "PG A", type = PropertyType.PG))
        val propCustomId = repo.saveProperty(PropertyEntity(name = "Shop B", type = PropertyType.SHOP, defaultDepositOverride = 45000.0))

        val unitCommonId = repo.saveUnit(UnitEntity(propertyId = propInheritId, unitName = "Room 1", baseRent = 6000.0))
        val unitPropId = repo.saveUnit(UnitEntity(propertyId = propCustomId, unitName = "Shop 1", baseRent = 18000.0))
        val unitCustomId = repo.saveUnit(UnitEntity(propertyId = propCustomId, unitName = "Shop 2", baseRent = 22000.0, depositOverride = 60000.0))

        val resCommon = repo.getDefaultDepositForUnit(unitCommonId)
        assertEquals(10000.0, resCommon.rate, 0.01)
        assertEquals("Common default", resCommon.sourceLabel)

        val resProp = repo.getDefaultDepositForUnit(unitPropId)
        assertEquals(45000.0, resProp.rate, 0.01)
        assertTrue(resProp.sourceLabel.contains("Property"))

        val resRoom = repo.getDefaultDepositForUnit(unitCustomId)
        assertEquals(60000.0, resRoom.rate, 0.01)
        assertEquals("Room custom", resRoom.sourceLabel)

        val leaseId = repo.checkInTenant(
            unitId = unitCommonId,
            tenantName = "Neha",
            tenantPhone = "9876500000",
            idType = "Aadhaar",
            idNumber = "1234",
            idPhotoPath = null,
            securityDeposit = resCommon.rate,
            rentDueDay = 5,
            occupantCount = 1,
            initialReading = 100.0,
            initialPhotoPath = null
        )

        assertEquals(10000.0, repo.observeDashboardSummary().first().totalDepositsHeld, 0.01)

        repo.updateLeaseSecurityDeposit(leaseId, 14000.0)
        assertEquals(14000.0, repo.getLeaseById(leaseId)!!.securityDeposit, 0.01)
        assertEquals(14000.0, repo.observeDashboardSummary().first().totalDepositsHeld, 0.01)
        db.close()
    }

    @Test
    fun normalizedRoi_moveAndResize_respectBounds() {
        val base = NormalizedRoi.DEFAULT_SCAN_BOX
        val movedUp = base.movedBy(dx = 0f, dy = -0.15f, minTop = 0.16f, maxBottom = 0.74f)
        assertEquals(base.height, movedUp.height, 0.001f)
        assertEquals(0.25f, movedUp.top, 0.001f)

        val clampedTop = base.movedBy(dx = 0f, dy = -0.50f, minTop = 0.16f, maxBottom = 0.74f)
        assertEquals(0.16f, clampedTop.top, 0.001f)
        assertEquals(base.height, clampedTop.height, 0.001f)

        val clampedBottom = base.movedBy(dx = 0f, dy = 0.50f, minTop = 0.16f, maxBottom = 0.74f)
        assertEquals(0.74f, clampedBottom.bottom, 0.001f)
        assertEquals(base.height, clampedBottom.height, 0.001f)

        val taller = base.resizedVertical(deltaTop = -0.02f, deltaBottom = 0.02f)
        assertEquals(base.height + 0.04f, taller.height, 0.001f)
    }

    @Test
    fun galleryCropAndLiveOverlay_dragUpAndDown_updatesRoi() {
        val dummyBitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        var galleryRoi by mutableStateOf(NormalizedRoi(0.08f, 0.38f, 0.92f, 0.56f))
        var showLive by mutableStateOf(false)
        var liveRoi by mutableStateOf(NormalizedRoi.DEFAULT_SCAN_BOX)

        composeRule.setContent {
            RentLedgerTheme {
                if (!showLive) {
                    GalleryManualCropView(
                        bitmap = dummyBitmap,
                        unit = null,
                        roi = galleryRoi,
                        isProcessing = false,
                        onRoiChange = { galleryRoi = it },
                        onCancel = {},
                        onConfirmCrop = {}
                    )
                } else {
                    DraggableRoiViewfinderOverlay(
                        roi = liveRoi,
                        onRoiChange = { liveRoi = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Drag gallery crop box UP from middle of box
        composeRule.onNodeWithTag("gallery_crop_canvas").performTouchInput {
            val startY = height * (galleryRoi.top + galleryRoi.height / 2f)
            swipe(
                start = Offset(centerX, startY),
                end = Offset(centerX, startY - height * 0.25f),
                durationMillis = 200
            )
        }
        composeRule.waitForIdle()
        assertTrue("Gallery ROI top should move up (< 0.38f), was ${galleryRoi.top}", galleryRoi.top < 0.30f)

        // Drag gallery crop box DOWN from middle of moved box
        val afterUpTop = galleryRoi.top
        composeRule.onNodeWithTag("gallery_crop_canvas").performTouchInput {
            val startY = height * (galleryRoi.top + galleryRoi.height / 2f)
            swipe(
                start = Offset(centerX, startY),
                end = Offset(centerX, startY + height * 0.35f),
                durationMillis = 200
            )
        }
        composeRule.waitForIdle()
        assertTrue("Gallery ROI top should move down (> $afterUpTop), was ${galleryRoi.top}", galleryRoi.top > afterUpTop + 0.15f)

        // Reset and Taller buttons
        composeRule.onNodeWithTag("crop_reset_button").performClick()
        composeRule.waitForIdle()
        assertEquals(0.38f, galleryRoi.top, 0.001f)

        composeRule.onNodeWithTag("crop_taller_button").performClick()
        composeRule.waitForIdle()
        assertTrue(galleryRoi.height > 0.18f)

        // Switch to live viewfinder overlay and drag UP
        showLive = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_roi_overlay").performTouchInput {
            val startY = height * (liveRoi.top + liveRoi.height / 2f)
            swipe(
                start = Offset(centerX, startY),
                end = Offset(centerX, startY - height * 0.20f),
                durationMillis = 200
            )
        }
        composeRule.waitForIdle()
        assertTrue("Live ROI top should move up (< 0.40f), was ${liveRoi.top}", liveRoi.top < 0.32f)
        dummyBitmap.recycle()
    }

    @Test
    fun appFormBottomSheet_andConfirmActionDialog_renderAndTriggerActions() {
        var sheetSaved = false
        var dialogConfirmed = false
        var showConfirm by mutableStateOf(false)

        composeRule.setContent {
            RentLedgerTheme {
                AppFormBottomSheet(
                    title = "Add Property",
                    subtitle = "Set property details",
                    onDismiss = {},
                    confirmText = "Save Property",
                    confirmTestTag = "test_save_sheet",
                    onConfirm = { sheetSaved = true }
                ) {
                    FormSectionCard(title = "Property Info") {
                        Text("Section Body")
                    }
                }
                if (showConfirm) {
                    ConfirmActionDialog(
                        title = "Delete Sunrise PG?",
                        message = "Confirm removal",
                        confirmText = "Delete",
                        isDestructive = true,
                        onConfirm = { dialogConfirmed = true },
                        onDismiss = { showConfirm = false }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add Property").assertIsDisplayed()
        composeRule.onNodeWithText("Property Info").assertIsDisplayed()
        composeRule.onNodeWithTag("test_save_sheet").performClick()
        assertTrue(sheetSaved)

        showConfirm = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete Sunrise PG?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertTrue(dialogConfirmed)
    }

    @Test
    fun fixedMonthlyWaterDefault_autoAppliesRegardlessOfOccupantCount() {
        val settings = OwnerSettings(
            defaultWaterMode = WaterRateMode.PER_ROOM,
            defaultWaterRate = 350.0
        )
        val prop = PropertyEntity(id = 1L, name = "Lakeview Homes", waterMode = WaterRateMode.INHERIT)
        val unit = UnitEntity(id = 1L, propertyId = 1L, unitName = "Flat 2B", baseRent = 12000.0, waterMode = WaterRateMode.INHERIT)

        val resolved1p = RentLedgerRepository.resolveWaterConfig(unit, prop, 1, settings)
        val resolved4p = RentLedgerRepository.resolveWaterConfig(unit, prop, 4, settings)

        assertEquals(WaterRateMode.PER_ROOM, resolved1p.mode)
        assertEquals("Fixed / Month", resolved1p.mode.label)
        assertEquals(350.0, resolved1p.computedCharge, 0.01)
        assertEquals(350.0, resolved4p.computedCharge, 0.01)
        assertEquals("Common default", resolved1p.sourceLabel)
    }
}
