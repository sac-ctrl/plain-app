package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.GraphQLError
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.preferences.SecurityAnswerPreference
import com.ismartcoding.plain.preferences.SecurityQuestionPreference

data class SecurityQA(
    val question: String,
    val hasAnswer: Boolean,
)

private fun normalize(s: String): String =
    s.trim().lowercase().replace(Regex("\\s+"), " ")

fun SchemaBuilder.addSecurityQASchema() {

    type<SecurityQA> {}

    /**
     * Returns the current feedback-gate question as plain text, plus a flag
     * indicating whether an answer has been set. The actual answer is never
     * exposed over the network.
     */
    query("securityQA") {
        resolver { ->
            val ctx = MainApp.instance
            SecurityQA(
                question = SecurityQuestionPreference.getAsync(ctx),
                hasAnswer = SecurityAnswerPreference.getAsync(ctx).isNotBlank(),
            )
        }
    }

    /**
     * Verifies a candidate answer against the stored one (case-insensitive,
     * whitespace-normalised). Returns true on match.
     */
    mutation("verifySecurityAnswer") {
        resolver { answer: String ->
            val ctx = MainApp.instance
            val expected = SecurityAnswerPreference.getAsync(ctx)
            normalize(answer) == normalize(expected)
        }
    }

    /**
     * Updates both the question and the answer used by the in-app feedback
     * gate. Requires the caller to provide the current answer first; without
     * it the update is rejected so a hijacked session cannot silently rotate
     * the credentials.
     *
     * Pass an empty `newQuestion` to keep the existing question.
     */
    mutation("updateSecurityQA") {
        resolver { currentAnswer: String, newQuestion: String, newAnswer: String ->
            val ctx = MainApp.instance
            val expected = SecurityAnswerPreference.getAsync(ctx)
            if (normalize(currentAnswer) != normalize(expected)) {
                throw GraphQLError("Current answer is incorrect")
            }
            val a = newAnswer.trim()
            if (a.isEmpty()) throw GraphQLError("New answer cannot be empty")
            val q = newQuestion.trim().ifEmpty { SecurityQuestionPreference.getAsync(ctx) }
            SecurityQuestionPreference.putAsync(ctx, q)
            SecurityAnswerPreference.putAsync(ctx, a)
            true
        }
    }
}
