package za.co.hawkiesza.lectiosa.content

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyContentViewModelTest {
    @Test
    fun splitsFullBookAlternatives() {
        assertEquals(
            listOf("Matthew 1:1-25", "Matthew 1:18-25"),
            splitAlternatives("Matthew 1:1-25 or Matthew 1:18-25"),
        )
    }

    @Test
    fun keepsCitationWithoutAlternativesUnchanged() {
        assertEquals(
            listOf("Prov 31:10-13 19-20 30-31"),
            splitAlternatives("Prov 31:10-13 19-20 30-31"),
        )
    }

    @Test
    fun parsesSameBookCrossChapterRange() {
        assertEquals(
            ParsedCitation("Rev", listOf(CitationSpan(1, 1, 4), CitationSpan(2, 1, 5))),
            parseCitation("Rev 1:1-4 2:1-5"),
        )
    }

    @Test
    fun inheritsBookForShortAlternative() {
        assertEquals(
            listOf("Matt 4:12-23", "Matt 4:12-17"),
            splitAlternatives("Matt 4:12-23 or 4:12-17"),
        )
    }

    @Test
    fun preservesPlusSeparatedVerses() {
        assertEquals(
            ParsedCitation("Ps", listOf(CitationSpan(51, 14, 14), CitationSpan(51, 17, 17))),
            parseCitation("Ps 51:14+17"),
        )
    }

    @Test
    fun identifiesPartialVerseSelectorsForWholeVerseFallback() {
        assertEquals(
            "Whole verse included; the calendar selects part of this verse.",
            partialVerseFallbackNotice("Isa 12:2-3 4bcd 5-6"),
        )
        assertEquals(null, partialVerseFallbackNotice("Ps 78:56-57 58-59 61-62"))
    }

    @Test
    fun parsesExodusAbbreviationUsedByMultiReadings() {
        assertEquals(
            ParsedCitation("Exod", listOf(CitationSpan(14, 15, null), CitationSpan(15, 1, 1))),
            parseCitation("Exod 14:15-15:1"),
        )
    }

    @Test
    fun doesNotDuplicateNumericBookName() {
        assertEquals(
            listOf("3 John 1:5-8"),
            splitAlternatives("3 John 1:5-8"),
        )
    }
}
