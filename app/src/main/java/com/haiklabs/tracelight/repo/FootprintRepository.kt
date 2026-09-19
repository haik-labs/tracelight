package com.haiklabs.tracelight.repo

import com.haiklabs.tracelight.SearchProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FootprintRepository {
    suspend fun findCandidates(profile: SearchProfile): Result<CandidateSearchResponse>
    suspend fun createReport(profile: SearchProfile, candidate: PersonCandidate): Result<DeepSearchReport>
}

class FirebaseFootprintRepository(private val service: FirebaseAiService = FirebaseAiService()) : FootprintRepository {
    override suspend fun findCandidates(profile: SearchProfile) = safeCall { service.findCandidates(profile) }
    override suspend fun createReport(profile: SearchProfile, candidate: PersonCandidate) = safeCall {
        service.createDeepReport(profile, candidate)
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) { block() })
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
