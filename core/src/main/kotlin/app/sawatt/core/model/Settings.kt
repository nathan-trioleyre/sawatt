package app.sawatt.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

@Serializable
enum class Gender { MALE, FEMALE }

@Serializable
enum class UnitSystem { METRIC, IMPERIAL }

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/**
 * Profil du cycliste. Valeurs par défaut : homme, né le 1980-01-01, 75 kg, 182 cm,
 * niveau d'entraînement 0, FTP et FC max estimées.
 */
@Serializable
data class UserProfile(
    val name: String = "",
    val gender: Gender = Gender.MALE,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate = LocalDate.of(1980, 1, 1),
    val weightKg: Double = 75.0,
    val heightCm: Double = 182.0,
    /** 0: 1-3 h, 1: 3-5 h, 2: 5-8 h, 3: 8+ h of cardio per week. */
    val trainingLevel: Int = 0,
    /** 0 means "estimate from age". */
    val maxHr: Int = 0,
    /** 0 means "estimate from profile". */
    val ftpW: Int = 0,
    val units: UnitSystem = UnitSystem.METRIC,
)

@Serializable
data class AppSettings(
    /** Génère de fausses données vélo + cardio (mode démo). */
    val simulateBike: Boolean = false,
    val transitionBeep: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Adresse de la ceinture cardio à reconnecter automatiquement après un scan. */
    val defaultHrSensor: String? = null,
    /** Address of the last bike used, auto-selected after a scan. */
    val defaultBike: String? = null,
)
