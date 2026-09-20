package com.example.aienglishcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
data class Correction(val original: String, val better: String, val why: String)
data class CallUiState(
    val tutorState: TutorState = TutorState.LISTENING,
    val muted: Boolean = false, val cameraOn: Boolean = true, val captionsOn: Boolean = true,
    val mode: LearningMode = LearningMode.CONVERSATION, val caption: String = "Tell me about your day.",
    val correction: Correction? = Correction("I am working here since five years.", "I have been working here for five years.", "Use 'for' with a duration."),
    val connected: Boolean = true
)
class CallViewModel : ViewModel() {
    private val _state = MutableStateFlow(CallUiState()); val state: StateFlow<CallUiState> = _state.asStateFlow()
    fun toggleMute() { _state.value = _state.value.copy(muted = !_state.value.muted) }
    fun toggleCamera() { _state.value = _state.value.copy(cameraOn = !_state.value.cameraOn) }
    fun toggleCaptions() { _state.value = _state.value.copy(captionsOn = !_state.value.captionsOn) }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { CallScreen() } } }
}
@Composable fun CallScreen(vm: CallViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Friendly Teacher", fontWeight = FontWeight.Bold); Text(s.mode.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall) }
                AssistChip(onClick = {}, label = { Text(if (s.connected) "Live" else "Reconnecting...") })
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, modifier = Modifier.size(150.dp), color = MaterialTheme.colorScheme.primary) { Box(contentAlignment = Alignment.Center) { Text("AI", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary) } }
                    Spacer(Modifier.height(20.dp)); Text("Tutor is ${s.tutorState.name.lowercase()}", fontWeight = FontWeight.SemiBold)
                    if (s.captionsOn) { Spacer(Modifier.height(12.dp)); Text(s.caption, modifier = Modifier.padding(horizontal = 24.dp)) }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(16.dp).size(width = 96.dp, height = 132.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) { Box(contentAlignment = Alignment.Center) { Text(if (s.cameraOn) "You" else "Camera off") } }
            }
            s.correction?.let { c -> Spacer(Modifier.height(14.dp)); Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Text("Live correction", fontWeight = FontWeight.Bold); Text("You said: ${c.original}"); Text("Better: ${c.better}", fontWeight = FontWeight.SemiBold); Text("Why: ${c.why}") } } }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledTonalButton(onClick = vm::toggleMute) { Text(if (s.muted) "Unmute" else "Mic") }
                FilledTonalButton(onClick = vm::toggleCamera) { Text(if (s.cameraOn) "Camera" else "Camera off") }
                FilledTonalButton(onClick = vm::toggleCaptions) { Text("CC") }
                Button(onClick = {}) { Text("End") }
            }
        }
    }
}
