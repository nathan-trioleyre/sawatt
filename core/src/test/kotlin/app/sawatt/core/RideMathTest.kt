package app.sawatt.core

import app.sawatt.core.model.Gender
import app.sawatt.core.model.UserProfile
import app.sawatt.core.ride.RideMath
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Valeurs de référence calculées indépendamment (Node) à partir des mêmes formules. */
class RideMathTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun `speed`() {
        assertEquals(0.0, RideMath.speedKmh(0.0), 1e-6)
        assertEquals(26.028594, RideMath.speedKmh(100.0), 1e-5)
        assertEquals(37.363354, RideMath.speedKmh(250.0), 1e-5)
        assertEquals(44.466000, RideMath.speedKmh(400.0), 1e-5)
    }

    @Test
    fun `calories`() {
        assertEquals(154.35, RideMath.caloriesPerHour(75.0, 0.0), 1e-6)
        assertEquals(964.035, RideMath.caloriesPerHour(75.0, 250.0), 1e-6)
    }

    @Test
    fun `ftp estimate`() {
        assertEquals(175.555844, RideMath.estimatedFtp(UserProfile(), today), 1e-5)
        val female = UserProfile(
            gender = Gender.FEMALE, trainingLevel = 2, heightCm = 165.0, weightKg = 70.0,
            birthDate = LocalDate.of(1995, 6, 15),
        )
        assertEquals(177.393296, RideMath.estimatedFtp(female, today), 1e-5)
        assertEquals(230.0, RideMath.effectiveFtp(UserProfile(ftpW = 230), today), 0.0)
    }

    @Test
    fun `zones and gears`() {
        assertEquals(0, RideMath.powerZone(55.9))
        assertEquals(1, RideMath.powerZone(56.0))
        assertEquals(3, RideMath.powerZone(105.0))
        assertEquals(4, RideMath.powerZone(106.0))
        assertEquals("G1", RideMath.gearZone(10.0))
        assertEquals("G9", RideMath.gearZone(100.0))
        assertEquals("G12", RideMath.gearZone(160.0))
    }

    @Test
    fun `max heart rate`() {
        assertEquals(175.8, RideMath.effectiveMaxHr(UserProfile(), today), 1e-9) // age 46
    }
}
