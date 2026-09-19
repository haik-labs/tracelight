package com.haiklabs.tracelight.repo

import kotlinx.serialization.Serializable

@Serializable
enum class SearchStatus { CANDIDATES_FOUND, NEEDS_MORE_INFO, NO_RELIABLE_MATCH }

@Serializable
enum class MatchLevel { OFFICIAL, STRONG, PARTIAL, UNCONFIRMED }

@Serializable
data class PublicProfileMatch(val platform: String, val url: String, val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED)

@Serializable
data class PersonCandidate(
    val id: String,
    val name: String,
    val role: String = "",
    val company: String = "",
    val location: String = "",
    val photoUrl: String? = null,
    val publicProfiles: List<PublicProfileMatch> = emptyList(),
    val matchingClues: List<String> = emptyList()
)

@Serializable
data class CandidateSearchResponse(
    val status: SearchStatus,
    val message: String,
    val requestedClues: List<String> = emptyList(),
    val candidates: List<PersonCandidate> = emptyList()
)

@Serializable
data class ReportPerson(
    val name: String,
    val role: String = "",
    val company: String = "",
    val generalLocation: String = "",
    val photoUrl: String? = null,
    val matchLevel: MatchLevel = MatchLevel.UNCONFIRMED
)

@Serializable
data class EvidenceItem(val label: String, val detail: String, val matchLevel: MatchLevel, val sourceIds: List<String>)

@Serializable
data class SocialProfile(
    val platform: String,
    val displayName: String,
    val username: String = "",
    val bio: String = "",
    val audience: String = "",
    val url: String,
    val matchLevel: MatchLevel,
    val matchReason: String,
    val sourceIds: List<String>
)

@Serializable
data class TimelineItem(
    val start: String,
    val end: String = "",
    val title: String,
    val organization: String,
    val location: String = "",
    val type: String,
    val sourceIds: List<String>
)

@Serializable
data class BusinessContact(val type: String, val label: String, val value: String, val url: String, val sourceIds: List<String>)

@Serializable
data class ArticleMention(
    val title: String,
    val publisher: String,
    val publishedDate: String = "",
    val summary: String,
    val url: String,
    val sourceIds: List<String>
)

@Serializable
data class ReportSource(
    val id: String,
    val title: String,
    val publisher: String,
    val url: String,
    val retrievedDate: String,
    val supports: List<String>,
    val corroboration: String
)

@Serializable
data class DeepSearchReport(
    val reportId: String,
    val generatedDate: String,
    val person: ReportPerson,
    val summary: String,
    val identityEvidence: List<EvidenceItem>,
    val socialProfiles: List<SocialProfile>,
    val professionalTimeline: List<TimelineItem>,
    val businessContacts: List<BusinessContact>,
    val articlesAndMentions: List<ArticleMention>,
    val uncertainties: List<String>,
    val sources: List<ReportSource>
) {
    val verifiedFactCount: Int get() = identityEvidence.count { it.matchLevel == MatchLevel.STRONG || it.matchLevel == MatchLevel.OFFICIAL }
}
