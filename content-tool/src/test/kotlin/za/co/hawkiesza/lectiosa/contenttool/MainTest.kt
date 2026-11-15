package za.co.hawkiesza.lectiosa.contenttool

import kotlin.test.Test
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun sampleCalendarFilesArePresent() {
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/2026.csv")))
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/2027.csv")))
    }
}
