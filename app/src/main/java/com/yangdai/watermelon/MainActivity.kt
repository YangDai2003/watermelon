package com.yangdai.watermelon

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.yangdai.watermelon.ui.theme.WatermelonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private var recorder: AudioRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatermelonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var isRecording by remember { mutableStateOf(false) }
                    var resultText by remember { mutableStateOf("未知") }
                    var frequency by remember { mutableStateOf(0f) }
                    var maxFrequency by remember { mutableStateOf(0f) }
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            modifier = Modifier.size(256.dp),
                            onClick = {
                                isRecording = !isRecording
                                if (isRecording) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        if (ActivityCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            // TODO: Consider calling
                                            //    ActivityCompat#requestPermissions
                                            // here to request the missing permissions, and then overriding
                                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                            //                                          int[] grantResults)
                                            // to handle the case where the user grants the permission. See the documentation
                                            // for ActivityCompat#requestPermissions for more details.
                                            return@launch
                                        }

                                        // 配置录音参数
                                        val audioSource = MediaRecorder.AudioSource.MIC
                                        val sampleRate = 44_100
                                        val channelConfig = AudioFormat.CHANNEL_IN_MONO
                                        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                                        val bufferSize = AudioRecord.getMinBufferSize(
                                            sampleRate,
                                            channelConfig,
                                            audioFormat
                                        )

                                        recorder = AudioRecord(
                                            audioSource,
                                            sampleRate,
                                            channelConfig,
                                            audioFormat,
                                            bufferSize
                                        )

                                        recorder?.startRecording()

                                        val buffer = ShortArray(bufferSize)
                                        while (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                            val readSize =
                                                recorder?.read(buffer, 0, buffer.size) ?: 0
                                            if (readSize > 0) {
                                                val decibels = calculateDecibels(buffer)
                                                if (decibels > 50) {
                                                    frequency = detectFrequency(buffer)
                                                    if (frequency > maxFrequency) {
                                                        maxFrequency = frequency
                                                    }
                                                    resultText = when {
                                                        maxFrequency in 133f..160f -> "成熟西瓜"
                                                        maxFrequency < 133f -> "过熟西瓜"
                                                        maxFrequency > 160f -> "半成熟西瓜"
                                                        else -> "未知"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    recorder?.stop()
                                    recorder?.release()
                                    recorder = null
                                    resultText = "未知"
                                    frequency = 0f
                                    maxFrequency = 0f
                                }
                            }) {
                            Icon(
                                modifier = Modifier.size(128.dp),
                                imageVector = if (!isRecording) Icons.Default.PlayCircleOutline else Icons.Default.PauseCircleOutline,
                                contentDescription = "Mic"
                            )
                        }
                        Text(text = resultText)
                        Text(text = "当前频率: $frequency Hz")

                        FeatureThatRequiresCameraPermission()
                    }
                }
            }
        }
    }
}

private fun calculateDecibels(buffer: ShortArray): Double {
    val sum = buffer.sumOf { it.toDouble() * it }
    val rms = sqrt(sum / buffer.size)
    return 20 * log10(rms)
}

// 使用 FFT 检测音频数据中的频率
private fun detectFrequency(buffer: ShortArray): Float {
    val n = buffer.size
    val real = DoubleArray(n)
    val imag = DoubleArray(n)

    // Convert buffer to double for FFT
    for (i in buffer.indices) {
        real[i] = buffer[i].toDouble()
        imag[i] = 0.0
    }

    // Perform FFT
    fft(real, imag)

    // Calculate magnitudes
    val magnitudes = DoubleArray(n / 2)
    for (i in 0 until n / 2) {
        magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
    }

    // Find the peak frequency
    val peakIndex = magnitudes.indices.maxByOrNull { magnitudes[it] } ?: 0
    val peakFrequency = peakIndex * 44_100 / n

    return peakFrequency.toFloat()
}

// FFT implementation (Cooley-Tukey algorithm)
private fun fft(real: DoubleArray, imag: DoubleArray) {
    val n = real.size
    if (n == 1) return

    val halfSize = n / 2
    val evenReal = DoubleArray(halfSize)
    val evenImag = DoubleArray(halfSize)
    val oddReal = DoubleArray(halfSize)
    val oddImag = DoubleArray(halfSize)

    for (i in 0 until halfSize) {
        evenReal[i] = real[2 * i]
        evenImag[i] = imag[2 * i]
        oddReal[i] = real[2 * i + 1]
        oddImag[i] = imag[2 * i + 1]
    }

    fft(evenReal, evenImag)
    fft(oddReal, oddImag)

    for (i in 0 until halfSize) {
        val angle = -2.0 * Math.PI * i / n
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        val tempReal = cos * oddReal[i] - sin * oddImag[i]
        val tempImag = sin * oddReal[i] + cos * oddImag[i]

        real[i] = evenReal[i] + tempReal
        imag[i] = evenImag[i] + tempImag
        real[i + halfSize] = evenReal[i] - tempReal
        imag[i + halfSize] = evenImag[i] - tempImag
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun FeatureThatRequiresCameraPermission() {

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )

    if (!cameraPermissionState.status.isGranted) {
        Column {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                // If the user has denied the permission but the rationale can be shown,
                // then gently explain why the app requires this permission
                stringResource(R.string.the_record_audio_is_important_for_this_app_please_grant_the_permission)
            } else {
                // If it's the first time the user lands on this feature, or the user
                // doesn't want to be asked again for this permission, explain that the
                // permission is required
                stringResource(R.string.record_audio_permission_required_for_this_feature_to_be_available_please_grant_the_permission)
            }
            Text(textToShow)
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text(stringResource(R.string.request_permission))
            }
        }
    }
}