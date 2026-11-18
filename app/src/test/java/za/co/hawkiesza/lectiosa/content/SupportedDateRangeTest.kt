package za.co.hawkiesza.lectiosa.content

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedDateRangeTest {
    private val range = SupportedDateRange(
        start = LocalDate.parse("2026-01-01"),
        end = LocalDate.parse("2027-12-31"),
    )

    @Test
    fun boundariesAreInclusive() {
        assertTrue(range.contains(range.start))
        assertTrue(range.contains(range.end))
        assertFalse(range.contains(range.start.minusDays(1)))
        assertFalse(range.contains(range.end.plusDays(1)))
    }

    @Test
    fun databaseRangeConvertsToDateRange() {
        assertTrue(
            CalendarDateRange("2026-01-01", "2027-12-31").toSupportedDateRangeOrNull() == range,
        )
        assertTrue(CalendarDateRange(null, "2027-12-31").toSupportedDateRangeOrNull() == null)
    }
}
