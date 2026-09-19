package com.haiklabs.tracelight.repo

import com.haiklabs.tracelight.SearchProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a public-footprint scan for one person. */
data class FootprintReport(
    val fullName: String,
    val content: String
)

interface FootprintRepository {
    suspend fun scan(profile: SearchProfile): Result<FootprintReport>
}

/** Repository backed by [FirebaseAiService]. */
class FirebaseFootprintRepository(
    private val service: FirebaseAiService = FirebaseAiService()
) : FootprintRepository {

    override suspend fun scan(profile: SearchProfile): Result<FootprintReport> {
        val fullName = "${profile.firstName.trim()} ${profile.lastName.trim()}"
        return try {
            val content = withContext(Dispatchers.IO) {
                service.findPublicInformation(
                    name = fullName,
                    additionalInfo = "Born in ${profile.birthYear}"
                )
            }
            if (content.isBlank()) {
                Result.failure(IllegalStateException("No public information was returned."))
            } else {
                Result.success(FootprintReport(fullName = fullName, content = content.trim()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
