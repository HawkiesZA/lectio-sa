package za.co.hawkiesza.lectiosa.contenttool

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files

class MainTest {
    @Test
    fun sampleCalendarFilesArePresent() {
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/2026.csv")))
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/2027.csv")))
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/mass_forms.csv")))
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("content/calendar/mass_readings.csv")))
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

    @Test
    fun psalm78SourceOverlayReplacesMalformedVerseAggregate() {
        val directory = Files.createTempDirectory("lectio-psalm78-test")
        val rendered = directory.resolve("rendered.tsv")
        val source = directory.resolve("psalm78.tsv")
        val output = directory.resolve("overlay.tsv")
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                appendLine("Psalms\t77\t1\tbefore\t")
                appendLine("Psalms\t78\t0\t\t")
                appendLine("Psalms\t78\t1\tbad\t")
                appendLine("Psalms\t78\t13\tbad aggregate\t")
                appendLine("Psalms\t79\t1\tafter\t")
            },
        )
        Files.writeString(
            source,
            buildString {
                appendLine("book\tchapter\tverse\ttext")
                (1..72).forEach { verse -> appendLine("Psalms\t78\t$verse\tverse $verse") }
            },
        )

        overlaySource(rendered, source, output)

        val rows = Files.readAllLines(output).drop(1).map { it.split('\t', limit = 5) }
        assertTrue(rows.count { it[0] == "Psalms" && it[1] == "78" && it[2] != "0" } == 72)
        assertTrue(rows.any { it[0] == "Psalms" && it[1] == "78" && it[2] == "56" && it[3] == "verse 56" })
    }

    @Test
    fun sourceOverlaySupportsMultiplePsalmChapters() {
        val directory = Files.createTempDirectory("lectio-psalm-multi-test")
        val rendered = directory.resolve("rendered.tsv")
        val source = directory.resolve("psalms.tsv")
        val output = directory.resolve("overlay.tsv")
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                appendLine("Psalms\t10\t0\t\t")
                appendLine("Psalms\t10\t1\tbad\t")
                appendLine("Psalms\t11\t0\t\t")
                appendLine("Psalms\t11\t1\tbad\t")
            },
        )
        Files.writeString(
            source,
            buildString {
                appendLine("book\tchapter\tverse\ttext")
                (1..2).forEach { verse -> appendLine("Psalms\t10\t$verse\tpsalm 10 verse $verse") }
                (1..3).forEach { verse -> appendLine("Psalms\t11\t$verse\tpsalm 11 verse $verse") }
            },
        )

        overlaySource(rendered, source, output)

        val rows = Files.readAllLines(output).drop(1).map { it.split('\t', limit = 5) }
        assertTrue(rows.any { it[1] == "10" && it[2] == "2" && it[3] == "psalm 10 verse 2" })
        assertTrue(rows.any { it[1] == "11" && it[2] == "3" && it[3] == "psalm 11 verse 3" })
    }
}
