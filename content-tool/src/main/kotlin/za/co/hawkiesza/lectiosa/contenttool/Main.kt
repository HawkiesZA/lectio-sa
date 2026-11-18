package za.co.hawkiesza.lectiosa.contenttool

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.time.Year

private const val EXPECTED_COLUMNS =
    "date,calendar_scope,celebration,rank,liturgical_colour,mass_form,first_reading,psalm,second_reading,gospel,source_url,review_status"

private data class ValidationIssue(val line: Int, val message: String)

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--inspect-sword") {
        inspectSwordModule(Path.of(args.getOrNull(1) ?: "engwebc2025eb"))
        return
    }
    if (args.firstOrNull() == "--export-sword") {
        require(args.size == 3) { "usage: --export-sword <module-root> <output.imp>" }
        exportSwordModule(Path.of(args[1]), Path.of(args[2]))
        return
    }
    if (args.firstOrNull() == "--normalize-imp") {
        require(args.size == 3) { "usage: --normalize-imp <input.imp> <output.tsv>" }
        normalizeImp(Path.of(args[1]), Path.of(args[2]))
        return
    }
    if (args.firstOrNull() == "--validate-tsv") {
        require(args.size == 2) { "usage: --validate-tsv <input.tsv>" }
        validateNormalized(Path.of(args[1]))
        return
    }
    if (args.firstOrNull() == "--render-tsv") {
        require(args.size == 3) { "usage: --render-tsv <input.tsv> <output.tsv>" }
        renderNormalized(Path.of(args[1]), Path.of(args[2]))
        return
    }
    if (args.firstOrNull() == "--validate-books") {
        require(args.size == 2) { "usage: --validate-books <rendered.tsv>" }
        validateBooks(Path.of(args[1]))
        return
    }
    if (args.firstOrNull() == "--validate-calendar") {
        require(args.size == 3) { "usage: --validate-calendar <rendered.tsv> <calendar-dir>" }
        validateCalendar(Path.of(args[1]), Path.of(args[2])).requireValid()
        return
    }
    if (args.firstOrNull() == "--compile-db") {
        require(args.size == 4) { "usage: --compile-db <rendered.tsv> <calendar-dir> <output.db>" }
        compileDatabase(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]))
        return
    }
    if (args.firstOrNull() == "--overlay-source") {
        require(args.size == 4) { "usage: --overlay-source <rendered.tsv> <source.tsv> <output.tsv>" }
        overlaySource(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]))
        return
    }

    val paths = args.map(Path::of).ifEmpty {
        listOf(Path.of("content/calendar/2026.csv"), Path.of("content/calendar/2027.csv"))
    }
    var failed = false

    paths.forEach { path ->
        val issues = validate(path)
        if (issues.isEmpty()) {
            println("OK $path")
        } else {
            failed = true
            issues.forEach { issue -> println("ERROR $path:${issue.line}: ${issue.message}") }
        }
    }

    if (failed) error("Calendar validation failed")
}

