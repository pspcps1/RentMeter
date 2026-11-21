package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.preferences.ThemeMode
import com.example.engine.RentReminderManager
import com.example.ui.components.BiometricLockScreen
import com.example.ui.navigation.Route
import com.example.ui.screens.BillPreviewScreen
import com.example.ui.screens.CheckInScreen
import com.example.ui.screens.CheckOutScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.InvoicePaymentScreen
import com.example.ui.screens.MeterScannerScreen
import com.example.ui.screens.PropertiesScreen
import com.example.ui.screens.RentAgreementScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TenantProfileScreen
import com.example.ui.theme.RentLedgerTheme
import com.example.ui.viewmodel.RentLedgerViewModel

class MainActivity : FragmentActivity() {

    private var isUnlocked by mutableStateOf(true)
    private var lockPromptTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as RentLedgerApplication

        setContent {
            val viewModel: RentLedgerViewModel = viewModel(
                factory = RentLedgerViewModel.Factory(app)
            )
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.biometricLockEnabled) {
                if (settings.biometricLockEnabled && !lockPromptTriggered) {
                    lockPromptTriggered = true
                    isUnlocked = false
                    triggerBiometricAuth()
                } else if (!settings.biometricLockEnabled) {
                    isUnlocked = true
                }
            }

            RentLedgerTheme(
                themeMode = settings.themeMode,
                appAccent = settings.appAccent
            ) {
                if (!isUnlocked && settings.biometricLockEnabled) {
                    BiometricLockScreen(onUnlockClick = { triggerBiometricAuth() })
                } else {
                    RentLedgerAppShell(viewModel = viewModel)
                }
            }
        }
    }

    private fun triggerBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                        errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                    ) {
                        isUnlocked = true
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock RentLedger")
            .setSubtitle("Verify identity to access property & tenant records")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}

