package io.github.tournierjc.mercury.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import io.github.tournierjc.mercury.data.PendingFile
import java.io.ByteArrayOutputStream

/**
 * Turns a picked document into bytes the API can take.
 *
 * A photo is downscaled and re-encoded: a 12 MP JPEG travels as 5-6 MB of base64
 * for pixels the model never needs, and the phone pays for that upload on mobile
 * data. A PDF is passed through untouched — it is already the thing the model reads.
 */
object MediaPicker {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80
    private const val PDF_LIMIT = 15 * 1024 * 1024

    /**
     * Reads one picked URI. The failure carries a French message ready for the UI.
     * `maxDimension` is 1600 for a chat photo and 512 for an avatar: a face in a roster
     * does not need more, and the server caps avatars at 2 MB anyway.
     */
    fun read(context: Context, uri: Uri, maxDimension: Int = MAX_DIMENSION): Result<PendingFile> = runCatching {
        val name = displayName(context, uri)
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Fichier illisible")

        if (name.endsWith(".pdf", true) || looksLikePdf(raw)) {
            require(raw.size <= PDF_LIMIT) { "$name dépasse 15 Mo" }
            return@runCatching PendingFile(name, raw)
        }

        val bitmap = decodeScaled(raw, maxDimension) ?: error("$name n'est pas une image lisible")
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            PendingFile(jpegName(name), out.toByteArray())
        } finally {
            bitmap.recycle()
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "piece-jointe"
    }

    /** `photo.PNG` → `photo.jpg`: the bytes are re-encoded, so the name follows. */
    private fun jpegName(name: String): String {
        val base = name.substringBeforeLast('.').ifBlank { "photo" }
        return "$base.jpg"
    }

    private fun looksLikePdf(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes.copyOfRange(0, 5).toString(Charsets.ISO_8859_1) == "%PDF-"

    /**
     * Decodes with `inSampleSize` first: a full-size 50 MP bitmap would blow the
     * heap before any downscale could happen.
     */
    private fun decodeScaled(bytes: ByteArray, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxDimension) return decoded

        val ratio = maxDimension.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
