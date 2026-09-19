package com.haiklabs.tracelight.repo

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool

class FirebaseAiService {

    private val model = Firebase
        .ai(backend = GenerativeBackend.vertexAI(location = "global"))
        .generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(
                Tool.googleSearch()
            )
        )

    suspend fun findPublicInformation(
        name: String,
        additionalInfo: String? = null
    ): String {

        val prompt = """
            Find publicly available information about this person.

            Name: $name
            Additional information: ${additionalInfo.orEmpty()}

            Use Google Search when necessary.

            Important rules:
            - Only use publicly available information.
            - Do not guess.
            - Make sure information belongs to the correct person.
            - If several people have the same name, explain the ambiguity.
            - Include sources for the information.
            - Prefer reliable sources.
        """.trimIndent()

        return model
            .generateContent(prompt)
            .text
            .orEmpty()
    }
}