private fun exportSwordModule(moduleRoot: Path, output: Path) {
    require(Files.exists(moduleRoot)) { "SWORD module root not found: $moduleRoot" }
    output.parent?.let(Files::createDirectories)
    val process = ProcessBuilder("mod2imp", "engwebc2025eb")
        .directory(moduleRoot.toFile())
        .redirectErrorStream(true)
        .apply { environment()["SWORD_PATH"] = moduleRoot.toAbsolutePath().toString() }
        .start()
    process.inputStream.use { input -> Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    val exitCode = process.waitFor()
    check(exitCode == 0) { "mod2imp failed with exit code $exitCode" }
    println("WROTE $output")
}

private fun normalizeImp(input: Path, output: Path) {
    require(Files.exists(input)) { "IMP input not found: $input" }
    output.parent?.let(Files::createDirectories)
    val header = "book\tchapter\tverse\tfragment"
    val entryPattern = Regex("^\\$\\$\\$(.+) (\\d+):(\\d+)$")
    var currentKey: Triple<String, String, String>? = null
    val fragment = StringBuilder()
    val rows = mutableListOf<String>()

    fun flush() {
        val key = currentKey ?: return
        val value = fragment.toString().trim().replace("\t", " ").replace("\n", "\\n")
        rows += listOf(key.first, key.second, key.third, value).joinToString("\t")
        fragment.clear()
    }

    Files.readAllLines(input).forEach { line ->
        val match = entryPattern.matchEntire(line)
        if (match != null) {
            flush()
            currentKey = Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        } else if (currentKey != null && !line.startsWith("$$$[")) {
            fragment.appendLine(line)
        }
    }
    flush()
    Files.writeString(output, buildString {
        appendLine(header)
        rows.forEach { appendLine(it) }
    })
    println("WROTE $output (${rows.size} entries)")
}

private fun validateNormalized(path: Path) {
    require(Files.exists(path)) { "normalized input not found: $path" }
    val lines = Files.readAllLines(path)
    require(lines.firstOrNull() == "book\tchapter\tverse\tfragment") {
        "normalized input has an unexpected header"
    }

    var verseCount = 0
    var structuralCount = 0
    var linkedCount = 0
    lines.drop(1).forEachIndexed { index, line ->
        val lineNumber = index + 2
        val fields = line.split('\t', limit = 4)
        require(fields.size == 4) { "line $lineNumber must contain four tab-separated fields" }
        require(fields[0].isNotBlank()) { "line $lineNumber has no book identifier" }
        require(fields[1].toIntOrNull() != null && fields[2].toIntOrNull() != null) {
            "line $lineNumber has a non-numeric chapter or verse"
        }
        if (fields[1] == "0" || fields[2] == "0") structuralCount++ else verseCount++
        if (fields[3].contains("link", ignoreCase = true) || fields[3].contains("osisIDWork", ignoreCase = true)) {
            linkedCount++
        }
    }
    println("OK $path")
    println("entries=${lines.size - 1}")
    println("verse_entries=$verseCount")
    println("structural_entries=$structuralCount")
    println("link_marked_entries=$linkedCount")
}

private fun renderNormalized(input: Path, output: Path) {
    require(Files.exists(input)) { "normalized input not found: $input" }
    val lines = Files.readAllLines(input)
    require(lines.firstOrNull() == "book\tchapter\tverse\tfragment") {
        "normalized input has an unexpected header"
    }
    output.parent?.let(Files::createDirectories)

    val entries = lines.drop(1).filter(String::isNotBlank).mapIndexed { index, line ->
        val fields = line.split('\t', limit = 4)
        require(fields.size == 4) { "line " + (index + 2) + " must contain four tab-separated fields" }
        fields
    }
    val renderedTexts = renderOsisStream(entries.map { it[3] })
    val rows = entries.indices.map { index ->
        val fields = entries[index]
        listOf(fields[0], fields[1], fields[2], renderedTexts[index], fields[3]).joinToString("\t")
    }

    Files.writeString(output, buildString {
        appendLine("book\tchapter\tverse\ttext\tosis_fragment")
        rows.forEach { appendLine(it) }
    })
    println("WROTE " + output + " (" + rows.size + " entries)")
}

internal fun overlaySource(renderedPath: Path, sourcePath: Path, outputPath: Path) {
    require(Files.exists(renderedPath)) { "rendered input not found: $renderedPath" }
    require(Files.exists(sourcePath)) { "source overlay not found: $sourcePath" }

    val renderedHeader = "book\tchapter\tverse\ttext\tosis_fragment"
    val sourceHeader = "book\tchapter\tverse\ttext"
    val renderedRows = Files.readAllLines(renderedPath)
    require(renderedRows.firstOrNull() == renderedHeader) {
        "rendered input has an unexpected header"
    }
    val sourceRows = Files.readAllLines(sourcePath)
    require(sourceRows.firstOrNull() == sourceHeader) {
        "source overlay has an unexpected header"
    }

    val overlay = sourceRows.drop(1).filter(String::isNotBlank).map { line ->
        val fields = line.split('\t', limit = 4)
        require(fields.size == 4) { "source overlay row must contain four tab-separated fields" }
        require(fields[0].isNotBlank() && fields[1].toIntOrNull() != null) {
            "source overlay must contain a book and numeric chapter"
        }
        val verse = fields[2].toIntOrNull()
        require(verse != null && verse > 0) {
            "source verse must be a positive integer"
        }
        require(fields[3].isNotBlank() && !fields[3].contains('\t') && !fields[3].contains('\n')) {
            "source text must be a non-empty single TSV field"
        }
        fields
    }.sortedWith(compareBy({ it[0] }, { it[1].toInt() }, { it[2].toInt() }))

    val chapters = overlay.groupBy { it[0] to it[1].toInt() }

    val rows = renderedRows.drop(1).filter(String::isNotBlank).map { it.split('\t', limit = 5) }
    require(rows.all { it.size == 5 }) { "rendered input row must contain five tab-separated fields" }
    val withoutSource = rows.filterNot { row ->
        row[1].toIntOrNull()?.let { chapter -> (row[0] to chapter) in chapters && row[2] != "0" } == true
    }
    val outputRows = withoutSource.toMutableList()
    chapters.toSortedMap(compareBy<Pair<String, Int>> { it.first }.thenBy { it.second }).forEach { (book, chapter), chapterRows ->
        val insertionIndex = outputRows.indexOfFirst { it[0] == book && it[1] == chapter.toString() }
        require(insertionIndex >= 0) { "rendered input has no $book $chapter structural row" }
        val replacement = chapterRows.map { listOf(it[0], it[1], it[2], it[3], "source-override:eng-web-c-vpl") }
        outputRows.addAll(insertionIndex + 1, replacement)
    }

    outputPath.parent?.let(Files::createDirectories)
    Files.writeString(outputPath, buildString {
        appendLine(renderedHeader)
        outputRows.forEach { appendLine(it.joinToString("\t")) }
    })
    println("WROTE $outputPath (${chapters.size} source chapters overlaid)")
}

private fun renderOsisStream(fragments: List<String>): List<String> {
    val rendered = mutableListOf<String>()
    var noteDepth = 0

    fragments.forEach { fragment ->
        val text = StringBuilder()
        var cursor = 0
        while (cursor < fragment.length) {
            if (fragment[cursor] == '<') {
                val end = fragment.indexOf('>', cursor)
                if (end < 0) {
                    if (noteDepth == 0) text.append(fragment.substring(cursor))
                    break
                }
                val tag = fragment.substring(cursor + 1, end).trim()
                val closing = tag.startsWith("/")
                val name = tag.removePrefix("/").takeWhile { it.isLetterOrDigit() || it == ':' || it == '-' }
                if (name.equals("note", ignoreCase = true)) {
                    if (closing) noteDepth = (noteDepth - 1).coerceAtLeast(0)
                    else if (!tag.endsWith("/")) noteDepth++
                }
                cursor = end + 1
            } else {
                val end = fragment.indexOf('<', cursor).let { if (it < 0) fragment.length else it }
                if (noteDepth == 0) text.append(fragment.substring(cursor, end))
                cursor = end
            }
        }
        rendered += decodeXmlEntities(text.toString()).replace(Regex("\\s+"), " ").trim()
    }
    return rendered
}

private fun decodeXmlEntities(value: String): String =
    value.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

private val expectedCatholicBooks = setOf(
    "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
    "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
    "Nehemiah", "Tobit", "Judith", "Esther", "1 Maccabees", "2 Maccabees", "Job", "Psalms",
    "Proverbs", "Ecclesiastes", "Song of Solomon", "Wisdom", "Sirach", "Isaiah", "Jeremiah",
    "Lamentations", "Baruch", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah",
    "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
    "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
    "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
    "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
    "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation",
)

private val swordBookAliases = mapOf(
    "I Samuel" to "1 Samuel",
    "II Samuel" to "2 Samuel",
    "I Kings" to "1 Kings",
    "II Kings" to "2 Kings",
    "I Chronicles" to "1 Chronicles",
    "II Chronicles" to "2 Chronicles",
    "I Maccabees" to "1 Maccabees",
    "II Maccabees" to "2 Maccabees",
    "I Corinthians" to "1 Corinthians",
    "II Corinthians" to "2 Corinthians",
    "I Thessalonians" to "1 Thessalonians",
    "II Thessalonians" to "2 Thessalonians",
    "I Timothy" to "1 Timothy",
    "II Timothy" to "2 Timothy",
    "I Peter" to "1 Peter",
    "II Peter" to "2 Peter",
    "I John" to "1 John",
    "II John" to "2 John",
    "III John" to "3 John",
    "Revelation of John" to "Revelation",
    "Canticles" to "Song of Solomon",
    "Song of Songs" to "Song of Solomon",
)

private fun validateBooks(path: Path) {
    require(Files.exists(path)) { "rendered input not found: $path" }
    val lines = Files.readAllLines(path)
    require(lines.firstOrNull() == "book\tchapter\tverse\ttext\tosis_fragment") {
        "rendered input has an unexpected header"
    }
    val present = lines.drop(1)
        .filter(String::isNotBlank)
        .map { it.substringBefore('\t') }
        .map { swordBookAliases[it] ?: it }
        .toSet()
    val missing = expectedCatholicBooks - present
    val unexpected = present - expectedCatholicBooks
    println("present_books=" + present.size)
    println("missing_books=" + missing.sorted().joinToString(", "))
    println("unexpected_books=" + unexpected.sorted().joinToString(", "))
    check(missing.isEmpty()) { "Catholic book-set validation failed" }
}

internal data class CalendarValidationReport(
    val datesChecked: Int,
    val formsChecked: Int,
    val assignmentsChecked: Int,
    val alternativesChecked: Int,
    val crossChapterCitations: Int,
    val noReadingDates: List<String>,
    val partialVerseFallbacks: Int,
    val documentedExceptions: Int,
    val failures: List<String>,
) {
    fun requireValid() {
        println("calendar_dates=$datesChecked")
        println("calendar_forms=$formsChecked")
        println("calendar_assignments=$assignmentsChecked")
        println("calendar_alternatives=$alternativesChecked")
        println("cross_chapter_citations=$crossChapterCitations")
        println("calendar_no_readings=${noReadingDates.size}")
        noReadingDates.forEach { println("NO_READINGS $it") }
        println("partial_verse_fallbacks=$partialVerseFallbacks")
        println("documented_exceptions=$documentedExceptions")
        println("calendar_failures=${failures.size}")
        failures.forEach { println("ERROR $it") }
        check(failures.isEmpty()) { "Calendar citation validation failed" }
    }
}

private data class CalendarCitation(
    val source: Path,
    val line: Int,
    val date: String,
    val form: String,
    val role: String,
    val citation: String,
)

internal data class SourceVerse(val book: String, val chapter: Int, val verse: Int)

private data class SourceSpan(val chapter: Int, val start: Int, val end: Int?)

private data class ParsedSourceCitation(
    val spans: List<SourceSpan>,
    val partialVerseCount: Int,
)

private data class ContentExceptionKey(val book: String, val chapter: Int, val verse: Int)

private data class SourceSpanValidation(val message: String?, val documentedExceptions: Int)

private data class CalendarStructureReport(
    val datesChecked: Int,
    val formsChecked: Int,
    val noReadingDates: List<String>,
    val failures: List<String>,
)

// Catholic lectionary Psalm numbering versus the WEB/SWORD verse labels.
// These are explicit because the offset is not uniform across all Psalms.
private val psalmCitationOffsets = mapOf(51 to 2, 52 to 2)

private val citationBookAliases = mapOf(
    "Gen" to "Genesis", "Genesis" to "Genesis", "Ex" to "Exodus", "Exod" to "Exodus", "Exodus" to "Exodus",
    "Lev" to "Leviticus", "Leviticus" to "Leviticus", "Num" to "Numbers", "Numbers" to "Numbers", "Deut" to "Deuteronomy", "Deuteronomy" to "Deuteronomy",
    "Josh" to "Joshua", "Joshua" to "Joshua", "Judg" to "Judges", "Judges" to "Judges", "Ruth" to "Ruth",
    "1 Sam" to "1 Samuel", "1Samuel" to "1 Samuel", "1 Samuel" to "1 Samuel", "2 Sam" to "2 Samuel", "2Samuel" to "2 Samuel", "2 Samuel" to "2 Samuel",
    "1 Kgs" to "1 Kings", "1 Kings" to "1 Kings", "2 Kgs" to "2 Kings", "2 Kings" to "2 Kings",
    "1 Chr" to "1 Chronicles", "1Chr" to "1 Chronicles", "1 Chronicles" to "1 Chronicles", "2 Chr" to "2 Chronicles", "2Chr" to "2 Chronicles", "2 Chronicles" to "2 Chronicles",
    "Ezra" to "Ezra", "Neh" to "Nehemiah", "Nehemiah" to "Nehemiah",
    "Tob" to "Tobit", "Tobit" to "Tobit", "Jdt" to "Judith", "Judith" to "Judith", "Esth" to "Esther", "Esther" to "Esther",
    "1 Macc" to "1 Maccabees", "1Macc" to "1 Maccabees", "1 Maccabees" to "1 Maccabees", "2 Macc" to "2 Maccabees", "2Macc" to "2 Maccabees", "2 Maccabees" to "2 Maccabees",
    "Job" to "Job", "Ps" to "Psalms", "Psalm" to "Psalms", "Psalms" to "Psalms", "Prov" to "Proverbs", "Proverbs" to "Proverbs",
    "Eccles" to "Ecclesiastes", "Eccl" to "Ecclesiastes", "Ecclesiastes" to "Ecclesiastes", "Song" to "Song of Solomon", "Song of Solomon" to "Song of Solomon", "Song of Songs" to "Song of Solomon",
    "Wis" to "Wisdom", "Wisdom" to "Wisdom", "Sir" to "Sirach", "Sirach" to "Sirach", "Isa" to "Isaiah", "Isaiah" to "Isaiah", "Jer" to "Jeremiah", "Jeremiah" to "Jeremiah", "Lam" to "Lamentations", "Lamentations" to "Lamentations",
    "Bar" to "Baruch", "Baruch" to "Baruch", "Ezek" to "Ezekiel", "Ezekiel" to "Ezekiel", "Dan" to "Daniel", "Daniel" to "Daniel", "Hos" to "Hosea", "Hosea" to "Hosea", "Joel" to "Joel", "Is" to "Isaiah",
    "Amos" to "Amos", "Obad" to "Obadiah", "Obadiah" to "Obadiah", "Jon" to "Jonah", "Jonah" to "Jonah", "Mic" to "Micah", "Micah" to "Micah", "Nah" to "Nahum", "Nahum" to "Nahum", "Hab" to "Habakkuk", "Habakkuk" to "Habakkuk",
    "Zeph" to "Zephaniah", "Zephaniah" to "Zephaniah", "Hag" to "Haggai", "Haggai" to "Haggai", "Zech" to "Zechariah", "Zechariah" to "Zechariah", "Mal" to "Malachi", "Malachi" to "Malachi",
    "Matt" to "Matthew", "Matthew" to "Matthew", "Mark" to "Mark", "Luke" to "Luke", "Lk" to "Luke",
    "John" to "John", "Jn" to "John", "Acts" to "Acts", "Rom" to "Romans", "Romans" to "Romans", "1 Cor" to "1 Corinthians", "1Cor" to "1 Corinthians", "1 Corinthians" to "1 Corinthians", "2 Cor" to "2 Corinthians", "2Cor" to "2 Corinthians", "2 Corinthians" to "2 Corinthians",
    "Gal" to "Galatians", "Galatians" to "Galatians", "Eph" to "Ephesians", "Ephesians" to "Ephesians", "Phil" to "Philippians", "Philippians" to "Philippians", "Col" to "Colossians", "Colossians" to "Colossians",
    "1 Thess" to "1 Thessalonians", "1Thess" to "1 Thessalonians", "1 Thessalonians" to "1 Thessalonians", "2 Thess" to "2 Thessalonians", "2Thess" to "2 Thessalonians", "2 Thessalonians" to "2 Thessalonians", "1 Tim" to "1 Timothy", "1Tim" to "1 Timothy", "1 Timothy" to "1 Timothy", "2 Tim" to "2 Timothy", "2Tim" to "2 Timothy", "2 Timothy" to "2 Timothy",
    "Tit" to "Titus", "Titus" to "Titus", "Phlm" to "Philemon", "Philemon" to "Philemon", "Heb" to "Hebrews", "Hebrews" to "Hebrews", "Jas" to "James", "James" to "James", "1 Pet" to "1 Peter", "1Pet" to "1 Peter", "1 Peter" to "1 Peter", "2 Pet" to "2 Peter", "2Pet" to "2 Peter", "2 Peter" to "2 Peter",
    "1 John" to "1 John", "1John" to "1 John", "2 John" to "2 John", "2John" to "2 John", "3 John" to "3 John", "3John" to "3 John", "Jude" to "Jude", "Rev" to "Revelation", "Revelation" to "Revelation",
)

internal fun validateCalendar(renderedPath: Path, calendarDir: Path): CalendarValidationReport {
    val verseIndex = readVerseIndex(renderedPath)
    val structure = validateCalendarStructure(calendarDir)
    val citations = readCalendarCitations(calendarDir)
    val exceptions = readContentExceptions(calendarDir)
    val failures = mutableListOf<String>()
    var alternativesChecked = 0
    var partialVerseFallbacks = 0
    var documentedExceptions = 0
    var crossChapterCitations = 0

    citations.forEach { input ->
        splitCitationAlternatives(input.citation).forEach { alternative ->
            alternativesChecked++
            val parsedResult = runCatching { parseSourceCitation(alternative) }
            if (parsedResult.isFailure) {
                val error = parsedResult.exceptionOrNull()
                failures += formatCitationFailure(input, alternative, error?.message ?: "invalid citation")
                return@forEach
            }
            val parsed = parsedResult.getOrThrow()
            partialVerseFallbacks += parsed.partialVerseCount
            crossChapterCitations += parsed.spans.zipWithNext().count { (first, second) -> first.chapter != second.chapter }
            parsed.spans.forEach { span ->
                val book = citationBookAliases.getValue(alternativeBookAlias(alternative))
                val validation = validateSourceSpan(verseIndex, book, span, exceptions)
                documentedExceptions += validation.documentedExceptions
                validation.message?.let { message ->
                    failures += formatCitationFailure(input, alternative, message)
                }
            }
        }
    }

    val report = CalendarValidationReport(
        datesChecked = structure.datesChecked,
        formsChecked = structure.formsChecked,
        assignmentsChecked = citations.size,
        alternativesChecked = alternativesChecked,
        crossChapterCitations = crossChapterCitations,
        noReadingDates = structure.noReadingDates,
        partialVerseFallbacks = partialVerseFallbacks,
        documentedExceptions = documentedExceptions,
        failures = structure.failures + failures,
    )
    return report
}

private fun validateCalendarStructure(calendarDir: Path): CalendarStructureReport {
    val failures = mutableListOf<String>()
    val calendarPaths = (2026..2027).map { calendarDir.resolve("$it.csv") }
    val calendarRows = mutableListOf<Pair<Path, List<String>>>()
    calendarPaths.filter { Files.exists(it) }.forEach { path ->
        Files.readAllLines(path).drop(1).forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val fields = parseCsvLine(line)
            if (fields.size != 12) {
                failures += "$path:${index + 2}: calendar row must contain 12 fields"
            } else {
                calendarRows += path to fields
            }
        }
    }
    val strictCoverage = calendarPaths.all { Files.exists(it) }
    val calendarKeys = calendarRows.map { it.second[0] to it.second[1] }
    calendarKeys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { duplicate ->
        failures += "duplicate calendar day: ${duplicate.first}/${duplicate.second}"
    }
    calendarRows.forEach { (path, fields) ->
        val date = runCatching { LocalDate.parse(fields[0]) }.getOrNull()
        if (date == null) {
            failures += "$path: invalid calendar date '${fields[0]}'"
        } else {
            val expectedYear = path.fileName.toString().substringBefore('.').toInt()
            if (date.year != expectedYear) failures += "$path: date ${fields[0]} is outside $expectedYear"
        }
    }
    if (strictCoverage) {
        (2026..2027).forEach { year ->
            val expected = (1..Year.of(year).length()).map { day ->
                LocalDate.ofYearDay(year, day).toString() to "SACBC"
            }.toSet()
            val actual = calendarKeys.filter { key -> key.first.startsWith("$year-") }.toSet()
            (expected - actual).forEach { missing -> failures += "missing calendar day: ${missing.first}/${missing.second}" }
            (actual - expected).forEach { unexpected -> failures += "unexpected calendar day: ${unexpected.first}/${unexpected.second}" }
        }
    }

    val formsPath = calendarDir.resolve("mass_forms.csv")
    val hasFormCatalog = Files.exists(formsPath)
    val formRows = if (Files.exists(formsPath)) {
        val lines = Files.readAllLines(formsPath)
        if (lines.firstOrNull() != "date,calendar_scope,form_id,label,sort_order,is_default,source_url,review_status") {
            failures += "$formsPath: unexpected header"
            emptyList()
        } else lines.drop(1).filter(String::isNotBlank).mapNotNull { line ->
            val fields = parseCsvLine(line)
            if (fields.size != 8) {
                failures += "$formsPath: mass-form row must contain 8 fields"
                null
            } else fields
        }
    } else emptyList()
    val explicitFormKeys = formRows.map { Triple(it[0], it[1], it[2]) }
    explicitFormKeys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { duplicate ->
        failures += "duplicate Mass form: ${duplicate.first}/${duplicate.second}/${duplicate.third}"
    }
    formRows.groupBy { it[0] to it[1] }.forEach { (day, rows) ->
        if (rows.count { it[5] == "1" } != 1) failures += "Mass form group must have exactly one default: $day"
        val sortOrders = rows.mapNotNull { it[4].toIntOrNull() }.sorted()
        if (sortOrders != (1..rows.size).toList()) failures += "Mass form sort orders are not contiguous: $day"
        if ((day.first to day.second) !in calendarKeys) failures += "Mass form references unknown calendar day: $day"
    }

    val readingsPath = calendarDir.resolve("mass_readings.csv")
    val readingRows = if (Files.exists(readingsPath)) {
        Files.readAllLines(readingsPath).drop(1).filter(String::isNotBlank).mapNotNull { line ->
            val fields = parseCsvLine(line)
            if (fields.size != 10) {
                failures += "$readingsPath: mass-reading row must contain 10 fields"
                null
            } else fields
        }
    } else emptyList()
    readingRows.groupBy { Triple(it[0], it[1], it[2]) }.forEach { (form, rows) ->
        if (hasFormCatalog && form !in explicitFormKeys) failures += "Mass reading references unknown form: $form"
        val sequences = rows.mapNotNull { it[3].toIntOrNull() }.sorted()
        if (sequences != (1..rows.size).toList()) failures += "Mass reading sequences are not contiguous: $form"
    }
    val implicitFormKeys = calendarRows.map { (path, fields) ->
        Triple(fields[0], fields[1], "default")
    }.filterNot { (it.first to it.second) in explicitFormKeys.map { form -> form.first to form.second }.toSet() }
    val allForms = if (hasFormCatalog) {
        (explicitFormKeys + implicitFormKeys).distinct()
    } else {
        readingRows.map { Triple(it[0], it[1], it[2]) }.distinct().ifEmpty { implicitFormKeys }
    }
    val noReadingDates = mutableSetOf<String>()
    allForms.groupBy { it.first to it.second }.forEach { (day, forms) ->
        val assignments = if (forms.any { it.third != "default" }) {
            readingRows.count { it[0] == day.first && it[1] == day.second && it[2] in forms.map { form -> form.third } }
        } else {
            calendarRows.count { it.second[0] == day.first && it.second[1] == day.second && it.second.drop(6).take(4).any(String::isNotBlank) }
        }
        if (assignments == 0) noReadingDates += "${day.first}/${day.second}"
        else if (forms.any { form ->
                form.third != "default" && readingRows.none { it[0] == day.first && it[1] == day.second && it[2] == form.third }
            }) {
            failures += "Mass form has no reading assignments: $day"
        }
    }
    return CalendarStructureReport(calendarRows.size, allForms.size, noReadingDates.sorted(), failures)
}

