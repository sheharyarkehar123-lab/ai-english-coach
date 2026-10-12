package com.example.aienglishcoach

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {
    @Test fun practiceValidationFeedbackAndControls() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = CallViewModel()
            assertNull(vm.state.value.feedback)
            vm.submitPractice()
            assertNotNull(vm.state.value.practiceError)
            vm.updatePracticeInput("They is ready.")
            vm.submitPractice()
            vm.toggleCamera(); vm.toggleCaptions()
            advanceUntilIdle()
            assertEquals("They are ready.", vm.state.value.feedback?.corrected)
            assertTrue(vm.state.value.cameraOn)
            assertFalse(vm.state.value.captionsOn)
            assertFalse(vm.state.value.checking)
            vm.updatePracticeInput("She are ready.")
            assertNull(vm.state.value.feedback)
            vm.submitPractice(); advanceUntilIdle()
            assertEquals("She is ready.", vm.state.value.feedback?.corrected)
            assertEquals(2, vm.state.value.checkedCount)
            vm.updatePracticeInput("a".repeat(501)); vm.submitPractice()
            assertNotNull(vm.state.value.practiceError)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun speechModesAndSessionLifecycle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = CallViewModel()
            vm.selectMode(LearningMode.INTERVIEW)
            vm.acceptSpeech("They is ready.")
            advanceUntilIdle()
            assertEquals(LearningMode.INTERVIEW, vm.state.value.mode)
            assertEquals("They is ready.", vm.state.value.practiceInput)
            assertEquals("They are ready.", vm.state.value.feedback?.corrected)
            vm.endSession()
            assertTrue(vm.state.value.sessionEnded)
            vm.submitPractice()
            assertEquals(1, vm.state.value.checkedCount)
            vm.startNewSession()
            assertFalse(vm.state.value.sessionEnded)
            assertEquals(0, vm.state.value.checkedCount)
            assertEquals(LearningMode.INTERVIEW, vm.state.value.mode)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun handlesServiceFailureAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = CallViewModel(object : GrammarCorrectionService {
                override suspend fun correct(sentence: String): GrammarFeedback {
                    calls++
                    if (calls == 1) error("Unavailable")
                    return GrammarFeedback(sentence, sentence, emptyList(), "Test service")
                }
            })
            vm.updatePracticeInput("Hello."); vm.submitPractice(); advanceUntilIdle()
            assertNotNull(vm.state.value.practiceError)
            assertFalse(vm.state.value.checking)
            vm.submitPractice(); advanceUntilIdle()
            assertNull(vm.state.value.practiceError)
            assertEquals("Test service", vm.state.value.feedback?.source)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun preventsDuplicateSubmissionDuringSuspendedRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = CompletableDeferred<GrammarFeedback>()
            var calls = 0
            val vm = CallViewModel(object : GrammarCorrectionService {
                override suspend fun correct(sentence: String): GrammarFeedback {
                    calls++
                    return response.await()
                }
            })
            vm.updatePracticeInput("Hello."); vm.submitPractice(); runCurrent()
            vm.submitPractice(); vm.updatePracticeInput("Changed")
            assertEquals(1, calls)
            assertEquals("Hello.", vm.state.value.practiceInput)
            response.complete(GrammarFeedback("Hello.", "Hello.", emptyList()))
            advanceUntilIdle()
            assertFalse(vm.state.value.checking)
        } finally { Dispatchers.resetMain() }
    }
}
