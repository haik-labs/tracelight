package com.haiklabs.tracelight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchProfileValidatorTest {
    @Test fun acceptsProfileWithStrongClue() = assertNull(
        SearchProfileValidator.validate(SearchProfile(fullName = "Maya Chen", countryOrRegion = "UK", username = "mayac"))
    )

    @Test fun rejectsSingleName() = assertEquals(
        "Enter the person's full name",
        SearchProfileValidator.validate(SearchProfile(fullName = "Maya", countryOrRegion = "UK", username = "mayac"))
    )

    @Test fun requiresStrongClue() = assertEquals(
        "Add at least one strong clue",
        SearchProfileValidator.validate(SearchProfile(fullName = "Maya Chen", countryOrRegion = "UK"))
    )

    @Test fun normalizesCluesForPrompt() {
        val profile = SearchProfile(fullName = "Maya Chen", countryOrRegion = "UK", username = "mayac", currentCity = "London")
        assertEquals(listOf("Username: mayac", "Current city: London"), profile.strongClues())
    }
}