private fun readContentExceptions(calendarDir: Path): Set<ContentExceptionKey> {
    val path = calendarDir.resolve("content_exceptions.csv")
    if (!Files.exists(path)) return emptySet()
    val lines = Files.readAllLines(path)
    require(lines.firstOrNull() == "edition_id,book_code,chapter,verse,reason,notice,source_url,review_status") {
        "content-exception input has an unexpected header"
    }
    return lines.drop(1).filter(String::isNotBlank).map { line ->
        val fields = parseCsvLine(line)
        require(fields.size == 8) { "content-exception row in $path must contain 8 fields" }
        require(fields[0] == "web-catholic") { "unsupported content-exception edition: ${fields[0]}" }
        require(fields[2].toIntOrNull() != null && fields[3].toIntOrNull() != null) {
            "content-exception chapter and verse must be numeric: $line"
        }
        require(fields[4].isNotBlank() && fields[5].isNotBlank() && fields[7] == "reviewed") {
            "content-exception metadata is incomplete: $line"
        }
        ContentExceptionKey(fields[1], fields[2].toInt(), fields[3].toInt())
    }.toSet()
}

private fun readVerseIndex(path: Path): Map<SourceVerse, Boolean> {
    require(Files.exists(path)) { "rendered input not found: $path" }
    val lines = Files.readAllLines(path)
    require(lines.firstOrNull() == "book\tchapter\tverse\ttext\tosis_fragment") {
        "rendered input has an unexpected header"
    }
    return lines.drop(1).filter(String::isNotBlank).associate { line ->
        val fields = line.split('\t', limit = 5)
        require(fields.size == 5) { "rendered row must contain five tab-separated fields" }
        val chapter = fields[1].toIntOrNull() ?: error("invalid chapter: ${fields[1]}")
        val verse = fields[2].takeWhile(Char::isDigit).toIntOrNull() ?: error("invalid verse: ${fields[2]}")
        SourceVerse(swordBookAliases[fields[0]] ?: fields[0], chapter, verse) to fields[3].isNotBlank()
    }.filterValues { it }
}

