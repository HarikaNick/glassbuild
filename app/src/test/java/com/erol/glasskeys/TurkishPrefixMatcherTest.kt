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
    fun acceptsSingleSubstitutionAndMissingCharacter() {
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("kitap", "kitqp"))
        assertTrue(TurkishPrefixMatcher.hasSingleEditPrefixMatch("kitap", "kitapx"))
    }

    @Test
    fun rejectsPrefixesWithMultipleErrors() {
        assertFalse(TurkishPrefixMatcher.hasSingleEditPrefixMatch("merhaba", "mxrhba"))
        assertEquals(2, TurkishPrefixMatcher.distanceScore("merhaba", "mxrhba"))
    }
}
