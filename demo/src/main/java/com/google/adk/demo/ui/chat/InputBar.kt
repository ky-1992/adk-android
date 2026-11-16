package com.google.adk.demo.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.adk.demo.ui.theme.CarMinTouchTargetDp
import com.google.adk.demo.ui.theme.LocalIsCarUi
import kotlinx.coroutines.delay

/** Recognition language for voice input. */
private const val VOICE_LANGUAGE = "zh-CN"

@Composable
fun InputBar(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    // Automotive context: larger paddings and >= 56dp touch targets on the
    // mic/send buttons (outlined text field is already >= 56dp tall by default).
    val isCarUi = LocalIsCarUi.current
    val context = LocalContext.current

    // --------------------------- Voice input -------------------------------
    var listening by remember { mutableStateOf(false) }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    // Text present in the field before the current recognition started; the
    // recognized fragment is appended to it (partial results update live).
    var baseText by remember { mutableStateOf("") }

    fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val rec = try {
                // Prefer the on-device recognizer (offline, lower latency).
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                voiceHint = "无法启动语音识别服务，请重试"
                return
            }
            recognizer = rec
            baseText = text
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        // Show intermediate results live in the input field.
                        text = joinVoice(baseText, partial)
                    }
                }

                override fun onResults(results: Bundle?) {
                    destroyRecognizer()
                    val heard = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (heard.isEmpty()) {
                        voiceHint = "未识别到语音内容，请重试"
                    } else {
                        // Fill the text field; the user confirms/edits before
                        // sending (automotive safety).
                        text = joinVoice(baseText, heard)
                        voiceHint = null
                    }
                }

                override fun onError(error: Int) {
                    destroyRecognizer()
                    voiceHint = when (error) {
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别超时，请重试"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误，无法完成识别"
                        SpeechRecognizer.ERROR_AUDIO -> "麦克风录音出错，请重试"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到说话声音"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音内容"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不足"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙，请稍后重试"
                        SpeechRecognizer.ERROR_SERVER -> "识别服务出错，请稍后重试"
                        else -> "语音识别失败（错误码 $error）"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, VOICE_LANGUAGE)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VOICE_LANGUAGE)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            rec.startListening(intent)
            listening = true
        } else {
            voiceHint = "此设备没有可用的语音识别服务"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            // Non-blocking friendly hint; the app keeps working.
            voiceHint = "未授予麦克风权限，语音输入不可用（可在系统设置中开启）"
        }
    }

    fun onMicClick() {
        if (listening) {
            recognizer?.stopListening() // final results arrive via onResults
            return
        }
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Release the recognizer when the input bar leaves composition.
    DisposableEffect(Unit) {
        onDispose { destroyRecognizer() }
    }

    // Non-blocking hint auto-clears after a few seconds.
    LaunchedEffect(voiceHint) {
        if (voiceHint != null) {
            delay(4000)
            voiceHint = null
        }
    }

    // Watchdog: if the recognition service never delivers a callback, the
    // listening flag would stick and keep the field disabled. Reset after a
    // generous timeout instead of leaving the bar stuck.
    LaunchedEffect(listening) {
        if (listening) {
            delay(15000)
            if (listening) {
                destroyRecognizer()
                voiceHint = "语音识别超时，请重试"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isCarUi) 24.dp else 8.dp,
                vertical = if (isCarUi) 12.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (isCarUi) (CarMinTouchTargetDp + 8).dp else Dp.Unspecified),
            placeholder = { Text("Type a message...") },
            enabled = enabled && !listening,
            singleLine = false,
            maxLines = 4,
        )
        Spacer(modifier = Modifier.width(if (isCarUi) 16.dp else 8.dp))
        IconButton(
            onClick = { onMicClick() },
            modifier = if (isCarUi) Modifier.size(CarMinTouchTargetDp.dp) else Modifier,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = if (listening) "Stop voice recognition" else "Voice input",
                // Recording state: tinted red so the driver sees it at a glance.
                tint = if (listening) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
        Spacer(modifier = Modifier.width(if (isCarUi) 8.dp else 4.dp))
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text.trim())
                    text = ""
                    voiceHint = null
                }
            },
            // While a recognition session is active, sending is disabled to
            // avoid fighting with partial results filling the field.
            enabled = enabled && !listening && text.isNotBlank(),
            modifier = if (isCarUi) Modifier.size(CarMinTouchTargetDp.dp) else Modifier,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }

    // Inline, non-blocking status line: listening indicator or voice hint.
    if (listening || voiceHint != null) {
        Text(
            text = if (listening) "● 正在聆听，请说话…" else (voiceHint ?: ""),
            modifier = Modifier.padding(
                start = if (isCarUi) 24.dp else 8.dp,
                bottom = if (isCarUi) 8.dp else 4.dp,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (listening) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}

/** Appends the recognized fragment to any text already in the input field. */
private fun joinVoice(base: String, voice: String): String = when {
    base.isBlank() -> voice
    voice.isBlank() -> base.trim()
    else -> "${base.trimEnd()} $voice"
}
