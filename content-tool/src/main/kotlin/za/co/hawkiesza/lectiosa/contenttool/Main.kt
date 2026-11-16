package za.co.hawkiesza.lectiosa.contenttool

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate

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
        require(fields[0] == "Psalms" && fields[1] == "119") {
            "source overlay may only contain Psalms chapter 119"
        }
        val verse = fields[2].toIntOrNull()
        require(verse != null && verse in 1..176) {
            "Psalm 119 source verse must be between 1 and 176"
        }
        require(fields[3].isNotBlank() && !fields[3].contains('\t') && !fields[3].contains('\n')) {
            "Psalm 119 source text must be a non-empty single TSV field"
        }
        fields
    }.sortedBy { it[2].toInt() }

    require(overlay.map { it[2].toInt() } == (1..176).toList()) {
        "Psalm 119 source overlay must contain exactly one row for every verse 1-176"
    }
    require(overlay.all { it[3].isNotBlank() }) { "Psalm 119 source text must not be blank" }

    val rows = renderedRows.drop(1).filter(String::isNotBlank).map { it.split('\t', limit = 5) }
    require(rows.all { it.size == 5 }) { "rendered input row must contain five tab-separated fields" }
    val withoutPsalm119 = rows.filterNot { it[0] == "Psalms" && it[1] == "119" && it[2] != "0" }
    val insertionIndex = withoutPsalm119.indexOfFirst { it[0] == "Psalms" && it[1] == "119" }
    require(insertionIndex >= 0) { "rendered input has no Psalms 119 structural row" }
    val replacement = overlay.map { listOf(it[0], it[1], it[2], it[3], "source-override:ebible.org/eng-web-c/PSA119.htm") }
    val outputRows = withoutPsalm119.toMutableList().apply { addAll(insertionIndex + 1, replacement) }

    outputPath.parent?.let(Files::createDirectories)
    Files.writeString(outputPath, buildString {
        appendLine(renderedHeader)
        outputRows.forEach { appendLine(it.joinToString("\t")) }
    })
    println("WROTE $outputPath (Psalm 119 source overlay applied)")
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

private fun compileDatabase(renderedPath: Path, calendarDir: Path, output: Path) {
    require(Files.exists(renderedPath)) { "rendered input not found: $renderedPath" }
    require(Files.isDirectory(calendarDir)) { "calendar directory not found: $calendarDir" }
    require(Files.exists(Path.of("content/schema.sql"))) { "content/schema.sql not found" }
    output.parent?.let(Files::createDirectories)
    Files.deleteIfExists(output)

    val sqlFile = Files.createTempFile(output.parent ?: Path.of("."), "lectio-compile-", ".sql")
    try {
        Files.newBufferedWriter(sqlFile).use { sql ->
            sql.appendLine("PRAGMA foreign_keys = ON;")
            sql.appendLine(Files.readString(Path.of("content/schema.sql")))
            sql.appendLine("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
            sql.appendLine("INSERT INTO room_master_table(id, identity_hash) VALUES (42, '870ba2fde235d6e79df432c9714035e6');")
            sql.appendLine("BEGIN;")
            sql.appendLine("INSERT INTO content_metadata(key, value) VALUES " +
                "('schema_version', '1'), " +
                "('source_module', 'engwebc2025eb'), " +
                "('rendered_source_sha256', " + sqlString(sha256(renderedPath)) + ");")
            sql.appendLine("INSERT INTO edition(id, display_name, source_module, versification, attribution, rights_notice) VALUES " +
                "('web-catholic', 'World English Bible Catholic', 'engwebc2025eb', 'Vulg', " +
                sqlString("eBible.org World English Bible Catholic") + ", " +
                sqlString("Public-domain Scripture text; preserve the publisher attribution and notice.") + ");")

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
            Files.list(calendarDir).use { files ->
                files.filter { it.fileName.toString().matches(Regex("\\d{4}\\.csv")) }.sorted().forEach { calendarPath ->
                    Files.readAllLines(calendarPath).drop(1).filter(String::isNotBlank).forEach { line ->
                        val fields = parseCsvLine(line)
                        require(fields.size == 12) { "calendar row in $calendarPath must contain 12 fields" }
                        sql.appendLine(
                            "INSERT INTO calendar_day(date, calendar_scope, celebration, rank, liturgical_colour, review_status, source_url) VALUES (" +
                                fields.take(5).joinToString(",") { sqlString(it) } + ", " +
                                sqlString(fields[11]) + ", " + sqlString(fields[10]) + ");",
                        )
                        listOf(
                            "first_reading" to fields[6],
                            "psalm" to fields[7],
                            "second_reading" to fields[8],
                            "gospel" to fields[9],
                        ).filter { it.second.isNotBlank() }.forEach { (role, citation) ->
                            sql.appendLine(
                                "INSERT INTO reading_assignment(id, date, calendar_scope, role, mass_form, original_citation, review_status) VALUES (" +
                                    assignmentId + ", " + sqlString(fields[0]) + ", " + sqlString(fields[1]) + ", " +
                                    sqlString(role) + ", " + sqlString(fields[5]) + ", " + sqlString(citation) + ", " +
                                    sqlString(fields[11]) + ");",
                            )
                            assignmentId++
                        }
                    }
                }
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
