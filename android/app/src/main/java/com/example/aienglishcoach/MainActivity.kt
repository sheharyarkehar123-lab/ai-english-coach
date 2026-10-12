package com.example.aienglishcoach

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class TutorState { LISTENING, THINKING, SPEAKING }
enum class LearningMode(val label: String, val prompt: String) {
    CONVERSATION("Conversation", "Tell me about your day."),
    TEACHER("Teacher", "Write or say a sentence you want to improve."),
    INTERVIEW("Interview", "Tell me about your experience and strengths.")
}

data class CallUiState(
    val tutorState: TutorState = TutorState.LISTENING,
    val cameraOn: Boolean = false,
    val captionsOn: Boolean = true,
    val mode: LearningMode = LearningMode.CONVERSATION,
    val practiceInput: String = "",
    val feedback: GrammarFeedback? = null,
    val checking: Boolean = false,
    val practiceError: String? = null,
    val sessionEnded: Boolean = false,
    val checkedCount: Int = 0
)

class CallViewModel(private val correctionService: GrammarCorrectionService = RuleBasedGrammarCorrectionService()) : ViewModel() {
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    fun toggleCamera() = _state.update { it.copy(cameraOn = !it.cameraOn) }
    fun toggleCaptions() = _state.update { it.copy(captionsOn = !it.captionsOn) }
    fun selectMode(mode: LearningMode) = _state.update { it.copy(mode = mode, feedback = null, practiceError = null) }
    fun updatePracticeInput(input: String) {
        if (!_state.value.checking) _state.update { it.copy(practiceInput = input, feedback = null, practiceError = null) }
    }
    fun acceptSpeech(text: String) { updatePracticeInput(text); submitPractice() }
    fun showError(message: String) = _state.update { it.copy(practiceError = message) }
    fun endSession() = _state.update { it.copy(sessionEnded = true, cameraOn = false) }
    fun startNewSession() = _state.update { CallUiState(mode = it.mode, captionsOn = it.captionsOn) }

    fun submitPractice() {
        val current = _state.value
        if (current.checking || current.sessionEnded) return
        val input = current.practiceInput.trim()
        if (input.isBlank() || current.practiceInput.length > MAX_PRACTICE_LENGTH) {
            showError(if (input.isBlank()) "Enter or say an English sentence first." else "Use at most $MAX_PRACTICE_LENGTH characters.")
            return
        }
        _state.update { it.copy(checking = true, tutorState = TutorState.THINKING, feedback = null, practiceError = null) }
        viewModelScope.launch {
            try {
                val feedback = correctionService.correct(input)
                _state.update { it.copy(feedback = feedback, tutorState = TutorState.SPEAKING, checkedCount = it.checkedCount + 1) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(practiceError = "Could not check your sentence. Please try again.", tutorState = TutorState.LISTENING) }
            } finally { _state.update { it.copy(checking = false) } }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { CoachRoute() } } }
}

@Composable private fun CoachRoute(vm: CallViewModel = viewModel()) {
    val context = LocalContext.current
    var cameraImage by remember { mutableStateOf<Bitmap?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) engine.language = Locale.US
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) { cameraImage = bitmap; if (!vm.state.value.cameraOn) vm.toggleCamera() }
        else vm.showError("Camera capture was cancelled.")
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else vm.showError("Camera permission is needed to show your preview.")
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text.isNullOrBlank()) vm.showError("No speech was recognized. Please try again.") else vm.acceptSpeech(text)
        } else vm.showError("Speech input was cancelled.")
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeechRecognizer(speechLauncher::launch, vm::showError)
        else vm.showError("Microphone permission is needed for speech practice.")
    }
    CallScreen(
        vm, cameraImage,
        onCamera = {
            if (vm.state.value.cameraOn) { cameraImage = null; vm.toggleCamera() }
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(null)
            else cameraPermission.launch(Manifest.permission.CAMERA)
        },
        onSpeak = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchSpeechRecognizer(speechLauncher::launch, vm::showError)
            else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onPlayFeedback = {
            val feedback = vm.state.value.feedback
            if (feedback != null) {
                if (!ttsReady) vm.showError("Text-to-speech is not ready on this device.")
                else tts?.speak(feedback.spokenSummary(), TextToSpeech.QUEUE_FLUSH, null, "grammar-feedback")
            }
        }
    )
}

