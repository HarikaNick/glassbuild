package com.erol.glasskeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishPrefixMatcherTest {
    @Test
    fun acceptsExactPrefix() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("merhaba", "merh"))
        assertEquals(0, TurkishPrefixMatcher.distanceScore("merhaba", "merh"))
    }

    @Test
    fun acceptsAdjacentTransposition() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("merhaba", "merhba"))
        assertEquals(1, TurkishPrefixMatcher.distanceScore("merhaba", "merhba"))
    }

    @Test
    fun acceptsSingleSubstitutionAndExtraCharacter() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("kitap", "kitqp"))
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("kitap", "kitapx"))
        assertEquals(1, TurkishPrefixMatcher.distanceScore("kitap", "kitapx"))
    }

    @Test
    fun rejectsPrefixesWithMultipleErrors() {
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("merhaba", "mxrhba"))
        assertEquals(2, TurkishPrefixMatcher.distanceScore("merhaba", "mxrhba"))
    }

    @Test
    fun rejectsEmptyCandidateOrPrefixForMatching() {
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("merhaba", ""))
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("", "mer"))
        assertEquals(2, TurkishPrefixMatcher.distanceScore("", "mer"))
        assertEquals(0, TurkishPrefixMatcher.distanceScore("merhaba", ""))
    }

    @Test
    fun supportsTurkishCharacters() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("şişe", "şiş"))
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("çalışmak", "çalışmka"))
    }

    @Test
    fun rejectsWordsWhoseLengthCannotMatchWithOneEdit() {
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("ev", "evler"))
        assertEquals(2, TurkishPrefixMatcher.distanceScore("ev", "evler"))
    }
}
