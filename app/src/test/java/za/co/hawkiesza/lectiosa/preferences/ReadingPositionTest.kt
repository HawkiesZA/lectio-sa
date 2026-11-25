package za.co.hawkiesza.lectiosa.preferences

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.hawkiesza.lectiosa.content.SupportedDateRange

class ReadingPositionTest {
    private val range = SupportedDateRange(
        start = LocalDate.parse("2026-01-01"),
        end = LocalDate.parse("2027-12-31"),
    )

    @Test
    fun validRememberedDateWins() {
        assertEquals(
            LocalDate.parse("2026-11-19"),
            initialReadingDate("2026-11-19", LocalDate.parse("2026-11-20"), range),
        )
    }

    @Test
    fun unsupportedRememberedDateFallsBackToTodayWhenCovered() {
        assertEquals(
            LocalDate.parse("2026-11-20"),
            initialReadingDate("2028-01-01", LocalDate.parse("2026-11-20"), range),
        )
    }

    @Test
    fun todayOutsideCoverageClampsToNearestBoundary() {
        assertEquals(
            range.end,
            initialReadingDate(null, LocalDate.parse("2028-01-01"), range),
        )
        assertEquals(
            range.start,
            initialReadingDate(null, LocalDate.parse("2025-01-01"), range),
        )
    }

    @Test
    fun automaticDateFollowsTodayAfterMidnight() {
        assertEquals(
            ReadingDateRefreshResult(
                selectedDate = LocalDate.parse("2026-11-21"),
                selectionMode = ReadingDateSelectionMode.AUTOMATIC_TODAY,
            ),
            refreshReadingDate(
                selectedDate = LocalDate.parse("2026-11-20"),
                today = LocalDate.parse("2026-11-21"),
                selectionMode = ReadingDateSelectionMode.AUTOMATIC_TODAY,
                supportedDateRange = range,
            ),
        )
    }

    @Test
    fun pinnedDateSurvivesMidnight() {
        assertEquals(
            ReadingDateRefreshResult(
                selectedDate = LocalDate.parse("2026-11-20"),
                selectionMode = ReadingDateSelectionMode.PINNED,
            ),
            refreshReadingDate(
                selectedDate = LocalDate.parse("2026-11-20"),
                today = LocalDate.parse("2026-11-21"),
                selectionMode = ReadingDateSelectionMode.PINNED,
                supportedDateRange = range,
            ),
        )
    }

    @Test
    fun automaticDateClampsWhenTodayIsOutsideCoverage() {
        assertEquals(
            ReadingDateRefreshResult(
                selectedDate = range.end,
                selectionMode = ReadingDateSelectionMode.AUTOMATIC_TODAY,
            ),
            refreshReadingDate(
                selectedDate = range.end.minusDays(1),
                today = LocalDate.parse("2028-01-01"),
                selectionMode = ReadingDateSelectionMode.AUTOMATIC_TODAY,
                supportedDateRange = range,
            ),
        )
    }
}
