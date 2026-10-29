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
    fun acceptsOneMissingCharacterAtEndOfShortPrefix() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("araba", "arab"))
        assertEquals(1, TurkishPrefixMatcher.distanceScore("araba", "arbaa"))
    }

    @Test
    fun allowsOneEditWhenCandidateIsShorterThanTypedPrefix() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("a", "ab"))
        assertEquals(1, TurkishPrefixMatcher.distanceScore("a", "ab"))
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
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("İstanbul", "İst"))
    }

    @Test
    fun scoresTurkishCaseAndDiacriticDifferencesAsEdits() {
        assertEquals(1, TurkishPrefixMatcher.distanceScore("istanbul", "İst"))
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("cagri", "çağ"))
    }

    @Test
    fun rejectsWordsWhoseLengthCannotMatchWithOneEdit() {
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("ev", "evler"))
        assertEquals(2, TurkishPrefixMatcher.distanceScore("ev", "evler"))
    }

    @Test
    fun exactMatchScoresBetterThanTypoAndNoMatch() {
        assertEquals(0, TurkishPrefixMatcher.distanceScore("kitap", "kit"))
        assertEquals(1, TurkishPrefixMatcher.distanceScore("kitap", "kiap"))
        assertEquals(2, TurkishPrefixMatcher.distanceScore("kitap", "xyz"))
    }
}
