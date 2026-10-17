package app.sawatt.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.sawatt.R
import app.sawatt.core.workout.IntervalKind

private val lapName = Regex("""(Lap|Free ride) (\d+)""")

/**
 * Display name of an interval. Core names unplanned laps "Lap 3" or "Free ride 3" in English, and
 * history keeps those names, so they are translated here; planned blocks keep the user's name.
 */
fun intervalName(context: Context, name: String, kind: IntervalKind): String {
    val match = lapName.matchEntire(name)
    if (kind != IntervalKind.LAP || match == null) return name
    val (prefix, number) = match.destructured
    return context.getString(if (prefix == "Lap") R.string.interval_lap else R.string.interval_free_ride, number.toInt())
}

@Composable
fun intervalName(name: String, kind: IntervalKind): String = intervalName(LocalContext.current, name, kind)
