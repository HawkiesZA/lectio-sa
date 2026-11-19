package za.co.hawkiesza.lectiosa.preferences

import java.time.LocalDate
import za.co.hawkiesza.lectiosa.content.SupportedDateRange

fun initialReadingDate(
    rememberedDate: String?,
    today: LocalDate,
    supportedDateRange: SupportedDateRange,
): LocalDate {
    val remembered = rememberedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (remembered != null && supportedDateRange.contains(remembered)) return remembered
    if (supportedDateRange.contains(today)) return today
    return if (today.isBefore(supportedDateRange.start)) supportedDateRange.start else supportedDateRange.end
}
