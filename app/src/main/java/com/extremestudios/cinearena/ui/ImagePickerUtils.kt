package com.extremestudios.cinearena.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Base64OutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SelectedImagePayload(
    val base64: String,
    val fileName: String,
    val mimeType: String,
    val previewBitmap: Bitmap
)

data class SelectedFilePayload(
    val base64: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

suspend fun readSelectedImage(
    context: Context,
    uri: Uri
): Result<SelectedImagePayload> = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.ifBlank { null } ?: "image/jpeg"
        val fileName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }?.ifBlank { null } ?: defaultFileNameForMimeType(mimeType)

        val previewBitmap = decodeSampledBitmapFromUri(context, uri, MAX_IMAGE_EDGE, MAX_IMAGE_EDGE)
            ?: throw IllegalStateException("Preview gambar gagal dibuat.")
        val processedImage = prepareImagePayload(
            originalMimeType = mimeType,
            originalFileName = fileName,
            previewBitmap = previewBitmap
        )

        SelectedImagePayload(
            base64 = Base64.encodeToString(processedImage.bytes, Base64.NO_WRAP),
            fileName = processedImage.fileName,
            mimeType = processedImage.mimeType,
            previewBitmap = processedImage.previewBitmap
        )
    }.recoverCatching { error ->
        throw IllegalStateException(error.message ?: "Gagal memproses gambar.")
    }
}

private fun defaultFileNameForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
    "image/png" -> "image.png"
    "image/webp" -> "image.webp"
    else -> "image.jpg"
}

private data class ProcessedImagePayload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    val previewBitmap: Bitmap
)

private fun prepareImagePayload(
    originalMimeType: String,
    originalFileName: String,
    previewBitmap: Bitmap
): ProcessedImagePayload {
    val boundedBitmap = scaleBitmapIfNeeded(previewBitmap)
    val shouldPreservePng = originalMimeType.equals("image/png", ignoreCase = true) && boundedBitmap.hasAlpha()
    val outputMimeType = if (shouldPreservePng) "image/png" else "image/jpeg"
    val outputBytes = ByteArrayOutputStream().use { stream ->
        val format = if (shouldPreservePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (shouldPreservePng) 100 else 84
        boundedBitmap.compress(format, quality, stream)
        stream.toByteArray()
    }
    if (outputBytes.size > MAX_IMAGE_UPLOAD_BYTES) {
        throw IllegalStateException("Ukuran cover terlalu besar. Pilih gambar yang lebih kecil.")
    }
    val outputFileName = ensureImageExtension(originalFileName, outputMimeType)
    return ProcessedImagePayload(
        bytes = outputBytes,
        fileName = outputFileName,
        mimeType = outputMimeType,
        previewBitmap = boundedBitmap
    )
}

private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= MAX_IMAGE_EDGE) return bitmap
    val ratio = MAX_IMAGE_EDGE.toFloat() / maxSide.toFloat()
    val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun decodeSampledBitmapFromUri(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, boundsOptions)
    } ?: return null
    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

    val sampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2

        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}

private fun ensureImageExtension(fileName: String, mimeType: String): String {
    val baseName = fileName.substringBeforeLast('.', fileName).ifBlank { "image" }
    val extension = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return "$baseName.$extension"
}

private const val MAX_IMAGE_EDGE = 1600
private const val MAX_IMAGE_UPLOAD_BYTES = 1_500_000
private const val DEFAULT_STREAM_BUFFER_SIZE = 16 * 1024

suspend fun readSelectedFile(
    context: Context,
    uri: Uri,
    fallbackFileName: String,
    fallbackMimeType: String,
    maxSizeBytes: Long? = null
): Result<SelectedFilePayload> = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val fileMeta = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                Pair(
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null,
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                )
            } else {
                null
            }
        }

        val resolverMimeType = resolver.getType(uri)?.ifBlank { null }
        val mimeType = when {
            fallbackMimeType.equals("audio/mpeg", ignoreCase = true) -> "audio/mpeg"
            resolverMimeType != null -> resolverMimeType
            else -> fallbackMimeType
        }
        val fileName = fileMeta?.first?.ifBlank { null } ?: fallbackFileName
        val declaredSize = fileMeta?.second ?: -1L
        if (maxSizeBytes != null && declaredSize > 0L && declaredSize > maxSizeBytes) {
            val maxMb = (maxSizeBytes / (1024 * 1024)).coerceAtLeast(1)
            throw IllegalStateException("Ukuran file maksimal ${maxMb}MB.")
        }
        val encodedFile = resolver.openInputStream(uri)?.use { input ->
            encodeStreamToBase64(input, maxSizeBytes)
        } ?: throw IllegalStateException("File tidak bisa dibaca.")
        val sizeBytes = if (declaredSize > 0L) declaredSize else encodedFile.sizeBytes

        if (maxSizeBytes != null && sizeBytes > maxSizeBytes) {
            val maxMb = (maxSizeBytes / (1024 * 1024)).coerceAtLeast(1)
            throw IllegalStateException("Ukuran file maksimal ${maxMb}MB.")
        }

        SelectedFilePayload(
            base64 = encodedFile.base64,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
    }.recoverCatching { error ->
        throw IllegalStateException(error.message ?: "Gagal memproses file.")
    }
}

private data class EncodedFile(
    val base64: String,
    val sizeBytes: Long
)

private fun encodeStreamToBase64(input: InputStream, maxSizeBytes: Long?): EncodedFile {
    val output = ByteArrayOutputStream()
    val base64Output = Base64OutputStream(output, Base64.NO_WRAP)
    val buffer = ByteArray(DEFAULT_STREAM_BUFFER_SIZE)
    var totalBytes = 0L

    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        totalBytes += read
        if (maxSizeBytes != null && totalBytes > maxSizeBytes) {
            val maxMb = (maxSizeBytes / (1024 * 1024)).coerceAtLeast(1)
            throw IllegalStateException("Ukuran file maksimal ${maxMb}MB.")
        }
        base64Output.write(buffer, 0, read)
    }

    base64Output.flush()
    base64Output.close()
    return EncodedFile(
        base64 = output.toString(Charsets.UTF_8.name()),
        sizeBytes = totalBytes
    )
}
