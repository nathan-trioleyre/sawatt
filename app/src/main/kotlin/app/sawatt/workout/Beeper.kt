package app.sawatt.workout

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/** Transition beeps, played on the media stream so they mix with music. */
class Beeper {
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: RuntimeException) {
        Log.w("Beeper", "No tone generator", e)
        null
    }

    /** New interval. */
    fun transition() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
    }

    /** Last planned block done. */
    fun planCompleted() {
        tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
    }
}
