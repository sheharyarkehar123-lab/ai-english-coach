package com.example.aienglishcoach

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GrammarCorrectionServiceTest {
    private val service = RuleBasedGrammarCorrectionService()

    @Test fun durationAndContinuingWork() = runTest {
        val result = service.correct("I am working here since five years.")
        assertEquals("I have been working here for five years.", result.corrected)
        assertEquals(1, result.explanations.size)
        assertEquals("Rule-based feedback (offline)", result.source)
    }

    @Test fun feedbackDependsOnInput() = runTest {
        val samples = mapOf(
            "They is ready." to "They are ready.",
            "She are happy." to "She is happy.",
            "He don't know." to "He does not know.",
            "We has tickets." to "We have tickets.",
            "She have a book." to "She has a book.",
            "I can to swim." to "I can swim.",
            "i is ready." to "I am ready."
        )
        for ((input, expected) in samples) {
            val result = service.correct(input)
            assertEquals(input, expected, result.corrected)
            assertTrue(result.explanations.isNotEmpty())
        }
    }

    @Test fun combinesRulesWithoutChangingUnrelatedText() = runTest {
        val result = service.correct("i has a book and they is ready!")
        assertEquals("I have a book and they are ready!", result.corrected)
        assertEquals(3, result.explanations.size)
    }

    @Test fun doesNotGuessForUnsupportedOrCorrectSentences() = runTest {
        for (input in listOf("I have worked here since 2020.", "They are ready.",
            "Yesterday I go to school.", "This island has a harbour.", "He has to go.")) {
            val result = service.correct(input)
            assertEquals(input, result.corrected)
            assertTrue(result.explanations.isEmpty())
        }
    }

    @Test fun trimsInputAndSupportsRepeatedChecks() = runTest {
        assertEquals("They are ready.", service.correct("  They is ready.  ").corrected)
        assertEquals("She is ready.", service.correct("She are ready.").corrected)
    }

    @Test fun rejectsEmptyAndOverlongInput() = runTest {
        for (input in listOf(" \n ", "a".repeat(MAX_PRACTICE_LENGTH + 1))) {
            try { service.correct(input); fail("Expected validation failure") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(MAX_PRACTICE_LENGTH, service.correct("a".repeat(MAX_PRACTICE_LENGTH)).corrected.length)
    }
}
