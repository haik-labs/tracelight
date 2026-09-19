package com.haiklabs.tracelight

import com.haiklabs.tracelight.repo.CandidateSearchResponse
import com.haiklabs.tracelight.repo.MatchLevel
import com.haiklabs.tracelight.repo.SearchStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun parsesNeedsMoreInfoResponse() {
        val result = json.decodeFromString<CandidateSearchResponse>(
            """{"status":"NEEDS_MORE_INFO","message":"Several people have this name.","requestedClues":["COUNTRY_OR_CITY"],"candidates":[]}"""
        )
        assertEquals(SearchStatus.NEEDS_MORE_INFO, result.status)
        assertEquals(listOf("COUNTRY_OR_CITY"), result.requestedClues)
    }

    @Test fun parsesCandidateAndProfileConfidence() {
        val result = json.decodeFromString<CandidateSearchResponse>(
            """{"status":"CANDIDATES_FOUND","message":"Found","candidates":[{"id":"a1","name":"Anna Williams","publicProfiles":[{"platform":"LinkedIn","url":"https://example.com","matchLevel":"STRONG"}],"matchingClues":["City"]}]}"""
        )
        assertEquals(MatchLevel.STRONG, result.candidates.single().publicProfiles.single().matchLevel)
    }
}
