package com.cahyo.batur.ui.agent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan perintah untuk Batur")

private fun canRecognizeSpeech(context: Context): Boolean =
    SpeechRecognizer.isRecognitionAvailable(context) &&
        context.packageManager.queryIntentActivities(speechIntent(), 0).isNotEmpty()

/**
 * Input suara lewat pengenal suara bawaan ponsel (dialog Google/OEM), jadi aplikasi ini tidak
 * butuh izin RECORD_AUDIO. Hasilnya diserahkan ke [onResult] tanpa langsung dikirim.
 * @return aksi untuk memulai, atau null kalau ponsel tidak punya pengenal suara.
 */
@Composable
fun rememberVoiceInput(onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val available = remember { canRecognizeSpeech(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
            ?.let(onResult)
    }
    if (!available) return null
    return {
        try {
            launcher.launch(speechIntent())
        } catch (_: ActivityNotFoundException) {
            // Pengenal suara dihapus setelah layar dibuka; tombol cukup tidak bereaksi.
        }
    }
}
