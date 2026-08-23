package com.perdolique.poleparkla.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.NormalizedPhotoRect
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class StoredGalleryPhoto(
    val photo: ReportPhoto,
    val exifLocation: LocationSnapshot?,
    val metadataNeedsReview: Boolean,
)

data class PhotoExifMetadata(
    val capturedAtEpochMillis: Long? = null,
    val location: LocationSnapshot? = null,
)

internal suspend fun decodeBitmapWithCancellationCleanup(
    dispatcher: CoroutineDispatcher,
    decode: () -> Bitmap,
): Bitmap {
    val pendingResult = AtomicReference<Bitmap?>()
    return try {
        withContext(dispatcher) {
            decode().also(pendingResult::set)
        }.also { delivered ->
            pendingResult.compareAndSet(delivered, null)
        }
    } catch (error: CancellationException) {
        pendingResult.getAndSet(null)?.recycle()
        throw error
    }
}

class PhotoStore(
    private val context: Context,
    private val reportsDirectory: File,
) {
    private val plateCropDecodeMutex = Mutex()

    fun createCameraTarget(reportId: String): File {
        val directory = reportDirectory(reportId)
        return File(directory, "${UUID.randomUUID()}.jpg")
    }

    fun cameraPhoto(reportId: String, file: File, isPrimary: Boolean): ReportPhoto = ReportPhoto(
        id = file.nameWithoutExtension,
        reportId = reportId,
        filePath = file.absolutePath,
        source = PhotoSource.CAMERA,
        capturedAtEpochMillis = readExif(file).capturedAtEpochMillis ?: System.currentTimeMillis(),
        isPrimary = isPrimary,
    )

    suspend fun importGalleryPhoto(
        reportId: String,
        uri: Uri,
        isPrimary: Boolean,
        fallbackLocation: LocationSnapshot?,
    ): StoredGalleryPhoto = withContext(Dispatchers.IO) {
        val extension = context.contentResolver.getType(uri)
            ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
            ?.takeIf(String::isNotBlank)
            ?: "jpg"
        val id = UUID.randomUUID().toString()
        val destination = File(reportDirectory(reportId), "$id.$extension")
        try {
            val declaredLength = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            require(declaredLength == null || declaredLength < 0 || declaredLength <= MAX_IMPORTED_BYTES) {
                "Selected photo is larger than 50 MB"
            }
            openPhotoInput(uri).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyToWithLimit(output, MAX_IMPORTED_BYTES)
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }

        val exif = readExif(destination)
        val capturedAt = exif.capturedAtEpochMillis ?: System.currentTimeMillis()
        val location = exif.location ?: fallbackLocation?.copy(capturedAtEpochMillis = capturedAt)
        StoredGalleryPhoto(
            photo = ReportPhoto(
                id = id,
                reportId = reportId,
                filePath = destination.absolutePath,
                source = PhotoSource.GALLERY,
                capturedAtEpochMillis = capturedAt,
                isPrimary = isPrimary,
            ),
            exifLocation = location,
            metadataNeedsReview = exif.location == null || exif.capturedAtEpochMillis == null,
        )
    }

    suspend fun readExifMetadata(photo: ReportPhoto): PhotoExifMetadata = withContext(Dispatchers.IO) {
        readExif(File(photo.filePath))
    }

    suspend fun prepareCloudImage(photo: ReportPhoto): File = withContext(Dispatchers.IO) {
        val outputDirectory = File(context.cacheDir, "cloud/${photo.reportId}").ensureDirectory()
        val output = File(outputDirectory, "${photo.id}.jpg")
        var bitmap = decodeOrientedBitmap(File(photo.filePath), CLOUD_MAX_LONG_SIDE)
        try {
            var quality = CLOUD_START_QUALITY
            while (true) {
                FileOutputStream(output).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                        "Unable to encode cloud image"
                    }
                }
                if (output.length() <= CLOUD_MAX_BYTES) break

                if (quality > CLOUD_MIN_QUALITY) {
                    quality = maxOf(CLOUD_MIN_QUALITY, quality - CLOUD_QUALITY_STEP)
                    continue
                }

                val currentLongSide = maxOf(bitmap.width, bitmap.height)
                require(currentLongSide > CLOUD_MIN_LONG_SIDE) { "Image cannot be reduced below 1 MB" }
                val targetLongSide = maxOf(
                    CLOUD_MIN_LONG_SIDE,
                    (currentLongSide * CLOUD_SCALE_STEP).roundToInt(),
                )
                val scale = targetLongSide.toFloat() / currentLongSide
                val resized = bitmap.scale(
                    width = maxOf(1, (bitmap.width * scale).roundToInt()),
                    height = maxOf(1, (bitmap.height * scale).roundToInt()),
                )
                if (resized !== bitmap) bitmap.recycle()
                bitmap = resized
                quality = CLOUD_START_QUALITY
            }
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun prepareEmailCopies(photos: List<ReportPhoto>): List<File> =
        withContext(Dispatchers.IO) {
            if (photos.isEmpty()) return@withContext emptyList()
            val reportId = photos.first().reportId
            require(photos.all { it.reportId == reportId }) { "Mail attachments must belong to one report" }
            val outputDirectory = File(context.cacheDir, "mail/$reportId")
            deleteDirectory(outputDirectory)
            outputDirectory.ensureDirectory()
            try {
                photos.map { photo ->
                    prepareEmailCopy(photo, outputDirectory)
                }
            } catch (error: Throwable) {
                outputDirectory.deleteRecursively()
                throw error
            }
        }

    suspend fun decodeForModel(photo: ReportPhoto, maxLongSide: Int): Bitmap =
        withContext(Dispatchers.IO) { decodeOrientedBitmap(File(photo.filePath), maxLongSide) }

    suspend fun decodePlateCrop(
        photo: ReportPhoto,
        bounds: NormalizedPhotoRect,
        maxLongSide: Int = PLATE_CROP_MAX_LONG_SIDE,
    ): Bitmap = plateCropDecodeMutex.withLock {
        decodeBitmapWithCancellationCleanup(Dispatchers.IO) {
            require(
                bounds.left in 0f..1f &&
                    bounds.top in 0f..1f &&
                    bounds.right in 0f..1f &&
                    bounds.bottom in 0f..1f &&
                    bounds.right > bounds.left &&
                    bounds.bottom > bounds.top
            ) { "Invalid normalized plate bounds" }
            val bitmap = decodeOrientedBitmap(File(photo.filePath), MODEL_EVIDENCE_MAX_LONG_SIDE)
            try {
                val plateWidth = (bounds.right - bounds.left) * bitmap.width
                val plateHeight = (bounds.bottom - bounds.top) * bitmap.height
                val left = (bounds.left * bitmap.width - plateWidth * PLATE_CROP_HORIZONTAL_PADDING)
                    .roundToInt()
                    .coerceIn(0, bitmap.width - 1)
                val top = (bounds.top * bitmap.height - plateHeight * PLATE_CROP_VERTICAL_PADDING)
                    .roundToInt()
                    .coerceIn(0, bitmap.height - 1)
                val right = (bounds.right * bitmap.width + plateWidth * PLATE_CROP_HORIZONTAL_PADDING)
                    .roundToInt()
                    .coerceIn(left + 1, bitmap.width)
                val bottom = (bounds.bottom * bitmap.height + plateHeight * PLATE_CROP_VERTICAL_PADDING)
                    .roundToInt()
                    .coerceIn(top + 1, bitmap.height)
                val candidate = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val crop = if (candidate === bitmap) {
                    requireNotNull(bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false))
                } else {
                    candidate
                }
                val longSide = maxOf(crop.width, crop.height)
                if (longSide <= maxLongSide) {
                    crop
                } else {
                    val scale = maxLongSide.toFloat() / longSide
                    crop.scale(
                        width = maxOf(1, (crop.width * scale).roundToInt()),
                        height = maxOf(1, (crop.height * scale).roundToInt()),
                    ).also { scaled ->
                        if (scaled !== crop) crop.recycle()
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun clearTemporaryCopies() = withContext(Dispatchers.IO) {
        deleteDirectory(File(context.cacheDir, "cloud"))
        deleteDirectory(File(context.cacheDir, "mail"))
    }

    suspend fun clearTemporaryCopies(reportId: String) = withContext(Dispatchers.IO) {
        deleteDirectory(File(context.cacheDir, "cloud/$reportId"))
        deleteDirectory(File(context.cacheDir, "mail/$reportId"))
    }

    private fun reportDirectory(reportId: String): File =
        File(reportsDirectory, reportId).ensureDirectory()

    private fun openPhotoInput(uri: Uri): InputStream = context.contentResolver.openInputStream(uri)
        ?: context.contentResolver.openFileDescriptor(uri, "r")?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
        ?: throw IllegalArgumentException("Unable to open selected photo")

    private fun deleteDirectory(directory: File) {
        check(!directory.exists() || directory.deleteRecursively()) { "Unable to delete temporary photo copies" }
    }

    private fun File.ensureDirectory(): File = apply {
        check(isDirectory || mkdirs() || isDirectory) { "Unable to create photo directory" }
    }

    private suspend fun prepareEmailCopy(photo: ReportPhoto, outputDirectory: File): File {
        val source = File(photo.filePath)
        if (source.length() <= MAIL_MAX_BYTES) {
            val bounds = imageBounds(source)
            if (maxOf(bounds.first, bounds.second) <= MAIL_MAX_LONG_SIDE) {
                val extension = source.extension.ifBlank { "jpg" }
                return source.copyTo(File(outputDirectory, "${photo.id}.$extension"), overwrite = true)
            }
        }

        val sourceIsJpeg = source.hasJpegSignature()
        val exifRead = if (sourceIsJpeg) {
            source.readJpegExifSegmentsUpTo(MAIL_MAX_RETAINED_EXIF_BYTES)
        } else {
            JpegExifReadResult(emptyList(), exceededLimit = false)
        }
        val preserveFullJpegExif = sourceIsJpeg && !exifRead.exceededLimit
        val output = File(outputDirectory, "${photo.id}.jpg")
        var bitmap = decodeBitmap(
            file = source,
            maxLongSide = MAIL_MAX_LONG_SIDE,
            applyExifOrientation = !preserveFullJpegExif,
        )
        try {
            while (true) {
                for (quality in MAIL_JPEG_QUALITIES) {
                    currentCoroutineContext().ensureActive()
                    writeMailCandidate(
                        source = source,
                        bitmap = bitmap,
                        quality = quality,
                        jpegExifSegments = exifRead.segments,
                        shouldCopyEssentialExif = !preserveFullJpegExif,
                        output = output,
                    )
                    if (output.length() <= MAIL_MAX_BYTES) return output
                }

                currentCoroutineContext().ensureActive()
                val currentLongSide = maxOf(bitmap.width, bitmap.height)
                check(currentLongSide > 1) { "Photo metadata exceeds the 2 MB mail attachment limit" }
                val targetLongSide = maxOf(
                    1,
                    minOf(currentLongSide - 1, (currentLongSide * MAIL_SCALE_STEP).roundToInt()),
                )
                val scale = targetLongSide.toFloat() / currentLongSide
                val resized = bitmap.scale(
                    width = maxOf(1, (bitmap.width * scale).roundToInt()),
                    height = maxOf(1, (bitmap.height * scale).roundToInt()),
                )
                if (resized !== bitmap) bitmap.recycle()
                bitmap = resized
            }
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeMailCandidate(
        source: File,
        bitmap: Bitmap,
        quality: Int,
        jpegExifSegments: List<ByteArray>,
        shouldCopyEssentialExif: Boolean,
        output: File,
    ) {
        val encoded = File(output.parentFile, "${output.name}.encoded")
        try {
            FileOutputStream(encoded).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    "Unable to encode mail attachment"
                }
            }
            FileInputStream(encoded).use { input ->
                FileOutputStream(output).use { stream ->
                    input.copyJpegWithExifSegments(stream, jpegExifSegments)
                }
            }
            if (shouldCopyEssentialExif) copyEssentialExif(source, output)
        } finally {
            encoded.delete()
        }
    }

    private fun copyEssentialExif(source: File, output: File) {
        val sourceExif = runCatching { ExifInterface(source) }.getOrNull() ?: return
        val outputExif = ExifInterface(output)
        ESSENTIAL_EXIF_TAGS.forEach { tag ->
            sourceExif.getAttribute(tag)?.let { outputExif.setAttribute(tag, it) }
        }
        sourceExif.latLong?.let { outputExif.setLatLong(it[0], it[1]) }
        sourceExif.getAltitude(Double.NaN).takeIf(Double::isFinite)?.let(outputExif::setAltitude)
        outputExif.setAttribute(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL.toString(),
        )
        outputExif.saveAttributes()
    }

    private fun readExif(file: File): PhotoExifMetadata = runCatching {
        val exif = ExifInterface(file)
        val coordinates = exif.latLong
        val capturedAtEpochMillis = readExifTimestamp(exif)
        PhotoExifMetadata(
            capturedAtEpochMillis = capturedAtEpochMillis,
            location = coordinates?.let {
                LocationSnapshot(
                    latitude = it[0],
                    longitude = it[1],
                    accuracyMeters = null,
                    capturedAtEpochMillis = capturedAtEpochMillis ?: System.currentTimeMillis(),
                )
            },
        )
    }.getOrDefault(PhotoExifMetadata())

    private fun readExifTimestamp(exif: ExifInterface): Long? {
        val rawDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val localDateTime = runCatching {
            LocalDateTime.parse(rawDateTime, EXIF_DATE_TIME_FORMATTER)
        }.getOrNull() ?: return null
        val rawOffset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
        val instant = rawOffset?.let { offset ->
            runCatching { localDateTime.toInstant(ZoneOffset.of(offset)) }.getOrNull()
        } ?: localDateTime.atZone(ZoneId.systemDefault()).toInstant()
        return instant.toEpochMilli()
    }

    private fun decodeOrientedBitmap(file: File, maxLongSide: Int): Bitmap =
        decodeBitmap(file, maxLongSide, applyExifOrientation = true)

    private fun decodeBitmap(file: File, maxLongSide: Int, applyExifOrientation: Boolean): Bitmap {
        val (width, height) = imageBounds(file)
        val sampleSize = calculateInSampleSize(width, height, maxLongSide)
        val decoded = requireNotNull(
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ),
        ) { "Unsupported image" }
        val scale = minOf(1f, maxLongSide.toFloat() / maxOf(decoded.width, decoded.height))
        val orientation = if (applyExifOrientation) runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL) else ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
            if (scale < 1f) postScale(scale, scale)
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun imageBounds(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        return bounds.outWidth to bounds.outHeight
    }

    companion object {
        const val CLOUD_MAX_BYTES = 1L * 1024L * 1024L
        const val MAIL_MAX_BYTES = 2L * 1024L * 1024L
        const val MAIL_MAX_LONG_SIDE = 2560
        const val MAX_IMPORTED_BYTES = 50L * 1024L * 1024L
        private const val CLOUD_MAX_LONG_SIDE = 2048
        private const val MODEL_EVIDENCE_MAX_LONG_SIDE = 2048
        private const val PLATE_CROP_MAX_LONG_SIDE = 720
        private const val PLATE_CROP_HORIZONTAL_PADDING = 0.12f
        private const val PLATE_CROP_VERTICAL_PADDING = 0.35f
        private const val CLOUD_MIN_LONG_SIDE = 640
        private const val CLOUD_START_QUALITY = 90
        private const val CLOUD_MIN_QUALITY = 34
        private const val CLOUD_QUALITY_STEP = 8
        private const val CLOUD_SCALE_STEP = 0.8f
        private const val MAIL_SCALE_STEP = 0.9f
        private const val MAIL_MAX_RETAINED_EXIF_BYTES = 256L * 1024L
        private val MAIL_JPEG_QUALITIES = intArrayOf(92, 89, 86, 85)
        private val ESSENTIAL_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
        )
        private val EXIF_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}

private fun File.hasJpegSignature(): Boolean = FileInputStream(this).use { input ->
    input.read() == JPEG_MARKER_PREFIX && input.read() == JPEG_START_OF_IMAGE
}

private fun File.readJpegExifSegmentsUpTo(maxTotalBytes: Long): JpegExifReadResult =
    FileInputStream(this).use { input ->
        input.readJpegExifSegmentsUpTo(maxTotalBytes)
    }

internal data class JpegExifReadResult(
    val segments: List<ByteArray>,
    val exceededLimit: Boolean,
)

internal fun InputStream.readJpegExifSegments(): List<ByteArray> =
    readJpegExifSegmentsUpTo(Long.MAX_VALUE).segments

internal fun InputStream.readJpegExifSegmentsUpTo(maxTotalBytes: Long): JpegExifReadResult {
    require(maxTotalBytes > 0)
    if (read() != JPEG_MARKER_PREFIX || read() != JPEG_START_OF_IMAGE) {
        return JpegExifReadResult(emptyList(), exceededLimit = false)
    }
    val segments = mutableListOf<ByteArray>()
    var totalBytes = 0L
    fun result() = JpegExifReadResult(segments, exceededLimit = false)
    while (true) {
        var prefix = read()
        while (prefix >= 0 && prefix != JPEG_MARKER_PREFIX) prefix = read()
        if (prefix < 0) return result()

        var marker = read()
        while (marker == JPEG_MARKER_PREFIX) marker = read()
        if (marker < 0 || marker == JPEG_START_OF_SCAN || marker == JPEG_END_OF_IMAGE) return result()
        if (marker == JPEG_TEMP || marker in JPEG_RESTART_MARKERS) continue

        val lengthHigh = read()
        val lengthLow = read()
        if (lengthHigh < 0 || lengthLow < 0) return result()
        val length = (lengthHigh shl 8) or lengthLow
        if (length < 2) return result()
        val payload = ByteArray(length - 2)
        var offset = 0
        while (offset < payload.size) {
            val count = read(payload, offset, payload.size - offset)
            if (count < 0) return result()
            offset += count
        }
        if (marker == JPEG_APP1 && payload.startsWith(EXIF_SIGNATURE)) {
            val segmentSize = payload.size + 4L
            if (totalBytes + segmentSize >= maxTotalBytes) {
                return JpegExifReadResult(emptyList(), exceededLimit = true)
            }
            totalBytes += segmentSize
            segments += ByteArray(payload.size + 4).apply {
                this[0] = JPEG_MARKER_PREFIX.toByte()
                this[1] = marker.toByte()
                this[2] = lengthHigh.toByte()
                this[3] = lengthLow.toByte()
                payload.copyInto(this, destinationOffset = 4)
            }
        }
    }
}

internal fun InputStream.copyJpegWithExifSegments(
    output: OutputStream,
    segments: List<ByteArray>,
) {
    val prefix = read()
    val marker = read()
    require(prefix == JPEG_MARKER_PREFIX && marker == JPEG_START_OF_IMAGE) {
        "Encoded mail attachment is not JPEG"
    }
    output.write(prefix)
    output.write(marker)
    segments.forEach(output::write)
    copyTo(output)
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

internal fun InputStream.copyToWithLimit(output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0L
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Photo import was cancelled")
        val read = read(buffer)
        if (read < 0) break
        totalBytes += read
        require(totalBytes <= maxBytes) { "Selected photo is larger than 50 MB" }
        output.write(buffer, 0, read)
    }
    return totalBytes
}

internal fun calculateInSampleSize(width: Int, height: Int, maxLongSide: Int): Int {
    require(width > 0 && height > 0 && maxLongSide > 0)
    var sampleSize = 1
    while (maxOf(width / (sampleSize * 2), height / (sampleSize * 2)) >= maxLongSide) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val JPEG_MARKER_PREFIX = 0xff
private const val JPEG_START_OF_IMAGE = 0xd8
private const val JPEG_END_OF_IMAGE = 0xd9
private const val JPEG_START_OF_SCAN = 0xda
private const val JPEG_APP1 = 0xe1
private const val JPEG_TEMP = 0x01
private val JPEG_RESTART_MARKERS = 0xd0..0xd7
private val EXIF_SIGNATURE = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)