private data class TopNavItem(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun RentLedgerAppShell(viewModel: RentLedgerViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val unitsWithContext by viewModel.unitsWithContext.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val sharedCharges by viewModel.sharedCharges.collectAsStateWithLifecycle()
    val leases by viewModel.leases.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedPropertyId by viewModel.selectedPropertyId.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(settings.rentReminderEnabled, settings.rentGenerationDay) {
        RentReminderManager.scheduleDailyReminder(context, settings)
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val topDestinations = remember {
        listOf(
            TopNavItem(Route.Dashboard, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
            TopNavItem(Route.Properties, "Properties", Icons.Filled.Apartment, Icons.Outlined.Apartment, "nav_properties"),
            TopNavItem(Route.Expenses, "Expenses", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_expenses"),
            TopNavItem(Route.Reports, "Reports", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_reports")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val showTopLevelChrome = currentDest?.let { dest ->
        dest.hasRoute<Route.Dashboard>() ||
            dest.hasRoute<Route.Properties>() ||
            dest.hasRoute<Route.Expenses>() ||
            dest.hasRoute<Route.Reports>()
    } ?: true

    fun navigateTopLevel(route: Route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = showTopLevelChrome && maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelChrome && !useRail) {
                    NavigationBar {
                        topDestinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        topDestinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Route.Dashboard
                    ) {
                        composable<Route.Dashboard> {
                            DashboardScreen(
                                summary = dashboardSummary,
                                unitsWithContext = unitsWithContext,
                                invoices = invoices,
                                onNavigateToProperties = { navigateTopLevel(Route.Properties) },
                                onNavigateToSettings = { navController.navigate(Route.Settings) },
                                onNavigateToExpenses = { navigateTopLevel(Route.Expenses) },
                                onLaunchMeterScanner = { unitId ->
                                    navController.navigate(Route.MeterScanner(unitId = unitId, forCheckout = false))
                                },
                                onOpenInvoice = { invoiceId ->
                                    navController.navigate(Route.InvoicePayment(invoiceId))
                                },
                                onOpenBillPreview = { unitId ->
                                    navController.navigate(Route.BillPreview(unitId = unitId))
                                },
                                onLoadDemoData = { viewModel.loadSampleDemoData() },
                                themeMode = settings.themeMode,
                                onCycleThemeMode = {
                                    val next = when (settings.themeMode) {
                                        ThemeMode.SYSTEM -> ThemeMode.DARK
                                        ThemeMode.DARK -> ThemeMode.LIGHT
                                        ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                    }
                                    viewModel.setTheme(next, settings.appAccent)
                                }
                            )
                        }

                        composable<Route.Properties> {
                            PropertiesScreen(
                                properties = properties,
                                unitsWithContext = unitsWithContext,
                                selectedPropertyId = selectedPropertyId,
                                settings = settings,
                                onSelectProperty = { viewModel.selectProperty(it) },
                                onSaveProperty = { id, name, type, address, elecRate, waterMode, waterRate, depositOverride, elecMode ->
                                    viewModel.saveProperty(id, name, type, address, elecRate, waterMode, waterRate, depositOverride, elecMode)
                                },
                                onDeleteProperty = { viewModel.deleteProperty(it) },
                                onSaveUnit = { id, propId, unitName, floor, rent, elecRate, waterMode, waterRate, depositOverride, meterType, redDec, lastRead, leaseId, elecMode ->
                                    viewModel.saveUnit(id, propId, unitName, floor, rent, elecRate, waterMode, waterRate, depositOverride, meterType, redDec, lastRead, leaseId, elecMode)
                                },
                                onSaveUnitsBulk = { propId, names, floor, rent, elecRate, waterMode, waterRate, depositOverride, meterType, redDec, startRead, elecMode ->
                                    viewModel.saveUnitsBulk(propId, names, floor, rent, elecRate, waterMode, waterRate, depositOverride, meterType, redDec, startRead, elecMode)
                                },
                                onDeleteUnit = { viewModel.deleteUnit(it) },
                                onUpdateOccupants = { leaseId, count -> viewModel.updateOccupants(leaseId, count) },
                                onUpdateHousehold = { leaseId, persons, childrenBelow5, childNotes ->
                                    viewModel.updateHousehold(leaseId, persons, childrenBelow5, childNotes)
                                },
                                onUpdateSecurityDeposit = { leaseId, deposit -> viewModel.updateSecurityDeposit(leaseId, deposit) },
                                onOpenTenantProfile = { leaseId ->
                                    navController.navigate(Route.TenantProfile(leaseId))
                                },
                                onCheckIn = { unitId ->
                                    navController.navigate(Route.CheckIn(unitId))
                                },
                                onScanMeter = { unitId ->
                                    navController.navigate(Route.MeterScanner(unitId = unitId, forCheckout = false))
                                },
                                onOpenBillPreview = { unitId ->
                                    navController.navigate(Route.BillPreview(unitId = unitId))
                                },
                                onOpenInvoice = { invoiceId ->
                                    navController.navigate(Route.InvoicePayment(invoiceId))
                                },
                                onCheckOut = { unitId ->
                                    navController.navigate(Route.CheckOut(unitId = unitId))
                                }
                            )
                        }

                        composable<Route.Expenses> {
                            ExpensesScreen(
                                properties = properties,
                                unitsWithContext = unitsWithContext,
                                expenses = expenses,
                                sharedCharges = sharedCharges,
                                onAddExpense = { propId, allProps, cat, title, amt, month, notes, splitMode, unitId ->
                                    viewModel.saveExpense(propId, allProps, cat, title, amt, month, notes, splitMode, unitId)
                                },
                                onDeleteExpense = { viewModel.deleteExpense(it) }
                            )
                        }

                        composable<Route.Reports> {
                            ReportsScreen(
                                properties = properties,
                                unitsWithContext = unitsWithContext,
                                invoices = invoices,
                                expenses = expenses,
                                leases = leases,
                                onOpenInvoice = { invoiceId ->
                                    navController.navigate(Route.InvoicePayment(invoiceId))
                                },
                                onOpenTenantProfile = { leaseId ->
                                    navController.navigate(Route.TenantProfile(leaseId))
                                }
                            )
                        }

                        composable<Route.Settings> {
                            SettingsScreen(
                                settings = settings,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<Route.CheckIn> { entry ->
                            val args = entry.toRoute<Route.CheckIn>()
                            CheckInScreen(
                                unitId = args.unitId,
                                defaultDueDay = settings.defaultDueDay,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onCompleted = { navController.popBackStack() },
                                onCompletedToAgreement = { leaseId ->
                                    navController.navigate(Route.RentAgreement(leaseId)) {
                                        popUpTo<Route.CheckIn> { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<Route.MeterScanner> { entry ->
                            val args = entry.toRoute<Route.MeterScanner>()
                            MeterScannerScreen(
                                unitId = args.unitId,
                                forCheckout = args.forCheckout,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onScanComplete = { result ->
                                    if (args.forCheckout) {
                                        navController.navigate(
                                            Route.CheckOut(
                                                unitId = args.unitId,
                                                finalReading = result.parsed.formattedValue,
                                                finalPhotoPath = result.croppedPhotoPath
                                            )
                                        ) {
                                            popUpTo(Route.CheckOut(unitId = args.unitId)) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(
                                            Route.BillPreview(
                                                unitId = args.unitId,
                                                detectedReading = result.parsed.formattedValue,
                                                croppedPhotoPath = result.croppedPhotoPath,
                                                ocrRawText = result.parsed.rawText,
                                                isSuspicious = result.parsed.isSuspicious,
                                                warningMessage = result.parsed.warningMessage ?: ""
                                            )
                                        ) {
                                            popUpTo<Route.MeterScanner> { inclusive = true }
                                        }
                                    }
                                },
                                onManualEntry = {
                                    if (args.forCheckout) {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate(Route.BillPreview(unitId = args.unitId)) {
                                            popUpTo<Route.MeterScanner> { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable<Route.BillPreview> { entry ->
                            val args = entry.toRoute<Route.BillPreview>()
                            BillPreviewScreen(
                                unitId = args.unitId,
                                detectedReading = args.detectedReading,
                                croppedPhotoPath = args.croppedPhotoPath,
                                ocrRawText = args.ocrRawText,
                                initialWarningMessage = args.warningMessage,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRescan = {
                                    navController.navigate(Route.MeterScanner(unitId = args.unitId, forCheckout = false)) {
                                        popUpTo<Route.BillPreview> { inclusive = true }
                                    }
                                },
                                onInvoiceCreated = { invoiceId ->
                                    navController.navigate(Route.InvoicePayment(invoiceId)) {
                                        popUpTo<Route.BillPreview> { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<Route.InvoicePayment> { entry ->
                            val args = entry.toRoute<Route.InvoicePayment>()
                            InvoicePaymentScreen(
                                invoiceId = args.invoiceId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<Route.CheckOut> { entry ->
                            val args = entry.toRoute<Route.CheckOut>()
                            CheckOutScreen(
                                unitId = args.unitId,
                                scannedFinalReading = args.finalReading,
                                scannedFinalPhotoPath = args.finalPhotoPath,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onLaunchFinalScan = {
                                    navController.navigate(Route.MeterScanner(unitId = args.unitId, forCheckout = true))
                                },
                                onFinished = { navController.popBackStack() }
                            )
                        }

                        composable<Route.TenantProfile> { entry ->
                            val args = entry.toRoute<Route.TenantProfile>()
                            TenantProfileScreen(
                                leaseId = args.leaseId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenInvoice = { invoiceId ->
                                    navController.navigate(Route.InvoicePayment(invoiceId))
                                },
                                onCreateAgreement = { leaseId ->
                                    navController.navigate(Route.RentAgreement(leaseId))
                                }
                            )
                        }

                        composable<Route.RentAgreement> { entry ->
                            val args = entry.toRoute<Route.RentAgreement>()
                            RentAgreementScreen(
                                leaseId = args.leaseId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
