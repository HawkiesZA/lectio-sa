package za.co.hawkiesza.lectiosa.contenttool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun sourceOverlayRepairsNonPsalmVerseBoundaries() {
        val directory = Files.createTempDirectory("lectio-vpl-overlay-test")
        val rendered = directory.resolve("rendered.tsv")
        val source = directory.resolve("source.tsv")
        val output = directory.resolve("overlay.tsv")
        Files.writeString(
            rendered,
            "book\tchapter\tverse\ttext\tosis_fragment\n" +
                "Mark\t4\t0\t\t\n" +
                "Mark\t4\t40\tverse 40 plus 41\tfragment\n",
        )
        Files.writeString(
            source,
            "book\tchapter\tverse\ttext\n" +
                "Mark\t4\t40\tverse 40\n" +
                "Mark\t4\t41\tverse 41\n",
        )

        overlaySource(rendered, source, output)

        val rows = Files.readAllLines(output).drop(1).map { it.split('\t', limit = 5) }
        assertTrue(rows.any { it[0] == "Mark" && it[1] == "4" && it[2] == "40" && it[3] == "verse 40" })
        assertTrue(rows.any { it[0] == "Mark" && it[1] == "4" && it[2] == "41" && it[3] == "verse 41" })
    }

    @Test
    fun calendarCitationValidationCoversAlternativesRangesPartialsAndPairedPsalms() {
        val directory = Files.createTempDirectory("lectio-calendar-validation-test")
        val rendered = directory.resolve("rendered.tsv")
        val calendar = directory.resolve("calendar")
        Files.createDirectories(calendar)
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                listOf("Matthew" to 1, "Matthew" to 2, "Deuteronomy" to 1, "Psalms" to 78, "Psalms" to 119).forEach { (book, chapter) ->
                    appendLine("$book\t$chapter\t0\t\t")
                }
                (1..25).forEach { verse -> appendLine("Matthew\t1\t$verse\ttext\t") }
                (1..20).forEach { verse -> appendLine("Matthew\t2\t$verse\ttext\t") }
                (1..2).forEach { verse -> appendLine("Deuteronomy\t1\t$verse\ttext\t") }
                (1..72).forEach { verse -> appendLine("Psalms\t78\t$verse\ttext\t") }
                (1..176).forEach { verse -> appendLine("Psalms\t119\t$verse\ttext\t") }
            },
        )
        Files.writeString(
            calendar.resolve("2026.csv"),
            buildString {
                appendLine("date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status")
                appendLine("2026-01-01,SACBC,Test,weekday,white,,Matthew 1:1a-25 or 1:18-20,Ps 78:56-57 58-59 61-62,Deuteronomy 1:1-2,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )
        Files.writeString(
            calendar.resolve("mass_readings.csv"),
            buildString {
                appendLine("date,calendar_scope,form_id,sequence,role,selection_rule,reading_citation,psalm_citation,source_url,review_status")
                appendLine("2026-01-01,SACBC,test,1,reading,obligatory,Matthew 1:1-2:2,Ps 78:1-2,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
                appendLine("2026-01-01,SACBC,test,2,psalm,obligatory,Ps 78:1-2,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )

        val report = validateCalendar(rendered, calendar)

        assertEquals(6, report.assignmentsChecked)
        assertEquals(7, report.alternativesChecked)
        assertEquals(1, report.partialVerseFallbacks)
        assertTrue(report.failures.isEmpty())
    }

    @Test
    fun calendarCitationValidationReportsMissingVerseWithSourceLocation() {
        val directory = Files.createTempDirectory("lectio-calendar-validation-failure-test")
        val rendered = directory.resolve("rendered.tsv")
        val calendar = directory.resolve("calendar")
        Files.createDirectories(calendar)
        Files.writeString(
            rendered,
            "book\tchapter\tverse\ttext\tosis_fragment\nMatthew\t1\t1\ttext\t\n",
        )
        Files.writeString(
            calendar.resolve("2026.csv"),
            buildString {
                appendLine("date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status")
                appendLine("2026-01-01,SACBC,Test,weekday,white,,Matthew 1:1-2,,,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )

        val report = validateCalendar(rendered, calendar)

        assertFalse(report.failures.isEmpty())
        assertTrue(report.failures.single().contains("2026.csv:2"))
        assertTrue(report.failures.single().contains("Matthew 1:2 verse not found"))
    }

    @Test
    fun calendarValidationAcceptsOnlyDocumentedContentExceptions() {
        val directory = Files.createTempDirectory("lectio-content-exception-test")
        val rendered = directory.resolve("rendered.tsv")
        val calendar = directory.resolve("calendar")
        Files.createDirectories(calendar)
        Files.writeString(
            rendered,
            "book\tchapter\tverse\ttext\tosis_fragment\n" +
                "Acts\t8\t36\ttext\t\n" +
                "Acts\t8\t38\ttext\t\n",
        )
        Files.writeString(
            calendar.resolve("2026.csv"),
            buildString {
                appendLine("date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status")
                appendLine("2026-01-01,SACBC,Test,weekday,white,,Acts 8:36-38,,,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )
        Files.writeString(
            calendar.resolve("content_exceptions.csv"),
            "edition_id,book_code,chapter,verse,reason,notice,source_url,review_status\n" +
                "web-catholic,Acts,8,37,textual_variant_omitted,Not included,https://ebible.org/eng-webc/,reviewed\n",
        )

        val report = validateCalendar(rendered, calendar)

        assertTrue(report.failures.isEmpty())
        assertEquals(1, report.documentedExceptions)
    }

    @Test
    fun calendarValidationUsesWebPsalmNumberingFallback() {
        val directory = Files.createTempDirectory("lectio-psalm-numbering-test")
        val rendered = directory.resolve("rendered.tsv")
        val calendar = directory.resolve("calendar")
        Files.createDirectories(calendar)
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                appendLine("Psalms\t67\t0\t\t")
                (1..7).forEach { verse -> appendLine("Psalms\t67\t$verse\ttext\t") }
                appendLine("Psalms\t40\t0\t\t")
                (1..17).forEach { verse -> appendLine("Psalms\t40\t$verse\ttext\t") }
            },
        )
        Files.writeString(
            calendar.resolve("2026.csv"),
            buildString {
                appendLine("date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status")
                appendLine("2026-01-01,SACBC,Test,weekday,white,, ,Ps 67:2-3 8,,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
                appendLine("2026-01-02,SACBC,Test,weekday,white,, ,Ps 40 (39):7-8,,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )

        val report = validateCalendar(rendered, calendar)

        assertTrue(report.failures.isEmpty())
    }

    @Test
    fun calendarValidationUsesChapterNumberingMappings() {
        val directory = Files.createTempDirectory("lectio-chapter-mapping-test")
        val rendered = directory.resolve("rendered.tsv")
        val calendar = directory.resolve("calendar")
        Files.createDirectories(calendar)
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                listOf("Romans" to 14, "Malachi" to 4, "Joel" to 3, "Hosea" to 14, "Genesis" to 32, "Jonah" to 2, "Zechariah" to 2, "Zechariah" to 3).forEach { (book, chapter) ->
                    appendLine("$book\t$chapter\t0\t\t")
                }
                (24..26).forEach { verse -> appendLine("Romans\t14\t$verse\ttext\t") }
                (1..6).forEach { verse -> appendLine("Malachi\t4\t$verse\ttext\t") }
                (12..21).forEach { verse -> appendLine("Joel\t3\t$verse\ttext\t") }
                (1..9).forEach { verse -> appendLine("Hosea\t14\t$verse\ttext\t") }
                (23..32).forEach { verse -> appendLine("Genesis\t32\t$verse\ttext\t") }
                (1..10).forEach { verse -> appendLine("Jonah\t2\t$verse\ttext\t") }
                (5..9).forEach { verse -> appendLine("Zechariah\t2\t$verse\ttext\t") }
                (1..2).forEach { verse -> appendLine("Zechariah\t3\t$verse\ttext\t") }
            },
        )
        Files.writeString(
            calendar.resolve("2026.csv"),
            buildString {
                appendLine("date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status")
                appendLine("2026-01-01,SACBC,Test,weekday,white,,Rom 16:25-27,Mal 3:23-24,Joel 4:12-21,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
                appendLine("2026-01-02,SACBC,Test,weekday,white,,Hos 14:2-10,Genesis 32:23-33,Jonah 2:1-11,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
                appendLine("2026-01-03,SACBC,Test,weekday,white,,Zech 2:5-9 14-15a,,,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )

        val report = validateCalendar(rendered, calendar)

        assertTrue(report.failures.isEmpty())
    }

    @Test
    fun calendarValidationUsesGreekEstherAdditionMapping() {
        val directory = Files.createTempDirectory("lectio-esther-mapping-test")
        val rendered = directory.resolve("rendered.tsv")
        val calendar = directory.resolve("calendar")
        Files.createDirectories(calendar)
        Files.writeString(
            rendered,
            buildString {
                appendLine("book\tchapter\tverse\ttext\tosis_fragment")
                appendLine("Esther\t4\t0\t\t")
                listOf(29, 31, 32, 33, 40, 41, 42).forEach { verse -> appendLine("Esther\t4\t$verse\ttext\t") }
            },
        )
        Files.writeString(
            calendar.resolve("2026.csv"),
            buildString {
                appendLine("date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status")
                appendLine("2026-01-01,SACBC,Test,weekday,white,,Esth C:12 14-16 23-25,,,,https://ordo.jesuitssouthern.africa/ordo_full.php,reviewed")
            },
        )

        val report = validateCalendar(rendered, calendar)

        assertTrue(report.failures.isEmpty())
    }
}
