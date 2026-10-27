package io.github.tournierjc.mercury.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.tournierjc.mercury.vm.AttachmentFile
import java.io.File

/**
 * Hands a downloaded board attachment to the system.
 *
 * The app keeps no copy of a card's files: they live on the daemon and are fetched when the
 * owner taps one. To be shown or saved, the bytes must become a real file behind a
 * `content://` URI the other app is granted to read — that is the `FileProvider` declared
 * for `cache/boards/`, and the file never leaves it.
 */
object AttachmentFiles {

    private const val IMAGE_MAX_DIMENSION = 1400
    private val UNSAFE_NAME = Regex("[^A-Za-z0-9._-]+")

    /** Writes the bytes into the app cache under a name no path can escape. */
    fun stage(context: Context, file: AttachmentFile): File {
        val directory = File(context.cacheDir, "boards").apply { mkdirs() }
        val safeName = file.name.replace(UNSAFE_NAME, "_").ifBlank { "piece-jointe" }
        return File(directory, safeName).apply { writeBytes(file.bytes) }
    }

    /**
     * Opens the file in whatever app reads that type. False means nothing on the phone claims
     * it — the caller tells the owner instead of failing silently.
     */
    fun open(context: Context, file: AttachmentFile): Boolean = runCatching {
        val uri = uriFor(context, stage(context, file))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, file.mimeType.ifBlank { "application/octet-stream" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // An application context cannot start an activity without a task of its own.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess

    /** Writes [bytes] to the location the owner picked in the system "save as" dialog. */
    fun save(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }.getOrDefault(false)

    /**
     * Decodes an image for the in-place preview. Sampled first: a full-size screenshot would
     * blow the heap before any downscale could happen.
     */
    fun decode(bytes: ByteArray, maxDimension: Int = IMAGE_MAX_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

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

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
