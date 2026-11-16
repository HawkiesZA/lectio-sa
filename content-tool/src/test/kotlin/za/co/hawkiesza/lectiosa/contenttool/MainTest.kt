package za.co.hawkiesza.lectiosa.contenttool

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files

class MainTest {
    @Test
    fun sampleCalendarFilesArePresent() {
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/2026.csv")))
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/2027.csv")))
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/multi_readings.csv")))
    }

    @Test
    fun psalm119SourceOverlayRequiresAndWritesAllVerses() {
        val directory = Files.createTempDirectory("lectio-psalm119-test")
        val rendered = directory.resolve("rendered.tsv")
        val source = directory.resolve("psalm119.tsv")
        val output = directory.resolve("overlay.tsv")
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                appendLine("Psalms\t118\t1\tbefore\t")
                appendLine("Psalms\t119\t0\t\t")
                appendLine("Psalms\t119\t1\tbad\t")
                appendLine("Psalms\t119\t7\tbad aggregate\t")
                appendLine("Psalms\t120\t1\tafter\t")
            },
        )
        Files.writeString(
            source,
            buildString {
                appendLine("book\tchapter\tverse\ttext")
                (1..176).forEach { verse -> appendLine("Psalms\t119\t$verse\tverse $verse") }
            },
        )

        overlaySource(rendered, source, output)

        val rows = Files.readAllLines(output).drop(1).map { it.split('\t', limit = 5) }
        assertTrue(rows.count { it[0] == "Psalms" && it[1] == "119" && it[2] != "0" } == 176)
        assertTrue(rows.any { it[0] == "Psalms" && it[1] == "119" && it[2] == "14" && it[3] == "verse 14" })
    }
}
