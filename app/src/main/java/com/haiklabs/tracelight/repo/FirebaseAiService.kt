package com.haiklabs.tracelight.repo

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.haiklabs.tracelight.SearchProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Thrown when the model responds but the response cannot be used. Message is user-facing. */
class AiResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The only client-to-AI boundary. Candidate discovery is intentionally compact; the
 * expensive deep search runs only after the user confirms a candidate and unlocks it.
 *
 * Static rules live in each model's system instruction; the per-request prompt carries
 * only the person's details. Gemini 3.x always thinks, and thinking tokens count against
 * maxOutputTokens, so the caps leave headroom above the expected JSON size.
 */
class FirebaseAiService(
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
) {
    private val ai = Firebase.ai(backend = GenerativeBackend.vertexAI(location = "global"))

    private val candidateModel = ai.generativeModel(
        modelName = CANDIDATE_MODEL,
        tools = listOf(Tool.googleSearch()),
        systemInstruction = content { text(CANDIDATE_RULES) },
        generationConfig = generationConfig {
            temperature = 0.1f
            maxOutputTokens = 8_000
            responseMimeType = "application/json"
            responseSchema = candidateSchema
            thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.LOW }
        }
    )

    /** One tiny grounded call per network. Cheap, parallel, and it cannot skip a platform. */
    private val sweepModel = ai.generativeModel(
        modelName = SWEEP_MODEL,
        tools = listOf(Tool.googleSearch()),
        systemInstruction = content { text(SWEEP_RULES) },
        generationConfig = generationConfig {
            // Tiny JSON: a call that needs more than this has started repeating itself, so fail fast.
            temperature = 0.2f
            maxOutputTokens = 1_200
            responseMimeType = "application/json"
            responseSchema = sweepSchema
            thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.LOW }
        }
    )

    private val reportModel = ai.generativeModel(
        modelName = REPORT_MODEL,
        tools = listOf(Tool.googleSearch(), Tool.urlContext()),
        systemInstruction = content { text(REPORT_RULES) },
        generationConfig = generationConfig {
            temperature = 0.1f
            maxOutputTokens = 32_000
            responseMimeType = "application/json"
            responseSchema = reportSchema
            thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.MEDIUM }
        }
    )

    suspend fun findCandidates(profile: SearchProfile): CandidateSearchResponse {
        val text = candidateModel.generateJson("candidates", candidatePrompt(profile))
        return decode<CandidateSearchResponse>(text).sanitizedCandidates()
    }

    suspend fun createDeepReport(profile: SearchProfile, candidate: PersonCandidate): DeepSearchReport {
        val sweep = sweepPlatforms(profile, candidate)
        val text = reportModel.generateJson("report", reportPrompt(profile, candidate, sweep))
        return decode<DeepSearchReport>(text).mergeSweep(sweep).sanitizedReport()
    }

    /** Checks every network in [SWEEP_PLATFORMS] in parallel. A failed check is reported as UNKNOWN, never as NOT_FOUND. */
    private suspend fun sweepPlatforms(profile: SearchProfile, candidate: PersonCandidate): List<PlatformCheck> {
        val handles = knownHandles(profile, candidate)
        val context = listOf(candidate.role, candidate.company, candidate.location).filter(String::isNotBlank).joinToString(", ")
        val gate = Semaphore(SWEEP_CONCURRENCY)
        return coroutineScope {
            SWEEP_PLATFORMS.map { platform ->
                async {
                    // A URL the user typed, or one the candidate step already found, is a known hit on
                    // this network. The sweep still runs to enrich it, but can no longer lose it.
                    val seeded = seededProfile(platform, profile, candidate)
                    gate.withPermit { try {
                        val text = sweepModel.generateJson("sweep:${platform.name}", sweepPrompt(platform, candidate.name, handles, context, seeded?.url))
                        val check = decode<PlatformCheck>(text).copy(platform = platform.name)
                        when {
                            check.status == SweepStatus.FOUND && platform.owns(check.url) -> check
                            seeded != null -> {
                                Log.i(TAG, "sweep:${platform.name} fell back to the seeded url")
                                seeded.copy(snippet = check.snippet.ifBlank { seeded.snippet })
                            }
                            check.status == SweepStatus.FOUND -> {
                                Log.w(TAG, "sweep:${platform.name} returned off-platform url ${check.url}")
                                check.copy(status = SweepStatus.NOT_FOUND, url = "")
                            }
                            else -> check
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "sweep:${platform.name} failed: ${e.message?.take(120)}")
                        seeded ?: PlatformCheck(platform = platform.name, status = SweepStatus.UNKNOWN)
                    } }
                }
            }.awaitAll()
        }
    }

    private fun seededProfile(platform: SweepPlatform, profile: SearchProfile, candidate: PersonCandidate): PlatformCheck? {
        val userUrl = profile.publicProfileUrl.trim()
        if (userUrl.isNotEmpty() && platform.owns(userUrl)) {
            return PlatformCheck(
                platform = platform.name, status = SweepStatus.FOUND, url = userUrl,
                username = handleFromUrl(userUrl).orEmpty(), matchLevel = MatchLevel.STRONG,
                matchReason = "Profile URL supplied by the user"
            )
        }
        val fromCandidate = candidate.publicProfiles.firstOrNull { platform.owns(it.url) } ?: return null
        return PlatformCheck(
            platform = platform.name, status = SweepStatus.FOUND, url = fromCandidate.url,
            username = handleFromUrl(fromCandidate.url).orEmpty(), matchLevel = fromCandidate.matchLevel,
            matchReason = "Found during candidate identification"
        )
    }

    private fun sweepPrompt(platform: SweepPlatform, name: String, handles: List<String>, context: String, knownUrl: String?): String {
        val queries = buildList {
            if (knownUrl != null) add("\"$knownUrl\"")
            add("$name ${platform.keywords}")
            handles.forEach { add("$it ${platform.keywords}") }
            handles.forEach { add("\"${platform.profilePath}$it\"") }
        }.distinct().take(5)
        return """
            Network: ${platform.name} (accepted hosts: ${platform.hosts.joinToString(", ")})
            Person: $name${if (context.isNotBlank()) " ($context)" else ""}
            Known handles: ${handles.joinToString(", ").ifBlank { "none" }}
            ${if (knownUrl != null) "Known profile URL on this network: $knownUrl (treat a result for this URL as FOUND even if its snippet is short)" else ""}
            Searches to run, in order, stopping once a result on an accepted host clearly matches:
            ${queries.joinToString("\n            ") { "- $it" }}
        """.trimIndent()
    }

    private fun knownHandles(profile: SearchProfile, candidate: PersonCandidate): List<String> = buildList {
        profile.username.trim().removePrefix("@").takeIf(String::isNotEmpty)?.let(::add)
        candidate.publicProfiles.mapNotNullTo(this) { handleFromUrl(it.url) }
    }.distinct().take(3)

    private fun SweepPlatform.owns(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("m.").lowercase()
        return hosts.any { host == it || host.endsWith(".$it") }
    }

    // ---------------------------------------------------------------- prompts

    private fun candidatePrompt(profile: SearchProfile) = """
        Identify plausible public candidates for this person.

        Full name: ${profile.fullName.trim()}
        Country/region: ${profile.countryOrRegion.trim()}
        Strong clues: ${profile.strongClues().joinToString("; ").ifBlank { "None" }}
        Additional clues: ${profile.additionalClues().joinToString("; ").ifBlank { "None" }}
    """.trimIndent()

    private fun reportPrompt(profile: SearchProfile, candidate: PersonCandidate, sweep: List<PlatformCheck>): String {
        val found = sweep.filter { it.status == SweepStatus.FOUND && it.url.isNotBlank() }
        val verified = found.joinToString("\n") { check ->
            buildString {
                append("- ${check.platform}: ${check.url} (${check.matchLevel}")
                if (check.matchReason.isNotBlank()) append("; ${check.matchReason}")
                append(")")
                if (check.displayName.isNotBlank()) append("\n  display name: ${check.displayName}")
                if (check.username.isNotBlank()) append("\n  handle: ${check.username}")
                if (check.bio.isNotBlank()) append("\n  headline/bio: ${check.bio}")
                if (check.audience.isNotBlank()) append("\n  audience: ${check.audience}")
                if (check.snippet.isNotBlank()) append("\n  search snippet: ${check.snippet.take(500)}")
            }
        }.ifBlank { "- None" }
        val knownUrls = (found.map { it.url } + candidate.publicProfiles.map { it.url }).filter(String::isNotBlank).distinct().take(5)
        return """
            Build the public professional-footprint report for this confirmed candidate.

            Confirmed candidate: ${json.encodeToString(PersonCandidate.serializer(), candidate)}
            Country/region supplied by the user: ${profile.countryOrRegion.trim()}
            Clues supplied by the user: ${(profile.strongClues() + profile.additionalClues()).joinToString("; ").ifBlank { "None" }}

            Profiles already verified by a per-network search. Include every one of them in socialProfiles,
            and treat the headline and snippet lines below as facts from that page (cite the profile as the
            source). In particular, a LinkedIn headline such as "Android Developer at Acme" must set
            person.role and person.company and produce a professionalTimeline entry, even if the page itself
            cannot be opened:
            $verified

            Profile URLs to open with the URL context tool: ${knownUrls.joinToString(", ").ifBlank { "None" }}
        """.trimIndent()
    }

    /** The client-side sweep is authoritative: its results replace the model's list and fill any profile it missed. */
    private fun DeepSearchReport.mergeSweep(sweep: List<PlatformCheck>): DeepSearchReport {
        val known = socialProfiles.map { it.url.trim().trimEnd('/').lowercase() }.toSet()
        val missing = sweep.filter { it.status == SweepStatus.FOUND && it.url.isNotBlank() }
            .filterNot { it.url.trim().trimEnd('/').lowercase() in known }
            .map {
                SocialProfile(
                    platform = it.platform, url = it.url, displayName = it.displayName, username = it.username,
                    bio = it.bio, audience = it.audience, matchLevel = it.matchLevel, matchReason = it.matchReason
                )
            }
        val allProfiles = socialProfiles + missing
        val reconciled = sweep.map { check ->
            if (check.status == SweepStatus.FOUND) return@map check
            val platform = SWEEP_PLATFORMS.firstOrNull { it.name == check.platform } ?: return@map check
            val hit = allProfiles.firstOrNull { platform.owns(it.url) } ?: return@map check
            check.copy(status = SweepStatus.FOUND, url = hit.url, displayName = hit.displayName, username = hit.username, matchLevel = hit.matchLevel)
        }
        return copy(socialProfiles = allProfiles, platformSweep = reconciled)
    }

    /** Extracts a username from a social profile URL such as x.com/timbl or linkedin.com/in/timbl. */
    private fun handleFromUrl(url: String): String? {
        val host = url.substringAfter("://").substringBefore('/').removePrefix("www.").lowercase()
        if (SOCIAL_HOSTS.none { host == it || host.endsWith(".$it") }) return null
        val path = url.substringAfter("://").substringAfter('/', "").trim('/')
        val segment = path.removePrefix("in/").removePrefix("@").substringBefore('/').substringBefore('?')
        return segment.takeIf {
            it.length in 3..40 && it.none(Char::isWhitespace) && !it.contains('.') && it !in GENERIC_SEGMENTS
        }
    }

    // ---------------------------------------------------------------- generation

    private suspend fun GenerativeModel.generateJson(label: String, prompt: String): String {
        val response = try {
            generateWithRetry(label, prompt)
        } catch (e: ResponseStoppedException) {
            logUsage(label, e.response)
            val reason = e.response.candidates.firstOrNull()?.finishReason
            Log.w(TAG, "$label stopped early: $reason")
            throw AiResponseException(
                when (reason) {
                    FinishReason.MAX_TOKENS ->
                        "The search produced more than we could process. Add a clue to narrow it and try again."
                    FinishReason.SAFETY, FinishReason.PROHIBITED_CONTENT, FinishReason.SPII, FinishReason.BLOCKLIST ->
                        "This search was blocked by content safety rules."
                    FinishReason.RECITATION ->
                        "The result reused too much copyrighted text. Please try again."
                    else -> "The search stopped unexpectedly. Please try again."
                }, e
            )
        }
        logUsage(label, response)
        return response.text?.takeIf(String::isNotBlank)
            ?: throw AiResponseException("The search returned no data. Please try again.")
    }

    /**
     * Vertex serves Gemini through dynamic shared quota, so a 429 "Resource exhausted" or a 503
     * "high demand" is usually momentary capacity, not a real quota problem. Retry a few times.
     */
    private suspend fun GenerativeModel.generateWithRetry(label: String, prompt: String): GenerateContentResponse {
        var attempt = 0
        while (true) {
            try {
                return generateContent(prompt)
            } catch (e: Exception) {
                if (e !is QuotaExceededException && !(e is ServerException && e.isTransient())) throw e
                if (++attempt > RETRY_DELAYS_MS.size) {
                    throw AiResponseException("The service is busy right now. Please try again in a moment.", e)
                }
                Log.w(TAG, "$label transient failure (attempt $attempt): ${e.message?.take(120)}")
                val base = RETRY_DELAYS_MS[attempt - 1]
                delay(base + Random.nextLong(-base / 3, base / 3))
            }
        }
    }

    private fun ServerException.isTransient(): Boolean {
        val m = message.orEmpty()
        return listOf("Resource exhausted", "429", "503", "UNAVAILABLE", "high demand", "overloaded")
            .any { m.contains(it, ignoreCase = true) }
    }

    private fun logUsage(label: String, response: GenerateContentResponse) {
        val usage = response.usageMetadata ?: return
        Log.d(
            TAG,
            "$label usage: prompt=${usage.promptTokenCount} thoughts=${usage.thoughtsTokenCount} " +
                "output=${usage.candidatesTokenCount} toolPrompt=${usage.toolUsePromptTokenCount} " +
                "total=${usage.totalTokenCount} finish=${response.candidates.firstOrNull()?.finishReason?.name}"
        )
        val queries = response.candidates.firstOrNull()?.groundingMetadata?.webSearchQueries.orEmpty()
        Log.d(TAG, "$label ran ${queries.size} search queries: $queries")
    }

    private inline fun <reified T> decode(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        Log.w(TAG, "Malformed model JSON: ${text.take(300)}", e)
        throw AiResponseException("We couldn't read the search result. Please try again.", e)
    }

    // ---------------------------------------------------------------- post-processing

    private fun CandidateSearchResponse.sanitizedCandidates() = copy(
        candidates = candidates.take(5).map { candidate ->
            candidate.copy(publicProfiles = candidate.publicProfiles.distinctBy { it.url }.take(6))
        }
    )

    private fun DeepSearchReport.sanitizedReport(): DeepSearchReport {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val safeContacts = businessContacts.filterNot {
            it.type.contains("phone", true) ||
                (it.type.contains("email", true) && !it.label.contains("business", true))
        }
        return copy(
            reportId = UUID.randomUUID().toString(),
            generatedDate = today,
            socialProfiles = socialProfiles.distinctBy { it.url }.take(10),
            professionalTimeline = professionalTimeline.take(12),
            businessContacts = safeContacts.take(6),
            articlesAndMentions = articlesAndMentions.distinctBy { it.url }.take(8),
            uncertainties = uncertainties.take(8),
            sources = sources.distinctBy { it.url }.take(24).map { it.copy(retrievedDate = today) }
        )
    }

    private companion object {
        const val TAG = "FirebaseAiService"
        private val RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000)

        /** Vertex rate-limits per model, so the sweep uses its own Flash model and a small concurrency cap. */
        const val SWEEP_MODEL = "gemini-3.6-flash"
        const val SWEEP_CONCURRENCY = 5
        /** Fast pass while the user waits on a spinner. */
        const val CANDIDATE_MODEL = "gemini-3.8-flash"

        /** Paid report: stronger disambiguation and corroboration, slower and ~3x the token price. */
        const val REPORT_MODEL = "gemini-3.1-pro-preview"

        val CANDIDATE_RULES = """
            You identify plausible public candidates for a named person using Google Search. This is a
            lightweight identification pass, not a biography or background investigation.

            Search strategy:
            - Search the name together with the supplied clues, then with smaller combinations of clues if
              the combined search is weak.
            - If a profile URL or username was supplied, search for it directly first; the page it
              identifies anchors the candidate.
            - Then run platform-specific searches for the name plus one clue on each major network:
              linkedin.com/in, x.com or twitter.com, instagram.com, facebook.com, github.com, youtube.com,
              tiktok.com, threads.net, bsky.app, plus Wikipedia and a personal website. Skip a platform
              only after a search for it returned nothing plausible.
            - Try reasonable spelling variants and transliterations of the name.
            - A candidate does not need to match every clue. Do not discard a plausible candidate because
              one clue cannot be verified publicly.
            - Prefer candidates supported by several independent clues.

            Candidate rules:
            - Return at most 5 candidates and at most 6 public profile URLs per candidate. Prefer one URL per
              platform: LinkedIn, X, Instagram, Facebook, GitHub, YouTube, TikTok, Threads, Bluesky, Wikipedia,
              personal site. platform is the network's common name, such as "LinkedIn" or "X".
            - Every URL must be a public result actually found during search. Never invent URLs, usernames,
              employers, locations, or any other fact.
            - Never merge information about different people into one candidate.
            - Leave role, company, or location empty when no public source states them.
            - matchingClues lists the supplied clues confirmed by a public source. unverifiedClues lists the
              supplied clues that could not be confirmed. A clue appears in at most one of the two lists.
            - Do not infer private, sensitive, or protected attributes. Do not return contact details,
              home addresses, phone numbers, or personal email addresses.

            Match levels for each profile URL:
            - OFFICIAL: an official or clearly self-authored public page of this person.
            - STRONG: several supplied clues independently match this person.
            - PARTIAL: at least one meaningful clue matches, but identity is not confirmed.
            - UNCONFIRMED: a plausible name match with no clue linking it to the supplied details.

            Status:
            - CANDIDATES_FOUND when one or more plausible candidates were found.
            - NEEDS_MORE_INFO when plausible candidates exist but the clues cannot distinguish the right one.
            - NO_RELIABLE_MATCH only when no plausible public candidate exists after trying name variants
              and clue combinations. Never use it merely because clues could not be verified.

            requestedClues is used only with NEEDS_MORE_INFO and may contain only: COUNTRY_OR_CITY,
            COMPANY_OR_PROFESSION, USERNAME_OR_PROFILE_URL, SCHOOL_OR_PROJECT. Ask only for clues that
            would separate the remaining candidates.

            message is shown to the user: one or two plain sentences explaining the outcome.
            id is a short stable string such as c1, c2.
        """.trimIndent()

        val REPORT_RULES = """
            You write a source-grounded report about the public professional footprint of one confirmed
            person, using Google Search. Facts come only from public pages found during search. Never guess.
            Do not contact the person.

            Social profiles: the request lists profiles already verified by a per-network search. Include
            every one of them in socialProfiles, enriched from the page or search snippet, and add any further
            public profile you find. Explain each link in matchReason.

            Reading pages: open at most 5 pages with the URL context tool, preferring the personal website,
            Wikipedia, and profiles whose bio or follower count is still unknown, and use what is publicly
            visible: display name, handle, bio, headline, location, follower or
            connection counts (put counts in audience), and pinned or recent public content. Some networks
            such as LinkedIn, Instagram, and Facebook may block reading; then extract everything the search
            snippet shows and mark the profile PARTIAL unless another source confirms it.

            LinkedIn specifically: the page is usually behind a login, so the Google snippet is the source.
            Capture the headline, current role and employer, location, and every experience or education
            entry visible in the snippet or page, and fill professionalTimeline with one entry per role or
            degree. A role stated only in the LinkedIn headline is still included, at PARTIAL match level,
            citing the LinkedIn profile as its source. Never leave person.role empty when a verified
            LinkedIn headline states one.

            Facebook and Instagram usually show only a name, handle, and short intro. Include the profile
            with whatever is visible rather than omitting it.

            Scope: identity evidence, public social profiles, current and previous professional roles,
            public education, personal websites and portfolios, projects and publications, news and public
            mentions, and contact channels explicitly published for business inquiries. General location
            only. Never include home addresses, personal phone numbers, personal email addresses, relatives,
            leaked data, credentials, live location, or inferences about sensitive traits.

            Size: prefer well-sourced items over padding. Return at most 6 identity evidence items, 10 social
            profiles, 10 timeline entries, 4 business contacts, 6 articles or mentions, 6 uncertainties, and
            16 sources. Keep summaries and bios to one or two sentences. Do not copy long
            passages.

            Sources: list every unique page once in sources with a short id such as s1, s2, and reference it
            elsewhere through sourceIds. Every material fact needs at least one sourceId. corroboration is
            MULTIPLE_SOURCES when the same fact appears on independent pages, otherwise SINGLE_SOURCE.
            supports lists the fact labels or timeline titles the source backs.

            Fields: leave a field empty when no source states it. Use an empty list rather than invented
            content. Put unsupported, conflicting, stale, or ambiguous claims in uncertainties. Timeline
            type is ROLE or EDUCATION. Match levels are OFFICIAL, STRONG, PARTIAL, UNCONFIRMED with the same
            meaning as in candidate identification. Dates are ISO-like strings such as 2021-03 or 2019 when
            a source gives them, otherwise empty.
        """.trimIndent()

        private val SOCIAL_HOSTS = listOf(
            "linkedin.com", "x.com", "twitter.com", "instagram.com", "facebook.com", "github.com",
            "youtube.com", "tiktok.com", "threads.net", "bsky.app", "medium.com", "substack.com"
        )
        private val GENERIC_SEGMENTS = setOf("in", "pub", "company", "profile", "user", "users", "channel", "c", "watch", "pages", "people", "groups", "home", "about")

        /**
         * Networks swept for every report. Google Search grounding rewrites queries and drops
         * `site:` operators, so each entry carries plain keywords and the hosts a hit must live on.
         */
        data class SweepPlatform(val name: String, val hosts: List<String>, val keywords: String, val profilePath: String)

        val SWEEP_PLATFORMS = listOf(
            SweepPlatform("LinkedIn", listOf("linkedin.com"), "LinkedIn profile", "linkedin.com/in/"),
            SweepPlatform("X", listOf("x.com", "twitter.com"), "X Twitter profile", "x.com/"),
            SweepPlatform("Instagram", listOf("instagram.com"), "Instagram", "instagram.com/"),
            SweepPlatform("Facebook", listOf("facebook.com"), "Facebook page", "facebook.com/"),
            SweepPlatform("GitHub", listOf("github.com"), "GitHub", "github.com/"),
            SweepPlatform("YouTube", listOf("youtube.com"), "YouTube channel", "youtube.com/@"),
            SweepPlatform("TikTok", listOf("tiktok.com"), "TikTok", "tiktok.com/@"),
            SweepPlatform("Threads", listOf("threads.net", "threads.com"), "Threads profile", "threads.net/@"),
            SweepPlatform("Bluesky", listOf("bsky.app"), "Bluesky", "bsky.app/profile/"),
            SweepPlatform("Wikipedia", listOf("wikipedia.org"), "Wikipedia", "wikipedia.org/wiki/")
        )

        private val matchLevel = Schema.enumeration(listOf("OFFICIAL", "STRONG", "PARTIAL", "UNCONFIRMED"))

        val SWEEP_RULES = """
            You check whether one specific person has a public profile on one specific network, using Google
            Search. Run the listed searches in order until a result hosted on one of the accepted hosts
            clearly belongs to this person. Ignore results on any other host, including aggregator or
            fan pages that merely mention the network.

            Return FOUND only when a result on an accepted host clearly belongs to this person: the name,
            handle, bio, employer, or location agrees with the details given. The url must be the profile
            page on that host exactly as it appeared in a result. Return NOT_FOUND only after running all the
            listed searches without such a result. Never pick a different person with the same name.

            When FOUND, copy what the result shows: url, displayName, username (handle without @), bio (one
            sentence), audience (follower, subscriber, or connection count if shown), matchLevel (OFFICIAL for
            a verified or clearly self-owned profile, STRONG when several details agree, PARTIAL when only the
            name agrees), matchReason (one sentence), and snippet: the result's title and description text
            copied verbatim, up to 500 characters, because it is the only view of pages that cannot be opened.
            For LinkedIn put the full headline in bio. Leave fields empty when the result does not show them.
        """.trimIndent()

        val sweepSchema = Schema.obj(
            mapOf(
                "status" to Schema.enumeration(listOf("FOUND", "NOT_FOUND")),
                "url" to Schema.string(),
                "displayName" to Schema.string(),
                "username" to Schema.string(),
                "bio" to Schema.string(),
                "audience" to Schema.string(),
                "matchLevel" to matchLevel,
                "matchReason" to Schema.string(),
                "snippet" to Schema.string()
            ),
            optionalProperties = listOf("url", "displayName", "username", "bio", "audience", "matchLevel", "matchReason", "snippet")
        )

        private val profileSchema = Schema.obj(
            mapOf("platform" to Schema.string(), "url" to Schema.string(), "matchLevel" to matchLevel)
        )

        val candidateSchema = Schema.obj(
            mapOf(
                "status" to Schema.enumeration(listOf("CANDIDATES_FOUND", "NEEDS_MORE_INFO", "NO_RELIABLE_MATCH")),
                "message" to Schema.string(),
                "requestedClues" to Schema.array(Schema.string()),
                "candidates" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "id" to Schema.string(),
                            "name" to Schema.string(),
                            "role" to Schema.string(),
                            "company" to Schema.string(),
                            "location" to Schema.string(),
                            "publicProfiles" to Schema.array(profileSchema),
                            "matchingClues" to Schema.array(Schema.string()),
                            "unverifiedClues" to Schema.array(Schema.string())
                        ),
                        optionalProperties = listOf("role", "company", "location", "matchingClues", "unverifiedClues")
                    )
                )
            ),
            optionalProperties = listOf("requestedClues")
        )

        private val evidenceSchema = Schema.obj(
            mapOf(
                "label" to Schema.string(),
                "detail" to Schema.string(),
                "matchLevel" to matchLevel,
                "sourceIds" to Schema.array(Schema.string())
            ),
            optionalProperties = listOf("detail")
        )
        private val socialSchema = Schema.obj(
            mapOf(
                "platform" to Schema.string(),
                "url" to Schema.string(),
                "displayName" to Schema.string(),
                "username" to Schema.string(),
                "bio" to Schema.string(),
                "audience" to Schema.string(),
                "matchLevel" to matchLevel,
                "matchReason" to Schema.string(),
                "sourceIds" to Schema.array(Schema.string())
            ),
            optionalProperties = listOf("displayName", "username", "bio", "audience", "matchReason")
        )
        private val timelineSchema = Schema.obj(
            mapOf(
                "title" to Schema.string(),
                "organization" to Schema.string(),
                "start" to Schema.string(),
                "end" to Schema.string(),
                "location" to Schema.string(),
                "type" to Schema.enumeration(listOf("ROLE", "EDUCATION")),
                "sourceIds" to Schema.array(Schema.string())
            ),
            optionalProperties = listOf("start", "end", "location")
        )
        private val contactSchema = Schema.obj(
            mapOf(
                "type" to Schema.string(),
                "label" to Schema.string(),
                "value" to Schema.string(),
                "url" to Schema.string(),
                "sourceIds" to Schema.array(Schema.string())
            ),
            optionalProperties = listOf("label", "url")
        )
        private val articleSchema = Schema.obj(
            mapOf(
                "title" to Schema.string(),
                "url" to Schema.string(),
                "publisher" to Schema.string(),
                "publishedDate" to Schema.string(),
                "summary" to Schema.string(),
                "sourceIds" to Schema.array(Schema.string())
            ),
            optionalProperties = listOf("publisher", "publishedDate", "summary")
        )
        private val sourceSchema = Schema.obj(
            mapOf(
                "id" to Schema.string(),
                "title" to Schema.string(),
                "url" to Schema.string(),
                "publisher" to Schema.string(),
                "supports" to Schema.array(Schema.string()),
                "corroboration" to Schema.enumeration(listOf("MULTIPLE_SOURCES", "SINGLE_SOURCE"))
            ),
            optionalProperties = listOf("publisher", "supports")
        )
        val reportSchema = Schema.obj(
            mapOf(
                "person" to Schema.obj(
                    mapOf(
                        "name" to Schema.string(),
                        "role" to Schema.string(),
                        "company" to Schema.string(),
                        "generalLocation" to Schema.string(),
                        "matchLevel" to matchLevel
                    ),
                    optionalProperties = listOf("role", "company", "generalLocation")
                ),
                "summary" to Schema.string(),
                "identityEvidence" to Schema.array(evidenceSchema),
                "socialProfiles" to Schema.array(socialSchema),
                "professionalTimeline" to Schema.array(timelineSchema),
                "businessContacts" to Schema.array(contactSchema),
                "articlesAndMentions" to Schema.array(articleSchema),
                "uncertainties" to Schema.array(Schema.string()),
                "sources" to Schema.array(sourceSchema)
            )
        )
    }
}
