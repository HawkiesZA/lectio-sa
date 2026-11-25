package za.co.hawkiesza.lectiosa.preferences

import java.time.LocalDate
import za.co.hawkiesza.lectiosa.content.SupportedDateRange

enum class ReadingDateSelectionMode {
    AUTOMATIC_TODAY,
    PINNED,
}

data class ReadingDateRefreshResult(
    val selectedDate: LocalDate,
    val selectionMode: ReadingDateSelectionMode,
)

fun refreshReadingDate(
    selectedDate: LocalDate,
    today: LocalDate,
    selectionMode: ReadingDateSelectionMode,
    supportedDateRange: SupportedDateRange,
): ReadingDateRefreshResult {
    if (selectionMode == ReadingDateSelectionMode.PINNED) {
        return ReadingDateRefreshResult(selectedDate, selectionMode)
    }

    return ReadingDateRefreshResult(
        selectedDate = initialReadingDate(null, today, supportedDateRange),
        selectionMode = ReadingDateSelectionMode.AUTOMATIC_TODAY,
    )
}
