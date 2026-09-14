package com.haiklabs.tracelight

data class SearchProfile(val firstName: String, val lastName: String, val birthYear: String)

object SearchProfileValidator {
    fun validate(profile: SearchProfile): String? = when {
        profile.firstName.trim().length < 2 -> "Enter your first name"
        profile.lastName.trim().length < 2 -> "Enter your last name"
        profile.birthYear.toIntOrNull() !in 1900..2026 -> "Enter a valid birth year"
        else -> null
    }
}
