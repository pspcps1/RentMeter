package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.UnitEntity
import com.example.data.model.MeterType
import com.example.engine.MeterOcrResult
import com.example.engine.NormalizedRoi
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.viewmodel.RentLedgerViewModel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class RoiDragMode {
    MOVE,
    RESIZE_TOP,
    RESIZE_BOTTOM,
    RESIZE_LEFT,
    RESIZE_RIGHT
}

private val DefaultGalleryRoi = NormalizedRoi(
    left = 0.08f,
    top = 0.38f,
    right = 0.92f,
    bottom = 0.56f
)

@Composable
fun MeterScannerScreen(
    unitId: Long,
    forCheckout: Boolean,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit,
    onScanComplete: (MeterOcrResult) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live camera scan box (draggable up/down & vertically resizable)
    var liveRoi by remember { mutableStateOf(NormalizedRoi.DEFAULT_SCAN_BOX) }

    // Manual crop state when user uploads from Gallery (WhatsApp photo)
    var galleryBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var galleryRoi by remember { mutableStateOf(DefaultGalleryRoi) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(unitId) {
        unit = viewModel.repository.getUnitById(unitId)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = viewModel.ocrEngine.decodeGalleryBitmap(uri)
                if (bmp != null) {
                    galleryBitmap?.recycle()
                    galleryBitmap = bmp
                    galleryRoi = DefaultGalleryRoi
                    errorMessage = null
                } else {
                    errorMessage = "Could not load selected image"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            galleryBitmap?.recycle()
        }
    }

    // If gallery image selected, show Interactive Manual Crop Overlay
    val currentGalleryBmp = galleryBitmap
    if (currentGalleryBmp != null) {
        GalleryManualCropView(
            bitmap = currentGalleryBmp,
            unit = unit,
            roi = galleryRoi,
            isProcessing = isProcessing,
            onRoiChange = { galleryRoi = it },
            onCancel = {
                galleryBitmap?.recycle()
                galleryBitmap = null
            },
            onConfirmCrop = {
                val targetUnit = unit ?: return@GalleryManualCropView
                isProcessing = true
                scope.launch {
                    try {
                        val result = viewModel.ocrEngine.processBitmap(
                            sourceBitmap = currentGalleryBmp,
                            roi = galleryRoi,
                            meterType = targetUnit.meterType,
                            analogHasRedDecimal = targetUnit.analogHasRedDecimal,
                            previousReading = targetUnit.lastReading
                        )
                        isProcessing = false
                        onScanComplete(result)
                    } catch (e: Exception) {
                        isProcessing = false
                        errorMessage = "OCR failed: ${e.message}"
                    }
                }
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("MeterScanner", "CameraX bind failed", e)
                            errorMessage = "Camera preview unavailable on this device"
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Draggable Horizontal ROI Viewfinder Box Overlay
            DraggableRoiViewfinderOverlay(
                roi = liveRoi,
                onRoiChange = { liveRoi = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission Denied Fallback UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Camera permission needed for live meter scan",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can grant camera access, upload a tenant's WhatsApp meter photo, or type the reading manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("Upload Gallery Photo", color = Color.White)
                }
            }
        }

        // Top Bar Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = unit?.let { "${it.unitName} • ${if (forCheckout) "Final Reading" else "Monthly Scan"}" }
                                ?: "Meter Scanner",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        unit?.let { u ->
                            Text(
                                text = "Prev: ${PdfInvoiceGenerator.formatUnits(u.lastReading)} kWh",
                                color = Color(0xFFFDE68A),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        val next = !torchEnabled
                        torchEnabled = next
                        camera?.cameraControl?.enableTorch(next)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("torch_toggle_button")
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flashlight",
                        tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Meter-specific Helper Tip Banner
            unit?.let { u ->
                val tipText = if (u.meterType == MeterType.DIGITAL_LCD) {
                    "Wait for kWh on screen before capturing (cycles Date → Time → kWh)"
                } else {
                    if (u.analogHasRedDecimal) {
                        "Analog Drum: Fit all 6 wheels inside box (red tenth wheel auto-dotted)"
                    } else {
                        "Analog Drum: Fit ONLY the main reading wheels inside the box"
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F4C5C).copy(alpha = 0.88f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = tipText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Controls (Gallery Upload, Capture Shutter, Manual Entry)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Drag box up/down to align meter numbers",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                if (hasCameraPermission && liveRoi != NormalizedRoi.DEFAULT_SCAN_BOX) {
                    TextButton(
                        onClick = { liveRoi = NormalizedRoi.DEFAULT_SCAN_BOX },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .testTag("reset_roi_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset scan box",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Center", color = Color(0xFFFBBF24), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("gallery_upload_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }

                // Primary Capture Button
                Surface(
                    onClick = {
                        val targetUnit = unit ?: return@Surface
                        if (!hasCameraPermission || isProcessing) return@Surface
                        isProcessing = true
                        errorMessage = null
                        val captureFile = File(context.cacheDir, "raw_capture_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(captureFile).build()
                        val captureRoi = liveRoi
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        try {
                                            val result = viewModel.ocrEngine.processImageFile(
                                                sourceFile = captureFile,
                                                roi = captureRoi,
                                                meterType = targetUnit.meterType,
                                                analogHasRedDecimal = targetUnit.analogHasRedDecimal,
                                                previousReading = targetUnit.lastReading
                                            )
                                            captureFile.delete()
                                            isProcessing = false
                                            onScanComplete(result)
                                        } catch (e: Exception) {
                                            captureFile.delete()
                                            isProcessing = false
                                            errorMessage = "Could not process photo: ${e.message}"
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isProcessing = false
                                    errorMessage = "Capture failed: ${exception.message}"
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    color = Color(0xFFFBBF24),
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("capture_meter_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capture Meter Reading",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onManualEntry,
                    modifier = Modifier.testTag("manual_entry_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual")
                }
            }
        }
    }
}

@Composable
internal fun DraggableRoiViewfinderOverlay(
    roi: NormalizedRoi,
    onRoiChange: (NormalizedRoi) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestRoi by rememberUpdatedState(roi)
    val latestOnRoiChange by rememberUpdatedState(onRoiChange)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val edgeTolerancePx = with(density) { 14.dp.toPx() }
        val pillOffsetY = with(density) {
            val topPx = constraints.maxHeight * roi.top
            max(110f, topPx - 32.dp.toPx()).roundToInt()
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("live_roi_overlay")
                .pointerInput(Unit) {
                    var dragMode = RoiDragMode.MOVE
                    var activeRoi = latestRoi
                    detectDragGestures(
                        onDragStart = { start ->
                            activeRoi = latestRoi
                            val topPx = size.height * activeRoi.top
                            val bottomPx = size.height * activeRoi.bottom
                            val vTol = minOf(edgeTolerancePx, (bottomPx - topPx) * 0.22f)
                            val inHorizontal = start.x in (size.width * activeRoi.left - edgeTolerancePx)..(size.width * activeRoi.right + edgeTolerancePx)
                            dragMode = when {
                                inHorizontal && abs(start.y - topPx) <= vTol -> RoiDragMode.RESIZE_TOP
                                inHorizontal && abs(start.y - bottomPx) <= vTol -> RoiDragMode.RESIZE_BOTTOM
                                else -> RoiDragMode.MOVE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / max(1, size.width).toFloat()
                            val dy = dragAmount.y / max(1, size.height).toFloat()
                            activeRoi = when (dragMode) {
                                RoiDragMode.RESIZE_TOP -> activeRoi.resizedVertical(
                                    deltaTop = dy,
                                    deltaBottom = 0f,
                                    minHeight = 0.08f,
                                    maxHeight = 0.36f,
                                    minTop = 0.16f,
                                    maxBottom = 0.74f
                                )
                                RoiDragMode.RESIZE_BOTTOM -> activeRoi.resizedVertical(
                                    deltaTop = 0f,
                                    deltaBottom = dy,
                                    minHeight = 0.08f,
                                    maxHeight = 0.36f,
                                    minTop = 0.16f,
                                    maxBottom = 0.74f
                                )
                                else -> activeRoi.movedBy(
                                    dx = dx * 0.35f,
                                    dy = dy,
                                    minLeft = 0.02f,
                                    minTop = 0.16f,
                                    maxRight = 0.98f,
                                    maxBottom = 0.74f
                                )
                            }
                            latestOnRoiChange(activeRoi)
                        }
                    )
                }
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val left = size.width * roi.left
            val top = size.height * roi.top
            val width = size.width * roi.width
            val height = size.height * roi.height

            // Dimmed scrim outside the scan box
            drawRect(color = Color.Black.copy(alpha = 0.62f))
            // Punch transparent cutout inside scan box
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                blendMode = BlendMode.Clear
            )
            // Bright amber scan frame + corner/edge drag handles
            drawRoiFrameWithHandles(
                left = left,
                top = top,
                width = width,
                height = height,
                cornerRadiusPx = 12.dp.toPx(),
                showHorizontalHandles = false
            )
        }

        // Floating drag hint pill locked above the scan box
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFBBF24),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, pillOffsetY) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Drag up / down",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun GalleryManualCropView(
    bitmap: Bitmap,
    unit: UnitEntity?,
    roi: NormalizedRoi,
    isProcessing: Boolean,
    onRoiChange: (NormalizedRoi) -> Unit,
    onCancel: () -> Unit,
    onConfirmCrop: () -> Unit
) {
    val latestRoi by rememberUpdatedState(roi)
    val latestOnRoiChange by rememberUpdatedState(onRoiChange)
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel crop", tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Crop Meter Digits", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Drag box up/down over ${unit?.meterType?.label ?: "meter"} digits",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(
                onClick = { onRoiChange(DefaultGalleryRoi) },
                modifier = Modifier.testTag("crop_reset_button")
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Reset crop box", tint = Color(0xFFFBBF24))
            }
        }

        // Aspect-ratio locked container so the uploaded photo is never stretched and crop coordinates map 1:1
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmapAspect = (bitmap.width.toFloat() / max(1, bitmap.height).toFloat()).coerceIn(0.25f, 4f)
            val containerAspect = maxWidth / maxHeight.coerceAtLeast(1.dp)
            val (fittedW, fittedH) = if (bitmapAspect > containerAspect) {
                maxWidth to (maxWidth / bitmapAspect)
            } else {
                (maxHeight * bitmapAspect) to maxHeight
            }

            Box(
                modifier = Modifier.size(width = fittedW, height = fittedH)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Uploaded meter photo",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                val edgeTolerancePx = with(density) { 14.dp.toPx() }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("gallery_crop_canvas")
                        .pointerInput(Unit) {
                            var dragMode = RoiDragMode.MOVE
                            var activeRoi = latestRoi
                            detectDragGestures(
                                onDragStart = { start ->
                                    activeRoi = latestRoi
                                    val l = size.width * activeRoi.left
                                    val t = size.height * activeRoi.top
                                    val rt = size.width * activeRoi.right
                                    val b = size.height * activeRoi.bottom
                                    val vTol = minOf(edgeTolerancePx, (b - t) * 0.22f)
                                    val hTol = minOf(edgeTolerancePx, (rt - l) * 0.15f)
                                    val nearH = start.x in (l - hTol)..(rt + hTol)
                                    val nearV = start.y in (t - vTol)..(b + vTol)
                                    dragMode = when {
                                        nearH && abs(start.y - t) <= vTol -> RoiDragMode.RESIZE_TOP
                                        nearH && abs(start.y - b) <= vTol -> RoiDragMode.RESIZE_BOTTOM
                                        nearV && abs(start.x - l) <= hTol -> RoiDragMode.RESIZE_LEFT
                                        nearV && abs(start.x - rt) <= hTol -> RoiDragMode.RESIZE_RIGHT
                                        else -> RoiDragMode.MOVE
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x / max(1, size.width).toFloat()
                                    val dy = dragAmount.y / max(1, size.height).toFloat()
                                    activeRoi = when (dragMode) {
                                        RoiDragMode.RESIZE_TOP -> activeRoi.resizedVertical(
                                            deltaTop = dy,
                                            deltaBottom = 0f,
                                            minHeight = 0.06f,
                                            maxHeight = 0.70f,
                                            minTop = 0f,
                                            maxBottom = 1f
                                        )
                                        RoiDragMode.RESIZE_BOTTOM -> activeRoi.resizedVertical(
                                            deltaTop = 0f,
                                            deltaBottom = dy,
                                            minHeight = 0.06f,
                                            maxHeight = 0.70f,
                                            minTop = 0f,
                                            maxBottom = 1f
                                        )
                                        RoiDragMode.RESIZE_LEFT -> activeRoi.resizedHorizontal(
                                            deltaLeft = dx,
                                            deltaRight = 0f,
                                            minWidth = 0.18f,
                                            maxWidth = 0.98f,
                                            minLeft = 0f,
                                            maxRight = 1f
                                        )
                                        RoiDragMode.RESIZE_RIGHT -> activeRoi.resizedHorizontal(
                                            deltaLeft = 0f,
                                            deltaRight = dx,
                                            minWidth = 0.18f,
                                            maxWidth = 0.98f,
                                            minLeft = 0f,
                                            maxRight = 1f
                                        )
                                        RoiDragMode.MOVE -> activeRoi.movedBy(
                                            dx = dx,
                                            dy = dy,
                                            minLeft = 0f,
                                            minTop = 0f,
                                            maxRight = 1f,
                                            maxBottom = 1f
                                        )
                                    }
                                    latestOnRoiChange(activeRoi)
                                }
                            )
                        }
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    val l = size.width * roi.left
                    val t = size.height * roi.top
                    val w = size.width * roi.width
                    val h = size.height * roi.height
                    drawRect(Color.Black.copy(alpha = 0.58f))
                    drawRect(
                        color = Color.Transparent,
                        topLeft = Offset(l, t),
                        size = Size(w, h),
                        blendMode = BlendMode.Clear
                    )
                    drawRoiFrameWithHandles(
                        left = l,
                        top = t,
                        width = w,
                        height = h,
                        cornerRadiusPx = 4.dp.toPx(),
                        showHorizontalHandles = true
                    )
                }
            }
        }

        // Height & Width Fine Adjustment Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onRoiChange(roi.resizedVertical(deltaTop = 0.02f, deltaBottom = -0.02f)) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("crop_tighter_button")
            ) {
                Text("Tighter", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onRoiChange(roi.resizedVertical(deltaTop = -0.02f, deltaBottom = 0.02f)) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("crop_taller_button")
            ) {
                Text("Taller", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onRoiChange(roi.resizedHorizontal(deltaLeft = 0.03f, deltaRight = -0.03f, minLeft = 0f, maxRight = 1f)) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Narrower", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onRoiChange(roi.resizedHorizontal(deltaLeft = -0.03f, deltaRight = 0.03f, minLeft = 0f, maxRight = 1f)) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Wider", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        Button(
            onClick = onConfirmCrop,
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp)
                .testTag("scan_cropped_button")
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running On-Device OCR...")
            } else {
                Icon(Icons.Default.Crop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Cropped Meter Digits")
            }
        }
    }
}

private fun DrawScope.drawRoiFrameWithHandles(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cornerRadiusPx: Float,
    showHorizontalHandles: Boolean
) {
    val amber = Color(0xFFFBBF24)
    val right = left + width
    val bottom = top + height
    val centerX = left + width / 2f
    val centerY = top + height / 2f

    // Subtle crisp amber border
    drawRoundRect(
        color = amber.copy(alpha = 0.9f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(width = 2.5.dp.toPx())
    )

    // Top & bottom center drag/resize handle bars
    val barHalfW = minOf(22.dp.toPx(), width * 0.18f)
    val barStroke = 4.dp.toPx()
    drawLine(
        color = Color.White,
        start = Offset(centerX - barHalfW, top),
        end = Offset(centerX + barHalfW, top),
        strokeWidth = barStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(centerX - barHalfW, bottom),
        end = Offset(centerX + barHalfW, bottom),
        strokeWidth = barStroke,
        cap = StrokeCap.Round
    )

    if (showHorizontalHandles) {
        val barHalfH = minOf(16.dp.toPx(), height * 0.28f)
        drawLine(
            color = Color.White,
            start = Offset(left, centerY - barHalfH),
            end = Offset(left, centerY + barHalfH),
            strokeWidth = barStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(right, centerY - barHalfH),
            end = Offset(right, centerY + barHalfH),
            strokeWidth = barStroke,
            cap = StrokeCap.Round
        )
    }

    // Subtle corner L-brackets for clear viewfinder alignment
    val bracketLen = minOf(16.dp.toPx(), width * 0.15f, height * 0.35f)
    val bracketStroke = 3.5.dp.toPx()
    // Top-left
    drawLine(amber, Offset(left, top + bracketLen), Offset(left, top), bracketStroke, cap = StrokeCap.Round)
    drawLine(amber, Offset(left, top), Offset(left + bracketLen, top), bracketStroke, cap = StrokeCap.Round)
    // Top-right
    drawLine(amber, Offset(right - bracketLen, top), Offset(right, top), bracketStroke, cap = StrokeCap.Round)
    drawLine(amber, Offset(right, top), Offset(right, top + bracketLen), bracketStroke, cap = StrokeCap.Round)
    // Bottom-left
    drawLine(amber, Offset(left, bottom - bracketLen), Offset(left, bottom), bracketStroke, cap = StrokeCap.Round)
    drawLine(amber, Offset(left, bottom), Offset(left + bracketLen, bottom), bracketStroke, cap = StrokeCap.Round)
    // Bottom-right
    drawLine(amber, Offset(right - bracketLen, bottom), Offset(right, bottom), bracketStroke, cap = StrokeCap.Round)
    drawLine(amber, Offset(right, bottom - bracketLen), Offset(right, bottom), bracketStroke, cap = StrokeCap.Round)
}