private fun readCalendarCitations(calendarDir: Path): List<CalendarCitation> {
    require(Files.isDirectory(calendarDir)) { "calendar directory not found: $calendarDir" }
    val citations = mutableListOf<CalendarCitation>()
    Files.list(calendarDir).use { files ->
        files.filter { it.fileName.toString().matches(Regex("\\d{4}\\.csv")) }.sorted().forEach { path ->
            Files.readAllLines(path).drop(1).forEachIndexed { index, line ->
                if (line.isBlank()) return@forEachIndexed
                val fields = parseCsvLine(line)
                require(fields.size == 12) { "calendar row in $path must contain 12 fields" }
                listOf("first_reading", "psalm", "second_reading", "gospel").forEachIndexed { offset, role ->
                    fields[6 + offset].takeIf(String::isNotBlank)?.let { citation ->
                        citations += CalendarCitation(path, index + 2, fields[0], fields[5], role, citation)
                    }
                }
            }
        }
    }
    val readingsPath = calendarDir.resolve("mass_readings.csv")
    if (Files.exists(readingsPath)) {
        Files.readAllLines(readingsPath).drop(1).forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val fields = parseCsvLine(line)
            require(fields.size == 10) { "mass-reading row in $readingsPath must contain 10 fields" }
            citations += CalendarCitation(readingsPath, index + 2, fields[0], fields[2], fields[4], fields[6])
            fields[7].takeIf(String::isNotBlank)?.let { citation ->
                citations += CalendarCitation(readingsPath, index + 2, fields[0], fields[2], "paired_psalm", citation)
            }
        }
    }
    return citations
}

