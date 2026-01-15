package com.example.texttospeech

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.texttospeech.languages.LanguagePreferences
import com.example.texttospeech.languages.Languages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore("speech_history")

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechToTextScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var spokenText by remember { mutableStateOf("") }
    var audioLevel by remember { mutableStateOf(0f) }
    var isListening by remember { mutableStateOf(false) }

    // Speech history remembered in Compose
    val speechHistory = remember { mutableStateListOf<String>() }

    // Load persisted history on start
    LaunchedEffect(Unit) {
        val key = stringSetPreferencesKey("speech_history")
        val savedSet = context.dataStore.data.first()[key] ?: emptySet()
        speechHistory.addAll(savedSet.sortedByDescending { it }) // Latest first
    }

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
        ) { granted -> hasPermission = granted }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    var selectedLanguage by remember {
        mutableStateOf(
            Languages.languages.first { it.first == "English" }
        )
    }

    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // Load saved language
    LaunchedEffect(Unit) {
        LanguagePreferences.getSelectedLanguage(context).collect { savedLanguageName ->
            savedLanguageName?.let { name ->
                Languages.languages.find { it.first == name }?.let {
                    selectedLanguage = it
                }
            }
        }
    }

    // Recognition listener
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
                audioLevel = 0f; isListening = false
            }

            override fun onError(error: Int) {
                audioLevel = 0f; isListening = false
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val result = matches?.firstOrNull()?.capitalizeFirstLetter() ?: ""
                if (result.isNotBlank()) {
                    spokenText = result
                    speechHistory.add(0, result) // Add to top of history

                    // Persist history
                    scope.launch {
                        val key = stringSetPreferencesKey("speech_history")
                        context.dataStore.edit { prefs ->
                            prefs[key] = speechHistory.toSet()
                        }
                    }
                }
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                spokenText = matches?.firstOrNull()?.capitalizeFirstLetter() ?: spokenText
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer.destroy() }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val configuration = LocalContext.current.resources.configuration
    val screenWidth = configuration.screenWidthDp.dp

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(screenWidth * 0.8f)
            ) {
                // Drawer header with delete icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Speech History",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete_icon),
                            contentDescription = "Delete History"
                        )
                    }
                }

                Divider()

                if (speechHistory.isEmpty()) {
                    Text(
                        "No history yet",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        speechHistory.forEachIndexed { index, text ->
                            Text(
                                text,
                                fontSize = 15.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (index < speechHistory.size - 1) {
                                Divider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Confirmation dialog
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete All History") },
                    text = { Text("Are you sure you want to delete all speech history?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // Clear history from UI
                                speechHistory.clear()
                                // Clear history from DataStore
                                scope.launch {
                                    val key = stringSetPreferencesKey("speech_history")
                                    context.dataStore.edit { prefs ->
                                        prefs.remove(key)
                                    }
                                }
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("No")
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Speech To Text") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.menu_icon),
                                contentDescription = "Menu"
                            )
                        }
                    }
                )
            },
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Language Dropdown
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text("Language: ${selectedLanguage.first}")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 6 * 50.dp)
                            ) {
                                Languages.languages.forEach { (name, locale) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedLanguage = name to locale
                                            expanded = false
                                            scope.launch {
                                                LanguagePreferences.saveSelectedLanguage(
                                                    context,
                                                    name
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        AudioWaveAnimation(level = audioLevel)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 24.dp),
                        ) {
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

                            IconButton(
                                onClick = {
                                    if (hasPermission) {
                                        if (isListening) {
                                            speechRecognizer.stopListening()
                                            isListening = false
                                        } else {
                                            spokenText = ""
                                            speechIntent.putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE,
                                                selectedLanguage.second.toLanguageTag()
                                            )
                                            speechRecognizer.startListening(speechIntent)
                                            isListening = true
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .align(Alignment.CenterEnd)
                                    .background(
                                        color = if (isListening) MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.3f
                                        )
                                        else MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.mic_icon),
                                    contentDescription = if (isListening) "Stop Speaking" else "Start Speaking",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun AudioWaveAnimation(level: Float) {
    val barCount = 7
    val maxBarHeight = 60.dp
    val minBarHeight = 8.dp
    val animatedHeights = remember { List(barCount) { Animatable(minBarHeight.value) } }

    LaunchedEffect(level) {
        animatedHeights.forEachIndexed { index, anim ->
            val randomFactor = (0.7f..1.0f).random()
            val target =
                (minBarHeight.value + (maxBarHeight.value - minBarHeight.value) * level * randomFactor)
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
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

fun ClosedFloatingPointRange<Float>.random() =
    (start + Math.random() * (endInclusive - start)).toFloat()

fun String.capitalizeFirstLetter(): String =
    if (this.isNotEmpty()) this[0].uppercaseChar() + this.substring(1) else this