private fun launchSpeechRecognizer(launch: (Intent) -> Unit, onError: (String) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your English sentence")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    try { launch(intent) } catch (_: ActivityNotFoundException) { onError("Speech recognition is not available on this device.") }
}

private fun GrammarFeedback.spokenSummary(): String = if (explanations.isEmpty())
    "No supported rule matched. Your sentence is $corrected" else "Suggested correction: $corrected. ${explanations.joinToString(" ")}"

@Composable fun CallScreen(vm: CallViewModel, cameraImage: Bitmap?, onCamera: () -> Unit, onSpeak: () -> Unit, onPlayFeedback: () -> Unit) {
    val s by vm.state.collectAsState()
    if (s.sessionEnded) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Practice complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("You checked ${s.checkedCount} ${if (s.checkedCount == 1) "sentence" else "sentences"}.")
                Button(onClick = vm::startNewSession) { Text("Start new session") }
            } }
        }; return
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header(s.mode, vm::selectMode); Spacer(Modifier.height(16.dp)); TutorPanel(s, cameraImage); Spacer(Modifier.height(14.dp))
        PracticeCard(s, vm, onSpeak, onPlayFeedback); Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onSpeak, enabled = !s.checking) { Text("Speak") }
            FilledTonalButton(onClick = onCamera) { Text(if (s.cameraOn) "Camera off" else "Camera") }
            FilledTonalButton(onClick = vm::toggleCaptions) { Text(if (s.captionsOn) "CC on" else "CC off") }
            Button(onClick = vm::endSession, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("End") }
        }
    }
}

@Composable private fun Header(mode: LearningMode, onMode: (LearningMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("Friendly Teacher", fontWeight = FontWeight.Bold); Text("Offline English coach", style = MaterialTheme.typography.bodySmall) }
        Box { AssistChip(onClick = { expanded = true }, label = { Text(mode.label) }); DropdownMenu(expanded, { expanded = false }) {
            LearningMode.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, { onMode(item); expanded = false }) }
        } }
    }
}

@Composable private fun TutorPanel(s: CallUiState, cameraImage: Bitmap?) {
    Box(Modifier.heightIn(min = 280.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, modifier = Modifier.size(132.dp), color = MaterialTheme.colorScheme.primary) { Box(contentAlignment = Alignment.Center) { Text("AI", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary) } }
            Spacer(Modifier.height(16.dp)); Text("Tutor is ${s.tutorState.name.lowercase()}", fontWeight = FontWeight.SemiBold)
            if (s.captionsOn) { Spacer(Modifier.height(10.dp)); Text(s.mode.prompt, modifier = Modifier.padding(horizontal = 24.dp)) }
        }
        Surface(Modifier.align(Alignment.TopEnd).padding(14.dp).size(width = 96.dp, height = 128.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
            if (s.cameraOn && cameraImage != null) Image(cameraImage.asImageBitmap(), "Your camera preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(contentAlignment = Alignment.Center) { Text("Camera off", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun PracticeCard(s: CallUiState, vm: CallViewModel, onSpeak: () -> Unit, onPlay: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("English practice", fontWeight = FontWeight.Bold); Text("Type a sentence or use Speak. Feedback uses clearly labeled offline rules.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(s.practiceInput, vm::updatePracticeInput, label = { Text("Your English sentence") }, placeholder = { Text("Example: They is ready.") }, enabled = !s.checking, isError = s.practiceError != null, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            supportingText = { Text(s.practiceError ?: "${s.practiceInput.length}/$MAX_PRACTICE_LENGTH characters") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.submitPractice() }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(vm::submitPractice, enabled = !s.checking) { Text(if (s.checking) "Checking…" else "Check sentence") }; OutlinedButton(onSpeak, enabled = !s.checking) { Text("Speak") } }
        s.feedback?.let { feedback -> HorizontalDivider(); Text(feedback.source, fontWeight = FontWeight.Bold); Text("You entered: ${feedback.original}")
            if (feedback.explanations.isEmpty()) Text("No supported rule matched. Your sentence may still contain errors.") else { Text("Suggested correction: ${feedback.corrected}", fontWeight = FontWeight.SemiBold); feedback.explanations.forEach { Text("• $it") } }
            OutlinedButton(onPlay) { Text("Play feedback") }
        }
    } }
}