private fun splitCitationAlternatives(citation: String): List<String> {
    val alternatives = citation.split(Regex("\\s+or\\s+", RegexOption.IGNORE_CASE)).map(String::trim).filter(String::isNotBlank)
    val firstAlias = alternativeBookAlias(alternatives.firstOrNull() ?: "")
    return alternatives.mapIndexed { index, alternative ->
        val alreadyHasBook = citationBookAliases.keys.any { alternative.startsWith("$it ") }
        if (index > 0 && firstAlias.isNotBlank() && !alreadyHasBook && alternative.firstOrNull()?.isDigit() == true) {
            "$firstAlias $alternative"
        } else {
            alternative
        }
    }
}

private fun alternativeBookAlias(citation: String): String =
    citationBookAliases.keys.sortedByDescending(String::length).firstOrNull { citation.startsWith("$it ") }
        ?: citationBookAliases.keys.sortedByDescending(String::length).firstOrNull { citation.startsWith(it) && citation.drop(it.length).firstOrNull()?.isDigit() == true }
        ?: ""

private fun parseSourceCitation(citation: String): ParsedSourceCitation {
    val normalizedCitation = normalizeSpecialCitation(citation)
    val alias = alternativeBookAlias(normalizedCitation)
    require(alias.isNotBlank()) { "unknown book" }
    val remainder = normalizedCitation.removePrefix(alias).trim()
    require(remainder.isNotBlank()) { "missing chapter and verse" }
    var currentChapter: Int? = null
    val spans = mutableListOf<SourceSpan>()
    var partials = 0
    remainder.split(Regex("\\s+")).flatMap { it.replace(Regex("\\s*&\\s*"), "+").split('+') }.forEach { rawToken ->
        val token = rawToken.trim().trim('(', ')', ',', '.', ';')
        if (token.isBlank()) return@forEach
        require(token.isNotBlank()) { "empty reference" }
        val range = token.split('-', limit = 2)
        val start = parseSourceEndpoint(range[0], currentChapter)
        currentChapter = start.chapter
        if (start.partial) partials++
        val end = range.getOrNull(1)?.let { parseSourceEndpoint(it, currentChapter, start.verse) }
        if (end != null && end.partial) partials++
        if (end != null && end.chapter < start.chapter) error("range ends before it starts")
        if (end != null && end.chapter == start.chapter && end.verse < start.verse) error("range ends before it starts")
        if (end != null && end.chapter > start.chapter) {
            spans += SourceSpan(start.chapter, start.verse, null)
            for (chapter in (start.chapter + 1) until end.chapter) spans += SourceSpan(chapter, 1, null)
            spans += SourceSpan(end.chapter, 1, end.verse)
            currentChapter = end.chapter
        } else {
            spans += SourceSpan(start.chapter, start.verse, end?.verse ?: start.verse)
        }
    }
    return ParsedSourceCitation(spans, partials)
}

