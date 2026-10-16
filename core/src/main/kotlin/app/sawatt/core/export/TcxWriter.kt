package app.sawatt.core.export

import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Garmin Training Center (TCX) export, accepted by Strava, Garmin Connect, intervals.icu and
 * TrainingPeaks. Each interval becomes a lap with its own averages; every second becomes a
 * trackpoint with heart rate, cadence, power and virtual speed.
 *
 * Only riding time is recorded, so pauses do not appear in the timeline.
 */
object TcxWriter {
    fun write(record: WorkoutRecord, samples: WorkoutSamples): String {
        require(samples.powerW.isNotEmpty()) { "Cannot export a workout without samples" }
        val start = Instant.ofEpochSecond(record.startEpochMs / 1000)
        val distance = samples.cumulativeDistanceM()
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" """ +
                """xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xsi:schemaLocation="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 """ +
                """http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd">""",
        ).append('\n')
        sb.append("<Activities>\n<Activity Sport=\"Biking\">\n")
        sb.append("<Id>").append(start).append("</Id>\n")

        for (lap in lapsOf(record, samples)) {
            val s = lap.stats
            val end = minOf(lap.startSec + lap.durationSec, samples.powerW.size)
            val lapStartDistance = if (lap.startSec == 0) 0.0 else distance[lap.startSec - 1]
            val lapDistance = distance[end - 1] - lapStartDistance
            sb.append("<Lap StartTime=\"").append(start.plusSeconds(lap.startSec.toLong())).append("\">\n")
            sb.append("<TotalTimeSeconds>").append(lap.durationSec).append("</TotalTimeSeconds>\n")
            sb.append("<DistanceMeters>").append(num(lapDistance)).append("</DistanceMeters>\n")
            sb.append("<Calories>").append(s.kcal.roundToInt()).append("</Calories>\n")
            s.avgHeartRate?.let { sb.append("<AverageHeartRateBpm><Value>").append(it.roundToInt()).append("</Value></AverageHeartRateBpm>\n") }
            s.maxHeartRate?.let { sb.append("<MaximumHeartRateBpm><Value>").append(it).append("</Value></MaximumHeartRateBpm>\n") }
            sb.append("<Intensity>Active</Intensity>\n")
            sb.append("<Cadence>").append(s.avgCadenceRpm.roundToInt().coerceIn(0, 254)).append("</Cadence>\n")
            sb.append("<TriggerMethod>Manual</TriggerMethod>\n")
            sb.append("<Track>\n")
            for (i in lap.startSec until end) {
                sb.append("<Trackpoint>")
                sb.append("<Time>").append(start.plusSeconds(i.toLong())).append("</Time>")
                sb.append("<DistanceMeters>").append(num(distance[i])).append("</DistanceMeters>")
                val hr = samples.heartRate.getOrElse(i) { 0 }
                if (hr > 0) sb.append("<HeartRateBpm><Value>").append(hr).append("</Value></HeartRateBpm>")
                sb.append("<Cadence>").append(samples.cadenceRpm.getOrElse(i) { 0 }.coerceIn(0, 254)).append("</Cadence>")
                sb.append("<Extensions><ns3:TPX>")
                sb.append("<ns3:Speed>").append(num(samples.speedMs(i))).append("</ns3:Speed>")
                sb.append("<ns3:Watts>").append(samples.powerW[i]).append("</ns3:Watts>")
                sb.append("</ns3:TPX></Extensions>")
                sb.append("</Trackpoint>\n")
            }
            sb.append("</Track>\n")
            sb.append("<Notes>").append(escape(lap.name)).append("</Notes>\n")
            sb.append("<Extensions><ns3:LX>")
            sb.append("<ns3:AvgSpeed>").append(num(if (lap.durationSec > 0) lapDistance / lap.durationSec else 0.0)).append("</ns3:AvgSpeed>")
            sb.append("<ns3:MaxBikeCadence>").append(s.maxCadenceRpm.coerceIn(0, 254)).append("</ns3:MaxBikeCadence>")
            sb.append("<ns3:AvgWatts>").append(s.avgPowerW.roundToInt()).append("</ns3:AvgWatts>")
            sb.append("<ns3:MaxWatts>").append(s.maxPowerW).append("</ns3:MaxWatts>")
            sb.append("</ns3:LX></Extensions>\n")
            sb.append("</Lap>\n")
        }

        sb.append("<Creator xsi:type=\"Device_t\"><Name>SaWatt</Name><UnitId>0</UnitId><ProductID>0</ProductID>")
        sb.append("<Version><VersionMajor>0</VersionMajor><VersionMinor>1</VersionMinor></Version></Creator>\n")
        sb.append("</Activity>\n</Activities>\n</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun num(v: Double) = String.format(Locale.ROOT, "%.2f", v)

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
