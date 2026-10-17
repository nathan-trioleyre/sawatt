package app.sawatt.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.sawatt.R
import app.sawatt.core.export.FitWriter
import app.sawatt.core.export.TcxWriter
import app.sawatt.core.workout.WorkoutRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    FIT("fit", "application/vnd.ant.fit", "FIT"),
    TCX("tcx", "application/vnd.garmin.tcx+xml", "TCX"),
}

/**
 * Writes a workout to a FIT or TCX file in the app cache and builds the Android share intent.
 * Files are exposed to other apps through the FileProvider declared in the manifest.
 */
class WorkoutExporter(private val context: Context, private val history: HistoryRepository) {

    /** Returns the exported file's content URI, or null when the workout has no recorded samples. */
    suspend fun export(record: WorkoutRecord, format: ExportFormat): Uri? {
        val samples = history.samples(record.id)?.takeIf { it.powerW.isNotEmpty() } ?: return null
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            // Only the latest export is kept: the share target copies what it needs.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName(record, format))
            when (format) {
                ExportFormat.FIT -> file.writeBytes(FitWriter.write(record, samples))
                ExportFormat.TCX -> file.writeText(TcxWriter.write(record, samples))
            }
            file
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun shareIntent(uri: Uri, format: ExportFormat): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(format.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, context.getString(R.string.export_chooser, format.label))
    }

    private fun fileName(record: WorkoutRecord, format: ExportFormat): String {
        val date = Instant.ofEpochMilli(record.startEpochMs).atZone(ZoneId.systemDefault()).format(FILE_DATE)
        return "sawatt-$date.${format.extension}"
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    }
}