private fun normalizeSpecialCitation(citation: String): String {
    val psalmNormalized = citation.replace(Regex("^(Ps(?:alms)?\\s+\\d+)\\s+\\(\\d+\\):"), "$1:")
    val greekEsther = Regex("^(Esth(?:er)?\\s+)C:(.*)$", RegexOption.IGNORE_CASE).matchEntire(psalmNormalized)
        ?: return psalmNormalized
    val mappedRemainder = greekEsther.groupValues[2].replace(Regex("\\d+")) { (it.value.toInt() + 17).toString() }
    return "${greekEsther.groupValues[1]}4:$mappedRemainder"
}

private data class SourceEndpoint(val chapter: Int, val verse: Int, val partial: Boolean)

private fun parseSourceEndpoint(value: String, inheritedChapter: Int?, inheritedVerse: Int? = null): SourceEndpoint {
    val cleaned = value.trim().trim('(', ')', ',', '.', ';')
    if (cleaned.matches(Regex("^[a-z]+$", RegexOption.IGNORE_CASE))) {
        val chapter = inheritedChapter ?: error("missing chapter in '$value'")
        val verse = inheritedVerse ?: error("missing verse in '$value'")
        return SourceEndpoint(chapter, verse, partial = true)
    }
    val match = Regex("^(?:(\\d+):)?(\\d+)([a-z]+)?$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        ?: error("invalid reference '$value'")
    val chapter = match.groupValues[1].toIntOrNull() ?: inheritedChapter ?: error("missing chapter in '$value'")
    return SourceEndpoint(chapter, match.groupValues[2].toInt(), match.groupValues[3].isNotBlank())
}

private fun validateSourceSpan(
    index: Map<SourceVerse, Boolean>,
    book: String,
    span: SourceSpan,
    exceptions: Set<ContentExceptionKey>,
): SourceSpanValidation {
    val chapterVerses = index.keys.filter { it.book == book && it.chapter == span.chapter }.map { it.verse }.toSet()
    val end = span.end ?: chapterVerses.maxOrNull()
        ?: return SourceSpanValidation("$book ${span.chapter}: chapter has no verses", 0)
    val missing = (span.start..end).filterNot { sourceVerseCandidates(index, book, span.chapter, it).any() }
    val documented = missing.count { ContentExceptionKey(book, span.chapter, it) in exceptions }
    val undocumented = missing.filterNot { ContentExceptionKey(book, span.chapter, it) in exceptions }
    return SourceSpanValidation(
        undocumented.takeIf { it.isNotEmpty() }?.let { "$book ${span.chapter}:${it.joinToString(",")} verse not found" },
        documented,
    )
}

private fun sourceVerseCandidates(index: Map<SourceVerse, Boolean>, book: String, chapter: Int, citedVerse: Int): List<SourceVerse> {
    mappedSourceReference(book, chapter, citedVerse)?.let { mapped ->
        return listOf(mapped).filter(index::containsKey)
    }
    val chapterVerses = index.keys.filter { it.book == book && it.chapter == chapter }.map { it.verse }.toSet()
    if (book == "Psalms") {
        psalmCitationOffsets[chapter]?.let { offset ->
            return listOf(SourceVerse(book, chapter, citedVerse - offset)).filter { it.verse > 0 && it.verse in chapterVerses }
        }
        if (citedVerse in chapterVerses) return listOf(SourceVerse(book, chapter, citedVerse))
        if (citedVerse > 1 && citedVerse - 1 in chapterVerses) return listOf(SourceVerse(book, chapter, citedVerse - 1))
    }
    return if (citedVerse in chapterVerses) listOf(SourceVerse(book, chapter, citedVerse)) else emptyList()
}

internal fun mappedSourceReference(book: String, chapter: Int, verse: Int): SourceVerse? = when {
    book == "Romans" && chapter == 16 && verse >= 25 -> SourceVerse(book, 14, verse - 1)
    book == "Malachi" && chapter == 3 && verse >= 19 -> SourceVerse(book, 4, verse - 18)
    book == "Joel" && chapter == 4 -> SourceVerse(book, 3, verse)
    book == "Hosea" && chapter == 14 && verse == 10 -> SourceVerse(book, 14, 9)
    book == "Genesis" && chapter == 32 && verse == 33 -> SourceVerse(book, 32, 32)
    book == "Jonah" && chapter == 2 && verse == 11 -> SourceVerse(book, 2, 10)
    book == "Zechariah" && chapter == 2 && verse >= 14 -> SourceVerse(book, 3, verse - 13)
    else -> null
}

private fun formatCitationFailure(input: CalendarCitation, citation: String, message: String): String =
    "${input.source}:${input.line} ${input.date} ${input.form}/${input.role} '$citation': $message"

private fun compileDatabase(renderedPath: Path, calendarDir: Path, output: Path) {
    require(Files.exists(renderedPath)) { "rendered input not found: $renderedPath" }
    require(Files.isDirectory(calendarDir)) { "calendar directory not found: $calendarDir" }
    require(Files.exists(Path.of("content/schema.sql"))) { "content/schema.sql not found" }
    validateCalendar(renderedPath, calendarDir).requireValid()
    val roomIdentityHash = readRoomIdentityHash()
    val calendarRows = mutableListOf<List<String>>()
    Files.list(calendarDir).use { files ->
        files.filter { it.fileName.toString().matches(Regex("\\d{4}\\.csv")) }.sorted().forEach { calendarPath ->
            Files.readAllLines(calendarPath).drop(1).filter(String::isNotBlank).forEach { line ->
                val fields = parseCsvLine(line)
                require(fields.size == 12) { "calendar row in $calendarPath must contain 12 fields" }
                calendarRows += fields
            }
        }
    }
    val formsPath = calendarDir.resolve("mass_forms.csv")
    val formRows = if (Files.exists(formsPath)) {
        val lines = Files.readAllLines(formsPath)
        require(lines.firstOrNull() == "date,calendar_scope,form_id,label,sort_order,is_default,source_url,review_status") {
            "mass-form input has an unexpected header"
        }
        lines.drop(1).filter(String::isNotBlank).map { line ->
            val fields = parseCsvLine(line)
            require(fields.size == 8) { "mass-form row in $formsPath must contain 8 fields" }
            require(fields[4].toIntOrNull() != null && fields[4].toInt() > 0) { "mass-form sort order must be positive: $line" }
            require(fields[5] == "0" || fields[5] == "1") { "mass-form default must be 0 or 1: $line" }
            fields
        }
    } else emptyList()
    val readingsPath = calendarDir.resolve("mass_readings.csv")
    val readingRows = if (Files.exists(readingsPath)) {
        val lines = Files.readAllLines(readingsPath)
        require(lines.firstOrNull() == "date,calendar_scope,form_id,sequence,role,selection_rule,reading_citation,psalm_citation,source_url,review_status") {
            "mass-reading input has an unexpected header"
        }
        lines.drop(1).filter(String::isNotBlank).map { line ->
            val fields = parseCsvLine(line)
            require(fields.size == 10) { "mass-reading row in $readingsPath must contain 10 fields" }
            require(fields[3].toIntOrNull() != null && fields[3].toInt() > 0) { "mass-reading sequence must be positive: $line" }
            require(fields[6].isNotBlank()) { "mass-reading citation must not be blank: $line" }
            require(fields[5] in setOf("obligatory", "optional")) { "invalid mass-reading selection rule: $line" }
            fields
        }
    } else emptyList()
    val explicitKeys = formRows.map { it[0] to it[1] }.toSet()
    val formLabels = formRows.associate { (it[0] to it[1]) to it[3] }
    formRows.groupBy { it[0] to it[1] }.forEach { (day, rows) ->
        require(rows.count { it[5] == "1" } == 1) { "mass-form group must have exactly one default: $day" }
        require(rows.map { it[4].toInt() }.distinct().size == rows.size) { "duplicate mass-form sort order: $day" }
    }
    readingRows.groupBy { Triple(it[0], it[1], it[2]) }.forEach { (form, rows) ->
        require((form.first to form.second) in explicitKeys) { "mass-reading references unknown form: $form" }
        val sequences = rows.map { it[3].toInt() }.sorted()
        require(sequences == sequences.distinct() && sequences == (1..sequences.size).toList()) {
            "mass-reading sequences must be contiguous: $form"
        }
    }
    output.parent?.let(Files::createDirectories)
    Files.deleteIfExists(output)

    val sqlFile = Files.createTempFile(output.parent ?: Path.of("."), "lectio-compile-", ".sql")
    try {
        Files.newBufferedWriter(sqlFile).use { sql ->
            sql.appendLine("PRAGMA foreign_keys = ON;")
            sql.appendLine(Files.readString(Path.of("content/schema.sql")))
            sql.appendLine("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
            sql.appendLine("INSERT INTO room_master_table(id, identity_hash) VALUES (42, " + sqlString(roomIdentityHash) + ");")
            sql.appendLine("BEGIN;")
            sql.appendLine("INSERT INTO content_metadata(key, value) VALUES " +
                "('schema_version', '4'), " +
                "('source_module', 'engwebc2025eb'), " +
                "('rendered_source_sha256', " + sqlString(sha256(renderedPath)) + ");")
            sql.appendLine("INSERT INTO edition(id, display_name, source_module, versification, attribution, rights_notice) VALUES " +
                "('web-catholic', 'World English Bible Catholic', 'engwebc2025eb', 'Vulg', " +
                sqlString("eBible.org World English Bible Catholic") + ", " +
                sqlString("Public-domain Scripture text; preserve the publisher attribution and notice.") + ");")

            val exceptionPath = calendarDir.resolve("content_exceptions.csv")
            if (Files.exists(exceptionPath)) {
                Files.readAllLines(exceptionPath).drop(1).filter(String::isNotBlank).forEach { line ->
                    val fields = parseCsvLine(line)
                    require(fields.size == 8) { "content-exception row in $exceptionPath must contain 8 fields" }
                    sql.appendLine(
                        "INSERT INTO content_exception(edition_id, book_code, chapter, verse, reason, notice, source_url, review_status) VALUES (" +
                            fields.mapIndexed { index, value -> if (index == 2 || index == 3) value else sqlString(value) }.joinToString(",") + ");",
                    )
                }
            }

            var verseId = 1
            Files.readAllLines(renderedPath).drop(1).filter(String::isNotBlank).forEach { line ->
                val fields = line.split('\t', limit = 5)
                require(fields.size == 5) { "rendered row must contain five tab-separated fields" }
                sql.appendLine(
                    "INSERT INTO verse_unit(id, edition_id, book_code, chapter, native_verse_label, sort_order, text, osis_fragment) VALUES (" +
                        verseId + ", 'web-catholic', " + sqlString(fields[0]) + ", " +
                        (fields[1].toIntOrNull() ?: error("invalid chapter: " + fields[1])) + ", " +
                        sqlString(fields[2]) + ", " + verseId + ", " + sqlString(fields[3]) + ", " +
                        sqlString(fields[4]) + ");",
                )
                verseId++
            }

            var assignmentId = 1
            fun insertAssignment(
                date: String,
                scope: String,
                massFormId: String,
                sequence: Int,
                role: String,
                massForm: String,
                citation: String,
                pairedPsalm: String,
                selectionRule: String,
                reviewStatus: String,
            ) {
                require(citation.isNotBlank()) { "reading citation must not be blank for $date#$sequence" }
                require(selectionRule in setOf("obligatory", "optional")) {
                    "invalid selection rule '$selectionRule' for $date#$sequence"
                }
                sql.appendLine(
                    "INSERT INTO reading_assignment(id, date, calendar_scope, mass_form_id, sequence, role, mass_form, original_citation, paired_psalm_citation, selection_rule, review_status) VALUES (" +
                        assignmentId + ", " + sqlString(date) + ", " + sqlString(scope) + ", " + sqlString(massFormId) + ", " + sequence + ", " +
                        sqlString(role) + ", " + sqlString(massForm) + ", " + sqlString(citation) + ", " +
                        sqlString(pairedPsalm) + ", " + sqlString(selectionRule) + ", " + sqlString(reviewStatus) + ");",
                )
                assignmentId++
            }
            calendarRows.forEach { fields ->
                sql.appendLine(
                    "INSERT INTO calendar_day(date, calendar_scope, celebration, rank, liturgical_colour, review_status, source_url) VALUES (" +
                        fields.take(5).joinToString(",") { sqlString(it) } + ", " +
                        sqlString(fields[11]) + ", " + sqlString(fields[10]) + ");",
                )
            }
            formRows.forEach { fields ->
                sql.appendLine(
                    "INSERT INTO mass_form(date, calendar_scope, form_id, label, sort_order, is_default, source_url, review_status) VALUES (" +
                        fields.mapIndexed { index, value -> if (index == 4 || index == 5) value else sqlString(value) }.joinToString(",") + ");",
                )
            }
            calendarRows.filterNot { (it[0] to it[1]) in explicitKeys }.forEach { fields ->
                val label = fields[5].ifBlank { "Mass of the day" }
                sql.appendLine(
                    "INSERT INTO mass_form(date, calendar_scope, form_id, label, sort_order, is_default, source_url, review_status) VALUES (" +
                        listOf(fields[0], fields[1], "default", label, "1", "1", fields[10], fields[11]).mapIndexed { index, value ->
                            if (index == 4 || index == 5) value else sqlString(value)
                        }.joinToString(",") + ");",
                )
            }
            calendarRows.filterNot { (it[0] to it[1]) in explicitKeys }.forEach { fields ->
                listOf(
                    "first_reading" to fields[6],
                    "psalm" to fields[7],
                    "second_reading" to fields[8],
                    "gospel" to fields[9],
                ).filter { it.second.isNotBlank() }
                    .mapIndexed { index, (role, citation) -> index + 1 to role to citation }
                    .forEach { (sequenceAndRole, citation) ->
                        insertAssignment(
                            fields[0], fields[1], "default", sequenceAndRole.first, sequenceAndRole.second,
                            fields[5], citation, "", "obligatory", fields[11],
                        )
                    }
            }
            readingRows.forEach { fields ->
                insertAssignment(
                    fields[0], fields[1], fields[2], fields[3].toInt(), fields[4], formLabels[fields[0] to fields[1]] ?: "",
                    fields[6], fields[7], fields[5], fields[9],
                )
            }
            sql.appendLine("COMMIT;")
        }

        val process = ProcessBuilder("sqlite3", output.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
        Files.newInputStream(sqlFile).use { input ->
            process.outputStream.use { outputStream -> input.copyTo(outputStream) }
        }
        val processOutput = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "sqlite3 failed: $processOutput" }
        println("WROTE $output")
    } finally {
        Files.deleteIfExists(sqlFile)
    }
}

private fun readRoomIdentityHash(): String {
    val schemaPath = Path.of("app/schemas/za.co.hawkiesza.lectiosa.content.ContentDatabase/4.json")
    require(Files.exists(schemaPath)) {
        "Room schema not found at $schemaPath; run ./gradlew :app:compileDebugKotlin first"
    }
    val match = Regex("\\\"identityHash\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\"").find(Files.readString(schemaPath))
    return match?.groupValues?.get(1)
        ?: error("Room schema has no identityHash: $schemaPath")
}

private fun sqlString(value: String): String = "'" + value.replace("'", "''") + "'"

private fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val character = line[index]
        when {
            character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                field.append('"')
                index++
            }
            character == '"' -> quoted = !quoted
            character == ',' && !quoted -> {
                fields += field.toString()
                field.clear()
            }
            else -> field.append(character)
        }
        index++
    }
    fields += field.toString()
    return fields
}

