package app.sawatt.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.data.ExportFormat
import app.sawatt.data.WorkoutExporter
import kotlinx.coroutines.launch

/** Top bar action: export the workout as FIT or TCX and open the Android share sheet. */
@Composable
fun ExportButton(exporter: WorkoutExporter, record: WorkoutRecord) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    fun export(format: ExportFormat) {
        menuOpen = false
        scope.launch {
            val uri = exporter.export(record, format)
            if (uri == null) {
                Toast.makeText(context, "No recorded data to export", Toast.LENGTH_SHORT).show()
            } else {
                context.startActivity(exporter.shareIntent(uri, format))
            }
        }
    }

    Box {
        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Share, "Export") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Export FIT (Strava, Garmin Connect…)") },
                onClick = { export(ExportFormat.FIT) },
            )
            DropdownMenuItem(
                text = { Text("Export TCX") },
                onClick = { export(ExportFormat.TCX) },
            )
        }
    }
}
