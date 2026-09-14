package com.haiklabs.tracelight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchProfileValidatorTest {
    @Test fun acceptsCompleteProfile() = assertNull(SearchProfileValidator.validate(SearchProfile("Maya", "Chen", "1993")))
    @Test fun rejectsInvalidYear() = assertEquals("Enter a valid birth year", SearchProfileValidator.validate(SearchProfile("Maya", "Chen", "20")))
    @Test fun rejectsMissingName() = assertEquals("Enter your first name", SearchProfileValidator.validate(SearchProfile("", "Chen", "1993")))
}
