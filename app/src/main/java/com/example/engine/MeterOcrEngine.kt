package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.data.model.MeterType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class NormalizedRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun movedBy(
        dx: Float,
        dy: Float,
        minLeft: Float = 0f,
        minTop: Float = 0f,
        maxRight: Float = 1f,
        maxBottom: Float = 1f
    ): NormalizedRoi {
        val w = width.coerceAtMost(maxRight - minLeft)
        val h = height.coerceAtMost(maxBottom - minTop)
        val newLeft = (left + dx).coerceIn(minLeft, maxRight - w)
        val newTop = (top + dy).coerceIn(minTop, maxBottom - h)
        return NormalizedRoi(
            left = newLeft,
            top = newTop,
            right = newLeft + w,
            bottom = newTop + h
        )
    }

    fun resizedVertical(
        deltaTop: Float,
        deltaBottom: Float,
        minHeight: Float = 0.06f,
        maxHeight: Float = 0.70f,
        minTop: Float = 0f,
        maxBottom: Float = 1f
    ): NormalizedRoi {
        var newTop = (top + deltaTop).coerceIn(minTop, bottom - minHeight)
        var newBottom = (bottom + deltaBottom).coerceIn(newTop + minHeight, maxBottom)
        if (newBottom - newTop > maxHeight) {
            if (deltaTop != 0f && deltaBottom == 0f) {
                newTop = newBottom - maxHeight
            } else {
                newBottom = newTop + maxHeight
            }
        }
        return copy(top = newTop, bottom = newBottom)
    }

    fun resizedHorizontal(
        deltaLeft: Float,
        deltaRight: Float,
        minWidth: Float = 0.22f,
        maxWidth: Float = 0.96f,
        minLeft: Float = 0.02f,
        maxRight: Float = 0.98f
    ): NormalizedRoi {
        var newLeft = (left + deltaLeft).coerceIn(minLeft, right - minWidth)
        var newRight = (right + deltaRight).coerceIn(newLeft + minWidth, maxRight)
        if (newRight - newLeft > maxWidth) {
            if (deltaLeft != 0f && deltaRight == 0f) {
                newLeft = newRight - maxWidth
            } else {
                newRight = newLeft + maxWidth
            }
        }
        return copy(left = newLeft, right = newRight)
    }

    companion object {
        // Default tight horizontal viewfinder box centered on screen
        val DEFAULT_SCAN_BOX = NormalizedRoi(
            left = 0.08f,
            top = 0.40f,
            right = 0.92f,
            bottom = 0.56f
        )
    }
}

data class MeterOcrResult(
    val parsed: ParsedMeterReading,
    val croppedPhotoPath: String
)

class MeterOcrEngine(private val context: Context) {

    private val mediaDir: File by lazy {
        File(context.filesDir, "media/meters").apply { mkdirs() }
    }

    private val tessRootDir: File by lazy {
        File(context.filesDir, "tesseract").apply { mkdirs() }
    }

