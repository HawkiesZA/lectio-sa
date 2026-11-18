package za.co.hawkiesza.lectiosa.content

import java.time.LocalDate

data class SupportedDateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "Supported date range must be ordered" }
    }

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

data class CalendarDateRange(
    val minDate: String?,
    val maxDate: String?,
) {
    fun toSupportedDateRangeOrNull(): SupportedDateRange? {
        val start = minDate?.let(LocalDate::parse) ?: return null
        val end = maxDate?.let(LocalDate::parse) ?: return null
        return SupportedDateRange(start, end)
    }
}
