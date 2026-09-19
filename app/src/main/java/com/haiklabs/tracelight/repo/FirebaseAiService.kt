package com.haiklabs.tracelight.repo

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig
import com.haiklabs.tracelight.SearchProfile
import kotlinx.serialization.json.Json

/**
 * The only client-to-AI boundary. Candidate discovery is intentionally compact; the
 * expensive deep search runs only after the user confirms a candidate and unlocks it.
 */
class FirebaseAiService(
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
) {
    // Keep the Vertex AI backend configured by the existing Firebase integration.
    // Candidate and report models extend that same client with separate schemas and limits.
    private val ai = Firebase.ai(backend = GenerativeBackend.vertexAI(location = "global"))

    private val candidateModel = ai.generativeModel(
        modelName = "gemini-3.8-flash",
        tools = listOf(Tool.googleSearch()),
        generationConfig = generationConfig {
            temperature = 0.1f
            maxOutputTokens = 2_500
            responseMimeType = "application/json"
            responseSchema = candidateSchema
        }
    )

    private val reportModel = ai.generativeModel(
        modelName = "gemini-3.8-flash",
        tools = listOf(Tool.googleSearch()),
        generationConfig = generationConfig {
            temperature = 0.1f
            maxOutputTokens = 12_000
            responseMimeType = "application/json"
            responseSchema = reportSchema
        }
    )

    suspend fun findCandidates(profile: SearchProfile): CandidateSearchResponse {
        val response = candidateModel.generateContent(candidatePrompt(profile)).text
            ?: error("Firebase AI returned no candidate data.")
        return json.decodeFromString<CandidateSearchResponse>(response)
            .sanitizedCandidates()
    }

    suspend fun createDeepReport(profile: SearchProfile, candidate: PersonCandidate): DeepSearchReport {
        val response = reportModel.generateContent(reportPrompt(profile, candidate)).text
            ?: error("Firebase AI returned no report data.")
        return json.decodeFromString<DeepSearchReport>(response).sanitizedReport()
    }

    private fun candidatePrompt(profile: SearchProfile) = """
        Find possible matches for a person using Google Search and return only the requested JSON.
        Full name: ${profile.fullName.trim()}
        Country/region: ${profile.countryOrRegion.trim()}
        Strong clues: ${profile.strongClues().joinToString("; ")}
        Additional clues: ${profile.additionalClues().joinToString("; ").ifBlank { "None" }}

        This is a low-cost identification pass, not a biography. Return at most 5 candidates and at
        most 4 public profile URLs per candidate. Never infer private or sensitive attributes. Use
        NEEDS_MORE_INFO when several people remain plausible and requestedClues from:
        COUNTRY_OR_CITY, COMPANY_OR_PROFESSION, USERNAME_OR_PROFILE_URL, SCHOOL_OR_PROJECT.
        Use NO_RELIABLE_MATCH rather than guessing. Every URL must be a public result you found.
        IDs must be short stable strings. Match levels: OFFICIAL, STRONG, PARTIAL, UNCONFIRMED.
    """.trimIndent()

    private fun reportPrompt(profile: SearchProfile, candidate: PersonCandidate) = """
        Create a source-grounded public professional-footprint report for the CONFIRMED candidate.
        Confirmed candidate: ${json.encodeToString(PersonCandidate.serializer(), candidate)}
        Original country/region: ${profile.countryOrRegion}
        User-provided clues: ${(profile.strongClues() + profile.additionalClues()).joinToString("; ")}

        Search broadly but return concise, deduplicated facts. Cover identity, public social profiles,
        current and previous professional roles, public education, personal websites, portfolios,
        projects, publications, news and public mentions, and contact details explicitly published for
        business inquiries. General location only. Never return home addresses, personal phone numbers,
        personal email addresses, relatives, leaked data, credentials, live location, or sensitive-trait
        inferences. Do not contact the person. Do not guess.

        Put every unique source once in sources and reference it elsewhere by sourceIds. Every material
        fact must have a sourceId. Mark unsupported, conflicting, stale, or ambiguous claims in
        uncertainties. Use an empty list rather than invented content. Keep summaries factual and brief;
        do not copy long passages. corroboration is "MULTIPLE_SOURCES" or "SINGLE_SOURCE". Timeline type
        is ROLE or EDUCATION. Business contacts must be explicitly professional. Match levels are
        OFFICIAL, STRONG, PARTIAL, UNCONFIRMED. Dates use readable ISO-like values when available.
    """.trimIndent()

    private fun CandidateSearchResponse.sanitizedCandidates() = copy(
        candidates = candidates.take(5).map { candidate ->
            candidate.copy(publicProfiles = candidate.publicProfiles.take(4))
        }
    )

    private fun DeepSearchReport.sanitizedReport(): DeepSearchReport {
        val safeContacts = businessContacts.filterNot {
            it.type.contains("phone", true) ||
                (it.type.contains("email", true) && !it.label.contains("business", true))
        }
        return copy(
            socialProfiles = socialProfiles.distinctBy { it.url }.take(20),
            professionalTimeline = professionalTimeline.take(24),
            businessContacts = safeContacts.take(8),
            articlesAndMentions = articlesAndMentions.distinctBy { it.url }.take(20),
            uncertainties = uncertainties.take(12),
            sources = sources.distinctBy { it.url }.take(40)
        )
    }

    private companion object {
        val profileSchema = Schema.obj(mapOf(
            "platform" to Schema.string(), "url" to Schema.string(),
            "matchLevel" to Schema.enumeration(listOf("OFFICIAL", "STRONG", "PARTIAL", "UNCONFIRMED"))
        ))
        val candidateSchema = Schema.obj(mapOf(
            "status" to Schema.enumeration(listOf("CANDIDATES_FOUND", "NEEDS_MORE_INFO", "NO_RELIABLE_MATCH")),
            "message" to Schema.string(),
            "requestedClues" to Schema.array(Schema.string()),
            "candidates" to Schema.array(Schema.obj(mapOf(
                "id" to Schema.string(), "name" to Schema.string(), "role" to Schema.string(),
                "company" to Schema.string(), "location" to Schema.string(), "photoUrl" to Schema.string(nullable = true),
                "publicProfiles" to Schema.array(profileSchema), "matchingClues" to Schema.array(Schema.string())
            ), optionalProperties = listOf("photoUrl")))
        ))

        private val evidenceSchema = Schema.obj(mapOf(
            "label" to Schema.string(), "detail" to Schema.string(),
            "matchLevel" to Schema.enumeration(listOf("OFFICIAL", "STRONG", "PARTIAL", "UNCONFIRMED")),
            "sourceIds" to Schema.array(Schema.string())
        ))
        private val socialSchema = Schema.obj(mapOf(
            "platform" to Schema.string(), "displayName" to Schema.string(), "username" to Schema.string(),
            "bio" to Schema.string(), "audience" to Schema.string(), "url" to Schema.string(),
            "matchLevel" to Schema.enumeration(listOf("OFFICIAL", "STRONG", "PARTIAL", "UNCONFIRMED")),
            "matchReason" to Schema.string(), "sourceIds" to Schema.array(Schema.string())
        ))
        private val timelineSchema = Schema.obj(mapOf(
            "start" to Schema.string(), "end" to Schema.string(), "title" to Schema.string(),
            "organization" to Schema.string(), "location" to Schema.string(),
            "type" to Schema.enumeration(listOf("ROLE", "EDUCATION")), "sourceIds" to Schema.array(Schema.string())
        ))
        private val contactSchema = Schema.obj(mapOf(
            "type" to Schema.string(), "label" to Schema.string(), "value" to Schema.string(),
            "url" to Schema.string(), "sourceIds" to Schema.array(Schema.string())
        ))
        private val articleSchema = Schema.obj(mapOf(
            "title" to Schema.string(), "publisher" to Schema.string(), "publishedDate" to Schema.string(),
            "summary" to Schema.string(), "url" to Schema.string(), "sourceIds" to Schema.array(Schema.string())
        ))
        private val sourceSchema = Schema.obj(mapOf(
            "id" to Schema.string(), "title" to Schema.string(), "publisher" to Schema.string(),
            "url" to Schema.string(), "retrievedDate" to Schema.string(),
            "supports" to Schema.array(Schema.string()),
            "corroboration" to Schema.enumeration(listOf("MULTIPLE_SOURCES", "SINGLE_SOURCE"))
        ))
        val reportSchema = Schema.obj(mapOf(
            "reportId" to Schema.string(), "generatedDate" to Schema.string(),
            "person" to Schema.obj(mapOf(
                "name" to Schema.string(), "role" to Schema.string(), "company" to Schema.string(),
                "generalLocation" to Schema.string(), "photoUrl" to Schema.string(nullable = true),
                "matchLevel" to Schema.enumeration(listOf("OFFICIAL", "STRONG", "PARTIAL", "UNCONFIRMED"))
            ), optionalProperties = listOf("photoUrl")),
            "summary" to Schema.string(), "identityEvidence" to Schema.array(evidenceSchema),
            "socialProfiles" to Schema.array(socialSchema), "professionalTimeline" to Schema.array(timelineSchema),
            "businessContacts" to Schema.array(contactSchema), "articlesAndMentions" to Schema.array(articleSchema),
            "uncertainties" to Schema.array(Schema.string()), "sources" to Schema.array(sourceSchema)
        ))
    }
}
