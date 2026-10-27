package io.github.tournierjc.mercury.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.OutputKind
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import java.io.File

/** A deliverable downloaded byte-exact from the daemon, waiting to be handed over. */
class SharedFile(val name: String, val bytes: ByteArray)

/**
 * What the preview dialog shows.
 *
 * Exactly one of [text] and [image] is set when the preview worked; [note] explains
 * the cases the phone cannot draw (a ZIP, a 12 MB photo) without pretending they
 * failed.
 */
data class OutputPreviewState(
    val output: DeliverableDto,
    val loading: Boolean = true,
    val text: String? = null,
    val truncated: Boolean = false,
    val image: ByteArray? = null,
    val note: String? = null,
)

/**
 * The three deliverable actions, shared by the bot sheet and the fleet inbox.
 *
 * Kept out of the view models so both screens behave identically, and out of the
 * composables so a preview is a suspend call, not a side effect of recomposition.
 */
class OutputActions(private val api: MercuryApi) {

    /**
     * Text is read through the preview route (64 kB max); a small image is downloaded
     * whole and decoded by the caller with `BitmapFactory` — no image library added.
     */
    suspend fun preview(output: DeliverableDto): OutputPreviewState = try {
        when {
            BotOps.previewable(output) -> {
                val preview = api.outputPreview(output.botId, output.name)
                OutputPreviewState(
                    output = output,
                    loading = false,
                    text = preview.preview,
                    truncated = preview.truncated,
                )
            }

            BotOps.inlineImage(output) -> OutputPreviewState(
                output = output,
                loading = false,
                image = api.downloadOutput(output.botId, output.name),
            )

            BotOps.kind(output.name) == OutputKind.Image ->
                note(output, "Image trop lourde pour un aperçu (${BotOps.sizeLabel(output.bytes)}) — partagez-la pour l'ouvrir.")

            else -> note(output, "Aperçu indisponible pour ce format — partagez le fichier pour l'ouvrir.")
        }
    } catch (e: Exception) {
        note(output, e.message ?: "Aperçu indisponible")
    }

    private fun note(output: DeliverableDto, message: String) =
        OutputPreviewState(output = output, loading = false, note = message)

    suspend fun download(output: DeliverableDto): SharedFile =
        SharedFile(output.name.substringAfterLast('/').ifBlank { "livrable" }, api.downloadOutput(output.botId, output.name))
}

/**
 * Hands a downloaded deliverable to the system share sheet.
 *
 * The app had no file handoff until now (attachments travel as base64 inside a turn).
 * Writing into the cache and exposing one URI through the manifest's FileProvider means
 * no storage permission and nothing else readable by the receiver.
 */
object OutputSharing {

    fun share(context: Context, file: SharedFile) {
        val directory = File(context.cacheDir, "deliverables").apply { mkdirs() }
        val target = File(directory, file.name)
        target.writeBytes(file.bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = BotOps.mimeType(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Partager « ${file.name} »")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }
}