private fun inspectSwordModule(moduleRoot: Path) {
    val configPath = moduleRoot.resolve("mods.d/engwebc2025eb.conf")
    require(Files.exists(configPath)) { "SWORD module configuration not found: $configPath" }
    val configLines = Files.readAllLines(configPath)
    val identifier = configLines.firstOrNull { it.trim().startsWith("[") && it.trim().endsWith("]") }
        ?.trim()
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?: "unknown"
    val metadata = configLines
        .asSequence()
        .map(String::trim)
        .filter { it.contains('=') && !it.startsWith("#") }
        .map { it.substringBefore('=') to it.substringAfter('=') }
        .toMap()

    println("module=${metadata["Abbreviation"] ?: "unknown"}")
    println("identifier=$identifier")
    println("description=${metadata["Description"] ?: "unknown"}")
    println("version=${metadata["Version"] ?: "unknown"}")
    println("version_date=${metadata["SwordVersionDate"] ?: "unknown"}")
    println("versification=${metadata["Versification"] ?: "unknown"}")
    println("text_source=${metadata["TextSource"] ?: "unknown"}")
    println("distribution_license=${metadata["DistributionLicense"] ?: "unknown"}")
    println("files:")

    Files.walk(moduleRoot).use { paths ->
        paths.filter(Files::isRegularFile)
            .sorted()
            .forEach { path ->
                val relative = moduleRoot.relativize(path)
                println("  ${relative.toString().replace('\\', '/')} sha256=${sha256(path)}")
            }
    }
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = input.read(buffer)
        while (read >= 0) {
            if (read > 0) digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun validate(path: Path): List<ValidationIssue> {
    if (!Files.exists(path)) return listOf(ValidationIssue(0, "file does not exist"))
    val lines = Files.readAllLines(path)
    if (lines.isEmpty() || lines.first() != EXPECTED_COLUMNS) {
        return listOf(ValidationIssue(1, "header must be exactly: $EXPECTED_COLUMNS"))
    }

    val issues = mutableListOf<ValidationIssue>()
    lines.drop(1).forEachIndexed { index, line ->
        val lineNumber = index + 2
        if (line.isBlank()) return@forEachIndexed
        val fields = parseCsvLine(line)
        if (fields.size != EXPECTED_COLUMNS.count { it == ',' } + 1) {
            issues += ValidationIssue(lineNumber, "expected 12 comma-separated columns")
            return@forEachIndexed
        }

        runCatching { LocalDate.parse(fields[0]) }
            .onFailure { issues += ValidationIssue(lineNumber, "invalid ISO date '${fields[0]}'") }
        if (fields[1] != "SACBC") issues += ValidationIssue(lineNumber, "calendar_scope must be SACBC")
        if (fields[10] != "https://ordo.jesuitssouthern.africa/ordo_full.php") {
            issues += ValidationIssue(lineNumber, "source_url must identify the Ordo source")
        }
        if (fields[11] != "needs_review" && fields[11] != "reviewed") {
            issues += ValidationIssue(lineNumber, "review_status must be needs_review or reviewed")
        }
    }
    return issues
}
