package app.sawatt.core.ride

import app.sawatt.core.model.Gender
import app.sawatt.core.model.UserProfile
import java.time.LocalDate
import java.time.Period
import kotlin.math.cbrt
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Training formulas: virtual speed, energy, FTP / max heart rate estimates, zones. */
object RideMath {
    /**
     * Virtual speed in km/h for a given power, solving a cubic drag model:
     *   t = -P / 0.37;  v = -cbrt(t + sqrt(t² + 421.12)) + cbrt(-t + sqrt(t² + 421.12));  km/h = 3.6·v
     */
    fun speedKmh(powerW: Double): Double {
        val t = -powerW / 0.37
        val root = sqrt(t * t + 421.12)
        val v = -cbrt(t + root) + cbrt(-t + root)
        return 3.6 * v
    }

    /** Energy expenditure in kcal/h: 60 · (0.0343 · weightKg + 0.053979 · P). */
    fun caloriesPerHour(weightKg: Double, powerW: Double): Double =
        60.0 * (0.0343 * weightKg + 0.053979 * powerW)

    /** Age-predicted max heart rate (Tanaka-style), `maxBPM` filter: 208 - 0.7·age. */
    fun estimatedMaxHr(age: Int): Double = 208.0 - 0.7 * age

    fun age(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int =
        Period.between(birthDate, today).years

    /**
     * FTP estimate used when the user has not entered one: a W/kg factor
     * by training level and gender, applied to the lower of body weight and a height-derived
     * "lean" weight, then reduced by 0.5 % per year above 35.
     */
    fun estimatedFtp(user: UserProfile, today: LocalDate = LocalDate.now()): Double {
        val level = user.trainingLevel.coerceIn(0, 3)
        val factors = arrayOf(
            doubleArrayOf(2.505652, 2.081739),
            doubleArrayOf(3.076957, 2.585217),
            doubleArrayOf(3.648261, 3.088696),
            doubleArrayOf(4.219565, 3.592174),
        )
        val inches = (user.heightCm - 152.4) / 2.54
        val (factor, leanWeight) = if (user.gender == Gender.MALE) {
            factors[level][0] to 52 + inches * 1.9
        } else {
            factors[level][1] to 49 + inches * 1.7
        }
        var ftp = factor * minOf(user.weightKg, leanWeight)
        val age = age(user.birthDate, today)
        if (age >= 35) ftp *= (100 - 0.5 * (age - 35)) / 100
        return ftp
    }

    fun effectiveFtp(user: UserProfile, today: LocalDate = LocalDate.now()): Double =
        if (user.ftpW > 0) user.ftpW.toDouble() else estimatedFtp(user, today)

    fun effectiveMaxHr(user: UserProfile, today: LocalDate = LocalDate.now()): Double =
        if (user.maxHr > 0) user.maxHr.toDouble() else estimatedMaxHr(age(user.birthDate, today))

    /** Power zone 0..4 from % of FTP: <56, <76, <91, <106, ≥106. */
    fun powerZone(percentFtp: Double): Int = when {
        percentFtp < 56 -> 0
        percentFtp < 76 -> 1
        percentFtp < 91 -> 2
        percentFtp < 106 -> 3
        else -> 4
    }

    /** "Gear" label G1..G12 from % of FTP. */
    fun gearZone(percentFtp: Double): String {
        val p = percentFtp.roundToInt()
        val limits = intArrayOf(26, 56, 62, 69, 76, 83, 91, 98, 106, 121, 151)
        val idx = limits.indexOfFirst { p < it }.let { if (it == -1) limits.size else it }
        return "G${idx + 1}"
    }

    const val KM_PER_MILE = 1.609344
}
