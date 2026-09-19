package com.haiklabs.tracelight

import kotlinx.serialization.Serializable

@Serializable
data class SearchProfile(
    val fullName: String = "",
    val countryOrRegion: String = "",
    val publicProfileUrl: String = "",
    val username: String = "",
    val companyOrProfession: String = "",
    val currentCity: String = "",
    val ageRange: String = "",
    val previousCity: String = "",
    val school: String = "",
    val previousCompany: String = "",
    val personalWebsite: String = "",
    val knownProject: String = "",
    val additionalContext: String = ""
) {
    fun strongClues(): List<String> = listOfNotNull(
        publicProfileUrl.trim().takeIf(String::isNotEmpty)?.let { "Public profile: $it" },
        username.trim().takeIf(String::isNotEmpty)?.let { "Username: $it" },
        companyOrProfession.trim().takeIf(String::isNotEmpty)?.let { "Company or profession: $it" },
        currentCity.trim().takeIf(String::isNotEmpty)?.let { "Current city: $it" }
    )

    fun additionalClues(): List<String> = listOfNotNull(
        ageRange.trim().takeIf(String::isNotEmpty)?.let { "Approximate age: $it" },
        previousCity.trim().takeIf(String::isNotEmpty)?.let { "Previous city: $it" },
        school.trim().takeIf(String::isNotEmpty)?.let { "School: $it" },
        previousCompany.trim().takeIf(String::isNotEmpty)?.let { "Previous company: $it" },
        personalWebsite.trim().takeIf(String::isNotEmpty)?.let { "Personal website: $it" },
        knownProject.trim().takeIf(String::isNotEmpty)?.let { "Known project/publication: $it" },
        additionalContext.trim().takeIf(String::isNotEmpty)?.let { "Additional context: $it" }
    )
}

object SearchProfileValidator {
    fun validate(profile: SearchProfile): String? = when {
        profile.fullName.trim().split(Regex("\\s+")).size < 2 -> "Enter the person's full name"
        profile.countryOrRegion.trim().length < 2 -> "Enter a country or region"
        profile.strongClues().isEmpty() -> "Add at least one strong clue"
        else -> null
    }
}
