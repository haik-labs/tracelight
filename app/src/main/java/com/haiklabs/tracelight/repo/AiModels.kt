package com.haiklabs.tracelight.repo

import kotlinx.serialization.Serializable

@Serializable
enum class SearchStatus { CANDIDATES_FOUND, NEEDS_MORE_INFO, NO_RELIABLE_MATCH }

@Serializable
enum class MatchLevel { OFFICIAL, STRONG, PARTIAL, UNCONFIRMED }

@Serializable
enum class TimelineType { ROLE, EDUCATION }

@Serializable
enum class Corroboration { MULTIPLE_SOURCES, SINGLE_SOURCE }

@Serializable
enum class SweepStatus { FOUND, NOT_FOUND, UNKNOWN }

/** Outcome of one per-platform profile search. Runs client-side, one grounded call per network. */
@Serializable
data class PlatformCheck(
    val platform: String = "",
    val status: SweepStatus = SweepStatus.NOT_FOUND,
    val url: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val audience: String = "",
    val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED,
    val matchReason: String = "",
    /** Raw search-result title and snippet, kept so the report writer sees every fact Google showed. */
    val snippet: String = ""
)

@Serializable
data class PublicProfileMatch(
    val platform: String = "",
    val url: String,
    val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED
)

@Serializable
data class PersonCandidate(
    val id: String,
    val name: String,
    val role: String = "",
    val company: String = "",
    val location: String = "",
    val publicProfiles: List<PublicProfileMatch> = emptyList(),
    /** Supplied clues confirmed by a public source. */
    val matchingClues: List<String> = emptyList(),
    /** Supplied clues that could not be confirmed or contradicted publicly. */
    val unverifiedClues: List<String> = emptyList()
)

@Serializable
data class CandidateSearchResponse(
    val status: SearchStatus,
    val message: String = "",
    val requestedClues: List<String> = emptyList(),
    val candidates: List<PersonCandidate> = emptyList()
)

@Serializable
data class ReportPerson(
    val name: String,
    val role: String = "",
    val company: String = "",
    val generalLocation: String = "",
    val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED
)

@Serializable
data class EvidenceItem(
    val label: String,
    val detail: String = "",
    val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED,
    val sourceIds: List<String> = emptyList()
)

@Serializable
data class SocialProfile(
    val platform: String = "",
    val url: String,
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val audience: String = "",
    val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED,
    val matchReason: String = "",
    val sourceIds: List<String> = emptyList()
)

@Serializable
data class TimelineItem(
    val title: String,
    val organization: String,
    val start: String = "",
    val end: String = "",
    val location: String = "",
    val type: TimelineType = TimelineType.ROLE,
    val sourceIds: List<String> = emptyList()
)

@Serializable
data class BusinessContact(
    val type: String,
    val label: String = "",
    val value: String,
    val url: String = "",
    val sourceIds: List<String> = emptyList()
)

@Serializable
data class ArticleMention(
    val title: String,
    val url: String,
    val publisher: String = "",
    val publishedDate: String = "",
    val summary: String = "",
    val sourceIds: List<String> = emptyList()
)

@Serializable
data class ReportSource(
    val id: String,
    val title: String,
    val url: String,
    val publisher: String = "",
    val supports: List<String> = emptyList(),
    val corroboration: Corroboration = Corroboration.SINGLE_SOURCE,
    /** Filled in by the app, not the model. */
    val retrievedDate: String = ""
)

@Serializable
data class DeepSearchReport(
    val person: ReportPerson,
    val summary: String = "",
    val identityEvidence: List<EvidenceItem> = emptyList(),
    val socialProfiles: List<SocialProfile> = emptyList(),
    val platformSweep: List<PlatformCheck> = emptyList(),
    val professionalTimeline: List<TimelineItem> = emptyList(),
    val businessContacts: List<BusinessContact> = emptyList(),
    val articlesAndMentions: List<ArticleMention> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val sources: List<ReportSource> = emptyList(),
    /** Filled in by the app, not the model. */
    val reportId: String = "",
    /** Filled in by the app, not the model. */
    val generatedDate: String = ""
) {
    val verifiedFactCount: Int
        get() = identityEvidence.count { it.matchLevel == MatchLevel.STRONG || it.matchLevel == MatchLevel.OFFICIAL }
}
