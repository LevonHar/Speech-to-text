package com.example.texttospeech

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SpeechToTextScreen()
            }
        }
    }
}

@Composable
fun SpeechToTextScreen() {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var spokenText by remember { mutableStateOf("") }
    var audioLevel by remember { mutableStateOf(0f) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // Add this at the top inside SpeechToTextScreen
    var isListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                audioLevel = rmsdB.coerceIn(0f, 10f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                audioLevel = 0f
                isListening = false
            }

            override fun onError(error: Int) {
                audioLevel = 0f
                isListening = false
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                spokenText = matches?.firstOrNull()?.capitalizeFirstLetter() ?: ""
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                spokenText = matches?.firstOrNull()?.capitalizeFirstLetter() ?: spokenText
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onDispose { speechRecognizer.destroy() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .padding(bottom = 100.dp), // leave space for bottom TextField
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "Speech to Text",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                AudioWaveAnimation(level = audioLevel)

                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                // Copy button
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = {
                            if (spokenText.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(spokenText))
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text("Copy Text 📋")
                    }
                }

                // Text field + Mic button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 24.dp),
                ) {
                    // Text field
                    OutlinedTextField(
                        value = spokenText,
                        onValueChange = { spokenText = it.capitalizeFirstLetter() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 60.dp),
                        placeholder = { Text("Your message will appear here...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(15.dp)
                    )

                    // Microphone button
                    IconButton(
                        onClick = {
                            if (hasPermission) {
                                spokenText = ""
                                speechRecognizer.startListening(speechIntent)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterEnd)
                            .background(
                                color = if (isListening) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.mic_icon),
                            contentDescription = "Start Speaking",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveAnimation(level: Float) {

    val barCount = 7
    val maxBarHeight = 60.dp
    val minBarHeight = 8.dp

    // Each bar has its own animated height for smooth bouncing effect
    val animatedHeights = remember {
        List(barCount) { Animatable(minBarHeight.value) }
    }

    // Launch animation when level changes
    LaunchedEffect(level) {
        animatedHeights.forEachIndexed { index, anim ->
            // Add slight random factor for each bar so all bars move
            val randomFactor = (0.7f..1.0f).random()
            val target = (minBarHeight.value + (maxBarHeight.value - minBarHeight.value) * level * randomFactor)
                .coerceIn(minBarHeight.value, maxBarHeight.value)

            anim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 150)
            )
        }
    }

    Row(
        modifier = Modifier.height(maxBarHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatedHeights.forEach { anim ->
            val barHeight = anim.value.dp
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(barHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(50) // Fully rounded ends
                    )
            )
        }
    }
}

// Helper extension for random float range
fun ClosedFloatingPointRange<Float>.random() = (start + Math.random() * (endInclusive - start)).toFloat()

fun String.capitalizeFirstLetter(): String {
    return if (this.isNotEmpty()) this[0].uppercaseChar() + this.substring(1) else this
}