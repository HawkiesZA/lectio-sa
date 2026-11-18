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
    fun preservesAlternativeThatUsesAnotherFullBookName() {
        assertEquals(
            listOf("Song 3:1-4a", "2 Cor 5:14-17"),
            splitAlternatives("Song 3:1-4a or 2 Cor 5:14-17"),
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
    fun normalizesParenthesizedPsalmNumbering() {
        assertEquals(
            ParsedCitation("Ps", listOf(CitationSpan(40, 7, 8))),
            parseCitation("Ps 40 (39):7-8"),
        )
    }

    @Test
    fun mapsCatholicChapterNumberingToWeb() {
        assertEquals(14 to 24, mappedSourceReference("Romans", 16, 25))
        assertEquals(4 to 6, mappedSourceReference("Malachi", 3, 24))
        assertEquals(3 to 21, mappedSourceReference("Joel", 4, 21))
        assertEquals(14 to 9, mappedSourceReference("Hosea", 14, 10))
        assertEquals(32 to 32, mappedSourceReference("Genesis", 32, 33))
        assertEquals(2 to 10, mappedSourceReference("Jonah", 2, 11))
        assertEquals(3 to 1, mappedSourceReference("Zechariah", 2, 14))
    }

    @Test
    fun mapsGreekEstherAdditionCToWebCatholicLayout() {
        assertEquals(
            ParsedCitation(
                "Esth",
                listOf(
                    CitationSpan(4, 29, 29),
                    CitationSpan(4, 31, 33),
                    CitationSpan(4, 40, 42),
                ),
            ),
            parseCitation("Esth C:12 14-16 23-25"),
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

    @Test
    fun parsesExpandedFullAndCompactBookAliases() {
        assertEquals("Deuteronomy", parseCitation("Deuteronomy 30:15-20")?.alias)
        assertEquals("Jeremiah", parseCitation("Jeremiah 31:31-34")?.alias)
        assertEquals("2 Peter", parseCitation("2 Peter 1:16-19")?.alias)
        assertEquals("2Chr", parseCitation("2Chr 36:14-16")?.alias)
        assertEquals("Song", parseCitation("Song 3:1-4a")?.alias)
    }
}
