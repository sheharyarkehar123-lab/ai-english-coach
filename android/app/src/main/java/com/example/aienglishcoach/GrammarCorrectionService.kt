package com.example.aienglishcoach

data class GrammarFeedback(
    val original: String,
    val corrected: String,
    val explanations: List<String>,
    val source: String = "Rule-based feedback (offline)"
)

/** Future implementations should call an authenticated backend; provider keys stay on the server. */
interface GrammarCorrectionService {
    suspend fun correct(sentence: String): GrammarFeedback
}

/** Deliberately narrow patterns: an unmatched sentence is not a guarantee of correct grammar. */
class RuleBasedGrammarCorrectionService : GrammarCorrectionService {
    override suspend fun correct(sentence: String): GrammarFeedback {
        require(sentence.isNotBlank()) { "Enter an English sentence first." }
        require(sentence.length <= MAX_PRACTICE_LENGTH) { "Use at most $MAX_PRACTICE_LENGTH characters." }
        val original = sentence.trim()
        var corrected = original
        val explanations = mutableListOf<String>()
        fun apply(pattern: String, why: String, replacement: (MatchResult) -> String) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(corrected)) {
                val updated = regex.replace(corrected, replacement)
                if (updated != corrected) {
                    corrected = updated
                    explanations += why
                }
            }
        }
        apply(
            "\\bI am working here since ((?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten) (?:years?|months?|weeks?|days?))\\b",
            "For work that started in the past and continues now, use 'have been working' and 'for' with a duration."
        ) { "I have been working here for ${it.groupValues[1]}" }
        apply("\\b(I) is\\b", "Use 'am' with 'I'.") { "I am" }
        apply("\\b(you|we|they) is\\b", "Use 'are' with 'you', 'we', and 'they'.") {
            "${it.groupValues[1]} are"
        }
        apply("\\b(he|she|it) are\\b", "Use 'is' with 'he', 'she', and 'it'.") {
            "${it.groupValues[1]} is"
        }
        apply("\\b(he|she|it) (don't|do not)\\b", "Use 'does not' with 'he', 'she', and 'it'.") {
            "${it.groupValues[1]} does not"
        }
        apply("\\b(I|you|we|they) has\\b", "Use 'have' with 'I', 'you', 'we', and 'they'.") {
            "${it.groupValues[1]} have"
        }
        apply("\\b(he|she|it) have\\b", "Use 'has' with 'he', 'she', and 'it'.") {
            "${it.groupValues[1]} has"
        }
        apply("\\b(can|could|should|must|will|would|may|might) to (?=[a-z])", "Use the base verb without 'to' after a modal verb.") {
            "${it.groupValues[1]} "
        }
        apply("\\bi\\b", "Capitalize the pronoun 'I'.") { "I" }
        return GrammarFeedback(original, corrected, explanations.distinct())
    }
}

const val MAX_PRACTICE_LENGTH = 500
