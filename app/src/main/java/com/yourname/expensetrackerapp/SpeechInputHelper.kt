package com.yourname.expensetrackerapp

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

/**
 * Builds the system speech-recognition intent used for voice transaction entry. Deliberately
 * uses [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] (the system's own dictation UI, e.g. the Google
 * app) rather than driving [android.speech.SpeechRecognizer] directly: it needs no RECORD_AUDIO
 * permission in this app at all (the recognizer activity owns the mic in its own process), and it
 * reuses whichever recognition engine the device/OEM already ships and has tuned — both more
 * reliable across devices and simpler than building a custom listener.
 */
object SpeechInputHelper {

    fun buildIntent(context: Context): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.prompt_voice_transaction))
        }
    }

    /** Requires the `<queries>` declaration in AndroidManifest.xml for API 30+ package-visibility
     *  rules — without it this can incorrectly report false even when a recognizer is installed. */
    fun isAvailable(context: Context): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    /** Pulls the top transcript out of a successful [RecognizerIntent.ACTION_RECOGNIZE_SPEECH]
     *  result. Returns null on cancel, empty results, or a blank transcript. */
    fun extractTranscript(resultCode: Int, data: Intent?): String? {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return null
        val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return results?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
