package com.example.aienglishcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TutorState { LISTENING, THINKING, SPEAKING }
enum class LearningMode { CONVERSATION, TEACHER, INTERVIEW }
data class CallUiState(
    val tutorState: TutorState = TutorState.LISTENING,
    val muted: Boolean = false, val cameraOn: Boolean = true, val captionsOn: Boolean = true,
    val mode: LearningMode = LearningMode.CONVERSATION, val caption: String = "Tell me about your day.",
    val practiceInput: String = "", val feedback: GrammarFeedback? = null,
    val checking: Boolean = false, val practiceError: String? = null,
    val connected: Boolean = true
)
class CallViewModel(
    private val correctionService: GrammarCorrectionService = RuleBasedGrammarCorrectionService()
) : ViewModel() {
    private val _state = MutableStateFlow(CallUiState()); val state: StateFlow<CallUiState> = _state.asStateFlow()
    fun toggleMute() { _state.update { it.copy(muted = !it.muted) } }
    fun toggleCamera() { _state.update { it.copy(cameraOn = !it.cameraOn) } }
    fun toggleCaptions() { _state.update { it.copy(captionsOn = !it.captionsOn) } }
    fun updatePracticeInput(input: String) {
        if (_state.value.checking) return
        _state.update { it.copy(practiceInput = input, feedback = null, practiceError = null) }
    }
    fun submitPractice() {
        val current = _state.value
        if (current.checking) return
        val input = current.practiceInput.trim()
        if (input.isBlank() || current.practiceInput.length > MAX_PRACTICE_LENGTH) {
            _state.update { it.copy(feedback = null, practiceError =
                if (input.isBlank()) "Enter an English sentence first."
                else "Use at most $MAX_PRACTICE_LENGTH characters.") }
            return
        }
        _state.update { it.copy(checking = true, feedback = null, practiceError = null) }
        viewModelScope.launch {
            try {
                val feedback = correctionService.correct(input)
                _state.update { it.copy(feedback = feedback) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(practiceError = "Could not check your sentence. Please try again.") }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { CallScreen() } } }
}
@Composable fun CallScreen(vm: CallViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Friendly Teacher", fontWeight = FontWeight.Bold); Text(s.mode.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall) }
                AssistChip(onClick = {}, label = { Text(if (s.connected) "Live" else "Reconnecting...") })
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.heightIn(min = 300.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, modifier = Modifier.size(150.dp), color = MaterialTheme.colorScheme.primary) { Box(contentAlignment = Alignment.Center) { Text("AI", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary) } }
                    Spacer(Modifier.height(20.dp)); Text("Tutor is ${s.tutorState.name.lowercase()}", fontWeight = FontWeight.SemiBold)
                    if (s.captionsOn) { Spacer(Modifier.height(12.dp)); Text(s.caption, modifier = Modifier.padding(horizontal = 24.dp)) }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(16.dp).size(width = 96.dp, height = 132.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) { Box(contentAlignment = Alignment.Center) { Text(if (s.cameraOn) "You" else "Camera off") } }
            }
            Spacer(Modifier.height(14.dp))
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Typed English practice", fontWeight = FontWeight.Bold)
                    Text("Offline rules check common patterns; they do not check every grammar error.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = s.practiceInput, onValueChange = vm::updatePracticeInput,
                        label = { Text("Your English sentence") },
                        placeholder = { Text("Type a sentence to practice") },
                        enabled = !s.checking, isError = s.practiceError != null,
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                        supportingText = { Text(s.practiceError ?: "${s.practiceInput.length}/$MAX_PRACTICE_LENGTH characters") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.submitPractice() })
                    )
                    Button(onClick = vm::submitPractice, enabled = !s.checking) {
                        Text(if (s.checking) "Checking…" else "Check sentence")
                    }
                    s.feedback?.let { feedback ->
                        Text(feedback.source, fontWeight = FontWeight.Bold)
                        Text("You typed: ${feedback.original}")
                        if (feedback.explanations.isEmpty()) {
                            Text("No supported rule matched. Your sentence may still contain errors.")
                            Text("Sentence: ${feedback.corrected}")
                        } else {
                            Text("Suggested correction: ${feedback.corrected}", fontWeight = FontWeight.SemiBold)
                            feedback.explanations.forEach { Text("• $it") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::toggleMute) { Text(if (s.muted) "Unmute" else "Mic") }
                FilledTonalButton(onClick = vm::toggleCamera) { Text(if (s.cameraOn) "Camera" else "Camera off") }
                FilledTonalButton(onClick = vm::toggleCaptions) { Text("CC") }
                Button(onClick = {}) { Text("End") }
            }
        }
    }
}