    private fun ensureTessDataCopied(): Boolean {
        return try {
            val tessDataDir = File(tessRootDir, "tessdata").apply { mkdirs() }
            val target = File(tessDataDir, "letsgodigital.traineddata")
            if (!target.exists() || target.length() < 1024L) {
                context.assets.open("tessdata/letsgodigital.traineddata").use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            target.exists() && target.length() > 1024L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy letsgodigital.traineddata", e)
            false
        }
    }

    suspend fun decodeGalleryBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "gallery_preview_${System.currentTimeMillis()}.jpg")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) return@withContext null
            decodeOrientedBitmap(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode gallery image", e)
            null
        } finally {
            tempFile.delete()
        }
    }

    suspend fun processImageFile(
        sourceFile: File,
        roi: NormalizedRoi,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        previousReading: Double
    ): MeterOcrResult = withContext(Dispatchers.Default) {
        val bitmap = decodeOrientedBitmap(sourceFile)
            ?: throw IllegalArgumentException("Could not decode captured meter photo")
        try {
            processBitmap(bitmap, roi, meterType, analogHasRedDecimal, previousReading)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun processUri(
        uri: Uri,
        roi: NormalizedRoi,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        previousReading: Double
    ): MeterOcrResult = withContext(Dispatchers.Default) {
        val tempFile = File(context.cacheDir, "gallery_meter_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot read selected image")

        try {
            val bitmap = decodeOrientedBitmap(tempFile)
                ?: throw IllegalArgumentException("Could not decode selected gallery image")
            try {
                processBitmap(bitmap, roi, meterType, analogHasRedDecimal, previousReading)
            } finally {
                bitmap.recycle()
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun processBitmap(
        sourceBitmap: Bitmap,
        roi: NormalizedRoi,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        previousReading: Double
    ): MeterOcrResult = withContext(Dispatchers.Default) {
        val cropped = cropToNormalizedRoi(sourceBitmap, roi)
        val savedCroppedFile = File(mediaDir, "meter_${System.currentTimeMillis()}.jpg")
        FileOutputStream(savedCroppedFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val rawText = when (meterType) {
            MeterType.DIGITAL_LCD -> runDigitalLcdOcr(cropped)
            MeterType.ANALOG_DRUM -> runAnalogDrumOcr(cropped)
        }

        val parsed = MeterReadingParser.parse(
            rawOcrText = rawText,
            meterType = meterType,
            analogHasRedDecimal = analogHasRedDecimal,
            previousReading = previousReading
        )
        cropped.recycle()

        MeterOcrResult(
            parsed = parsed,
            croppedPhotoPath = savedCroppedFile.absolutePath
        )
    }

    private suspend fun runDigitalLcdOcr(cropped: Bitmap): String {
        val preprocessed = preprocessDigitalLcd(cropped)
        return try {
            val tessResult = runTesseractLetsGoDigital(preprocessed)
            if (tessResult.any { it.isDigit() }) {
                tessResult
            } else {
                // Fallback to ML Kit on both preprocessed & raw crop
                val mlPre = runMlKitText(preprocessed)
                if (mlPre.any { it.isDigit() }) mlPre else runMlKitText(cropped)
            }
        } finally {
            preprocessed.recycle()
        }
    }

    private suspend fun runAnalogDrumOcr(cropped: Bitmap): String {
        val enhancedNormal = enhanceContrastGrayscale(cropped, invert = false)
        val enhancedInverted = enhanceContrastGrayscale(cropped, invert = true)
        return try {
            val r1 = runMlKitText(cropped)
            val r2 = runMlKitText(enhancedInverted)
            val r3 = runMlKitText(enhancedNormal)
            // Combine lines so MeterReadingParser can pick the strongest 4-7 digit reading
            listOf(r2, r1, r3).filter { it.isNotBlank() }.joinToString("\n")
        } finally {
            enhancedNormal.recycle()
            enhancedInverted.recycle()
        }
    }

    private fun runTesseractLetsGoDigital(bitmap: Bitmap): String {
        if (!ensureTessDataCopied()) return ""
        var tess: TessBaseAPI? = null
        return try {
            tess = TessBaseAPI()
            val initialized = tess.init(tessRootDir.absolutePath, "letsgodigital")
            if (!initialized) {
                Log.w(TAG, "Tesseract init returned false")
                return ""
            }
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789.")
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            tess.setImage(bitmap)
            tess.utF8Text?.trim() ?: ""
        } catch (e: Throwable) {
            Log.e(TAG, "Tesseract4Android execution error, falling back to ML Kit", e)
            ""
        } finally {
            try {
                tess?.recycle()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun runMlKitText(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text.trim())
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit recognition failed", e)
                    if (cont.isActive) cont.resume("")
                }
        }
    }

    fun cropToNormalizedRoi(source: Bitmap, roi: NormalizedRoi): Bitmap {
        val left = (roi.left.coerceIn(0f, 0.95f) * source.width).roundToInt()
        val top = (roi.top.coerceIn(0f, 0.95f) * source.height).roundToInt()
        val right = (roi.right.coerceIn(0.05f, 1f) * source.width).roundToInt()
        val bottom = (roi.bottom.coerceIn(0.05f, 1f) * source.height).roundToInt()

        val w = max(32, right - left)
        val h = max(32, bottom - top)
        val safeLeft = left.coerceIn(0, max(0, source.width - w))
        val safeTop = top.coerceIn(0, max(0, source.height - h))
        val safeW = min(w, source.width - safeLeft)
        val safeH = min(h, source.height - safeTop)

        return Bitmap.createBitmap(source, safeLeft, safeTop, safeW, safeH)
    }

    /**
     * Digital 7-segment LCD preprocessor:
     * 1. Extracts Green-weighted luminance channel (great for green backlit LCDs like HPL).
     * 2. Stretches contrast across dynamic range.
     * 3. Computes Otsu threshold and outputs crisp dark digits on white background.
     */
    internal fun preprocessDigitalLcd(src: Bitmap): Bitmap {
        val targetWidth = max(480, min(960, src.width * 2))
        val scale = targetWidth.toFloat() / max(1, src.width)
        val targetHeight = max(80, (src.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)

        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()

        val lum = IntArray(w * h)
        var minL = 255
        var maxL = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // Green-channel biased luminance for backlit LCD contrast
            val y = ((r * 2 + g * 7 + b) / 10).coerceIn(0, 255)
            lum[i] = y
            if (y < minL) minL = y
            if (y > maxL) maxL = y
        }

        val range = max(1, maxL - minL)
        val hist = IntArray(256)
        for (i in lum.indices) {
            val stretched = (((lum[i] - minL) * 255) / range).coerceIn(0, 255)
            lum[i] = stretched
            hist[stretched]++
        }

        val threshold = computeOtsuThreshold(hist, lum.size)
        var darkCount = 0
        for (y in lum) if (y <= threshold) darkCount++
        // 7-segment digits typically occupy < 50% of the box; if darkCount > 55%, LCD has light digits on dark bg -> invert
        val invert = darkCount > (lum.size * 0.55)

        val outPixels = IntArray(w * h)
        for (i in lum.indices) {
            val isDark = lum[i] <= threshold
            val foreground = if (invert) !isDark else isDark
            outPixels[i] = if (foreground) Color.BLACK else Color.WHITE
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Analog drum preprocessor:
     * Boosts contrast and optionally inverts white-on-black drum digits into black-on-white for ML Kit.
     */
    internal fun enhanceContrastGrayscale(src: Bitmap, invert: Boolean): Bitmap {
        val targetWidth = max(480, min(960, src.width * 2))
        val scale = targetWidth.toFloat() / max(1, src.width)
        val targetHeight = max(80, (src.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)

        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()

        val lum = IntArray(w * h)
        var minL = 255
        var maxL = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = ((r * 3 + g * 6 + b) / 10).coerceIn(0, 255)
            lum[i] = y
            if (y < minL) minL = y
            if (y > maxL) maxL = y
        }

        val range = max(1, maxL - minL)
        val outPixels = IntArray(w * h)
        for (i in lum.indices) {
            var v = (((lum[i] - minL) * 255) / range).coerceIn(0, 255)
            // Apply mild S-curve contrast boost
            v = if (v < 128) (v * v) / 128 else 255 - ((255 - v) * (255 - v)) / 128
            if (invert) v = 255 - v
            outPixels[i] = Color.rgb(v, v, v)
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, w, 0, 0, w, h)
        }
    }

    private fun computeOtsuThreshold(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (t in 0..255) sum += t * hist[t].toDouble()

        var sumB = 0.0
        var wB = 0
        var varMax = 0.0
        var threshold = 128

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }
        return threshold
    }

    private fun decodeOrientedBitmap(file: File): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    fun saveBitmapCopy(bitmap: Bitmap, prefix: String = "crop"): String {
        val file = File(mediaDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file.absolutePath
    }

    companion object {
        private const val TAG = "MeterOcrEngine"
    }
}
