package za.co.hawkiesza.lectiosa.content

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ResolvedReading(
    val sequence: Int,
    val role: String,
    val alternatives: List<ResolvedAlternative>,
    val pairedPsalmAlternatives: List<ResolvedAlternative>,
    val selectionRule: String,
    val reviewStatus: String,
)

data class ResolvedAlternative(
    val citation: String,
    val text: String,
    val fallbackNotice: String? = null,
    val exceptionNotice: String? = null,
    val verseLines: List<String> = emptyList(),
)

private data class CitationResolution(
    val text: String?,
    val verseLines: List<String>,
)

data class AvailableMassForm(
    val id: String,
    val label: String,
)

sealed interface DailyContentState {
    data object Loading : DailyContentState
    data object Unavailable : DailyContentState
    data class Available(
        val celebration: String,
        val reviewStatus: String,
        val forms: List<AvailableMassForm>,
        val selectedFormId: String,
        val readings: List<ResolvedReading>,
    ) : DailyContentState
}

class DailyContentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ContentDatabase.create(application)
    private val _content = MutableStateFlow<DailyContentState>(DailyContentState.Unavailable)
    val content: StateFlow<DailyContentState> = _content

    fun load(date: String, requestedFormId: String = "") {
        _content.value = DailyContentState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.contentDao()
            val day = dao.day(date, "SACBC")
            if (day == null) {
                _content.value = DailyContentState.Unavailable
                return@launch
            }
            val forms = dao.forms(date, "SACBC")
            val selectedForm = forms.firstOrNull { it.formId == requestedFormId }
                ?: forms.firstOrNull { it.isDefault == 1 }
                ?: forms.firstOrNull()
            if (selectedForm == null) {
                _content.value = DailyContentState.Unavailable
                return@launch
            }
            val readings = dao.assignments(date, "SACBC", selectedForm.formId).map { assignment ->
                val alternatives = splitAlternatives(assignment.originalCitation).map { alternative ->
                    val resolution = resolveCitation(dao, alternative)
                    ResolvedAlternative(
                        citation = alternative,
                        text = resolution.text ?: "Passage mapping pending for $alternative",
                        fallbackNotice = resolution.text?.let { partialVerseFallbackNotice(alternative) },
                        exceptionNotice = exceptionNotice(dao, alternative),
                        verseLines = resolution.verseLines,
                    )
                }
                val pairedPsalmAlternatives = splitAlternatives(assignment.pairedPsalmCitation)
                    .map { alternative ->
                        val resolution = resolveCitation(dao, alternative)
                        ResolvedAlternative(
                            citation = alternative,
                            text = resolution.text ?: "Passage mapping pending for $alternative",
                            fallbackNotice = resolution.text?.let { partialVerseFallbackNotice(alternative) },
                            exceptionNotice = exceptionNotice(dao, alternative),
                            verseLines = resolution.verseLines,
                        )
                    }
                ResolvedReading(
                    sequence = assignment.sequence,
                    role = assignment.role,
                    alternatives = alternatives,
                    pairedPsalmAlternatives = pairedPsalmAlternatives,
                    selectionRule = assignment.selectionRule,
                    reviewStatus = assignment.reviewStatus,
                )
            }
            _content.value = DailyContentState.Available(
                celebration = day.celebration,
                reviewStatus = day.reviewStatus,
                forms = forms.map { AvailableMassForm(it.formId, it.label) },
                selectedFormId = selectedForm.formId,
                readings = readings,
            )
        }
    }

    private suspend fun resolveCitation(dao: ContentDao, citation: String): CitationResolution {
        val parsed = parseCitation(citation) ?: return CitationResolution(null, emptyList())
        val book = bookAliasesForParsing.getValue(parsed.alias)
        val output = buildList {
            for (span in parsed.spans) {
                val initialUnits = dao.chapter("web-catholic", book, span.chapter)
                val end = span.end ?: initialUnits.maxOfOrNull {
                    it.nativeVerseLabel.takeWhile(Char::isDigit).toIntOrNull() ?: 0
                } ?: 0
                val references = (span.start..end).mapNotNull { citedVerse ->
                    mappedSourceReference(book, span.chapter, citedVerse)
                        ?: (span.chapter to citedVerse)
                }.groupBy({ it.first }, { it.second })
                for ((sourceChapter, citedVerses) in references) {
                    val units = if (sourceChapter == span.chapter) initialUnits else dao.chapter("web-catholic", book, sourceChapter)
                    val availableVerses = units.mapNotNull {
                        it.nativeVerseLabel.takeWhile(Char::isDigit).toIntOrNull()
                    }.toSet()
                    val targetVerses = citedVerses.flatMap { citedVerse ->
                        when {
                            citedVerse in availableVerses -> listOf(citedVerse)
                            book == "Psalms" && citedVerse > 1 && citedVerse - 1 in availableVerses -> listOf(citedVerse - 1)
                            else -> emptyList()
                        }
                    }.toSet()
                    addAll(units.filter { unit ->
                        val verse = unit.nativeVerseLabel.takeWhile(Char::isDigit).toIntOrNull()
                        verse != null && verse in targetVerses
                    })
                }
            }
        }
        val text = output.joinToString(" ") { it.text }.ifBlank { null }
        return CitationResolution(
            text = text,
            verseLines = if (book == "Psalms") psalmVerseLines(output.map { it.text }) else emptyList(),
        )
    }

    private suspend fun exceptionNotice(dao: ContentDao, citation: String): String? {
        val parsed = parseCitation(citation) ?: return null
        val book = bookAliasesForParsing.getValue(parsed.alias)
        for (span in parsed.spans) {
            val end = span.end ?: continue
            for (verse in span.start..end) {
                dao.exception("web-catholic", book, span.chapter, verse)?.let { return it.notice }
            }
        }
        return null
    }

    private companion object {
        val psalmCitationOffsets = mapOf(51 to 2, 52 to 2)

        val bookAliases = mapOf(
            "Gen" to "Genesis", "Genesis" to "Genesis", "Ex" to "Exodus", "Exod" to "Exodus", "Exodus" to "Exodus", "Lev" to "Leviticus", "Num" to "Numbers",
            "Deut" to "Deuteronomy", "Josh" to "Joshua", "Judg" to "Judges", "Ruth" to "Ruth",
            "1 Sam" to "I Samuel", "2 Sam" to "II Samuel", "1 Kgs" to "I Kings", "2 Kgs" to "II Kings",
            "1 Chr" to "I Chronicles", "2 Chr" to "II Chronicles", "Ezra" to "Ezra", "Neh" to "Nehemiah",
            "Tob" to "Tobit", "Jdt" to "Judith", "Esth" to "Esther", "1 Macc" to "I Maccabees",
            "2 Macc" to "II Maccabees", "Job" to "Job", "Ps" to "Psalms", "Prov" to "Proverbs",
            "Eccles" to "Ecclesiastes", "Song" to "Song of Solomon", "Wis" to "Wisdom", "Sir" to "Sirach",
            "Isa" to "Isaiah", "Isaiah" to "Isaiah", "Jer" to "Jeremiah", "Lam" to "Lamentations", "Bar" to "Baruch",
            "Ezek" to "Ezekiel", "Ezekiel" to "Ezekiel", "Dan" to "Daniel", "Hos" to "Hosea", "Joel" to "Joel",
            "Amos" to "Amos", "Obad" to "Obadiah", "Jonah" to "Jonah", "Mic" to "Micah",
            "Nah" to "Nahum", "Hab" to "Habakkuk", "Zeph" to "Zephaniah", "Hag" to "Haggai",
            "Zech" to "Zechariah", "Mal" to "Malachi", "Matt" to "Matthew", "Matthew" to "Matthew", "Mark" to "Mark",
            "Luke" to "Luke", "Lk" to "Luke", "John" to "John", "Jn" to "John", "Acts" to "Acts",
            "Rom" to "Romans", "1 Cor" to "I Corinthians", "2 Cor" to "II Corinthians",
            "Gal" to "Galatians", "Eph" to "Ephesians", "Phil" to "Philippians", "Col" to "Colossians",
            "1 Thess" to "I Thessalonians", "2 Thess" to "II Thessalonians", "1 Tim" to "I Timothy",
            "2 Tim" to "II Timothy", "Titus" to "Titus", "Phlm" to "Philemon", "Heb" to "Hebrews",
            "Jas" to "James", "1 Pet" to "I Peter", "2 Pet" to "II Peter", "1 John" to "I John",
            "2 John" to "II John", "3 John" to "III John", "Jude" to "Jude", "Rev" to "Revelation of John",
        )
    }
}

internal fun splitAlternatives(citation: String): List<String> =
    citation.split(Regex("\\s+or\\s+", RegexOption.IGNORE_CASE))
        .map(String::trim)
        .filter(String::isNotBlank)
        .let { alternatives ->
            val bookPrefix = bookAliasesForParsing.keys
                .sortedByDescending(String::length)
                .firstOrNull { alternatives.firstOrNull()?.startsWith("$it ") == true }
            if (bookPrefix == null) alternatives
            else alternatives.mapIndexed { index, alternative ->
                val alreadyHasBook = bookAliasesForParsing.keys.any { alternative.startsWith("$it ") }
                if (index > 0 && !alreadyHasBook && alternative.firstOrNull()?.isDigit() == true) "$bookPrefix $alternative" else alternative
            }
        }

internal data class CitationSpan(val chapter: Int, val start: Int, val end: Int?)

internal data class ParsedCitation(val alias: String, val spans: List<CitationSpan>)

internal fun mappedSourceReference(book: String, chapter: Int, verse: Int): Pair<Int, Int>? = when {
    book == "Romans" && chapter == 16 && verse >= 25 -> 14 to (verse - 1)
    book == "Malachi" && chapter == 3 && verse >= 19 -> 4 to (verse - 18)
    book == "Joel" && chapter == 4 -> 3 to verse
    book == "Hosea" && chapter == 14 && verse == 10 -> 14 to 9
    book == "Genesis" && chapter == 32 && verse == 33 -> 32 to 32
    book == "Jonah" && chapter == 2 && verse == 11 -> 2 to 10
    book == "Zechariah" && chapter == 2 && verse >= 14 -> 3 to (verse - 13)
    else -> null
}

internal fun partialVerseFallbackNotice(citation: String): String? =
    if (citation.split(Regex("\\s+"))
            .flatMap { it.replace('&', '+').split('+') }
            .any { Regex("\\d+[a-z]+", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    ) {
        "Whole verse included; the calendar selects part of this verse."
    } else {
        null
    }

internal fun psalmVerseLines(texts: List<String>): List<String> = texts

internal fun parseCitation(citation: String): ParsedCitation? {
    val normalizedCitation = normalizeSpecialCitation(citation)
    val alias = bookAliasesForParsing.keys
        .sortedByDescending(String::length)
        .firstOrNull { normalizedCitation.startsWith("$it ") }
        ?: return null
    val remainder = normalizedCitation.removePrefix(alias).trim()
    val tokens = remainder.split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return null

    var currentChapter: Int? = null
    val spans = mutableListOf<CitationSpan>()
    for (rawToken in tokens.flatMap { it.replace('&', '+').split('+') }) {
        val token = rawToken.trim().trim('(', ')', ',', '.', ';')
        if (token.isBlank() || token.equals("or", ignoreCase = true) || token.any(Char::isLetter) && !token.any(Char::isDigit)) continue
        val rangeParts = token.split('-', limit = 2)
        val startRef = parseReference(rangeParts[0], currentChapter) ?: continue
        currentChapter = startRef.first
        val endRef = rangeParts.getOrNull(1)?.let { parseReference(it, currentChapter) }
        if (rangeParts.size == 2 && endRef != null && endRef.first != startRef.first) {
            spans += CitationSpan(startRef.first, startRef.second, null)
            for (chapter in (startRef.first + 1) until endRef.first) spans += CitationSpan(chapter, 1, null)
            spans += CitationSpan(endRef.first, 1, endRef.second)
            currentChapter = endRef.first
        } else {
            spans += CitationSpan(startRef.first, startRef.second, endRef?.second ?: startRef.second)
        }
    }
    return spans.takeIf { it.isNotEmpty() }?.let { ParsedCitation(alias, it) }
}

private fun normalizeSpecialCitation(citation: String): String {
    val psalmNormalized = citation.replace(Regex("^(Ps(?:alms)?\\s+\\d+)\\s+\\(\\d+\\):"), "$1:")
    val greekEsther = Regex("^(Esth(?:er)?\\s+)C:(.*)$", RegexOption.IGNORE_CASE).matchEntire(psalmNormalized)
        ?: return psalmNormalized
    val mappedRemainder = greekEsther.groupValues[2].replace(Regex("\\d+")) { (it.value.toInt() + 17).toString() }
    return "${greekEsther.groupValues[1]}4:$mappedRemainder"
}

private fun parseReference(value: String, inheritedChapter: Int?): Pair<Int, Int>? {
    val cleaned = value.trim().trim('(', ')', ',', '.', ';')
    val parts = cleaned.split(':', limit = 2)
    val chapter = if (parts.size == 2) parts[0].toIntOrNull() else inheritedChapter
    val verse = (parts.lastOrNull() ?: "").takeWhile(Char::isDigit).toIntOrNull()
    return if (chapter != null && verse != null) chapter to verse else null
}

private val additionalBookAliasesForParsing = mapOf(
    "Leviticus" to "Leviticus", "Numbers" to "Numbers", "Deuteronomy" to "Deuteronomy", "Joshua" to "Joshua", "Judges" to "Judges",
    "1Samuel" to "I Samuel", "1 Samuel" to "I Samuel", "2Samuel" to "II Samuel", "2 Samuel" to "II Samuel", "1 Kings" to "I Kings", "2 Kings" to "II Kings",
    "1Chr" to "I Chronicles", "1 Chronicles" to "I Chronicles", "2Chr" to "II Chronicles", "2 Chronicles" to "II Chronicles", "Nehemiah" to "Nehemiah",
    "Tobit" to "Tobit", "Judith" to "Judith", "Esther" to "Esther", "1Macc" to "I Maccabees", "1 Maccabees" to "I Maccabees", "2Macc" to "II Maccabees", "2 Maccabees" to "II Maccabees",
    "Psalm" to "Psalms", "Psalms" to "Psalms", "Proverbs" to "Proverbs", "Eccl" to "Ecclesiastes", "Ecclesiastes" to "Ecclesiastes", "Song of Solomon" to "Song of Solomon", "Song of Songs" to "Song of Solomon",
    "Wisdom" to "Wisdom", "Sirach" to "Sirach", "Jeremiah" to "Jeremiah", "Lamentations" to "Lamentations", "Baruch" to "Baruch", "Daniel" to "Daniel", "Hosea" to "Hosea", "Obadiah" to "Obadiah", "Jon" to "Jonah", "Micah" to "Micah", "Nahum" to "Nahum", "Habakkuk" to "Habakkuk", "Zephaniah" to "Zephaniah", "Haggai" to "Haggai", "Zechariah" to "Zechariah", "Malachi" to "Malachi", "Is" to "Isaiah", "Tit" to "Titus",
    "Romans" to "Romans", "1Cor" to "I Corinthians", "1 Corinthians" to "I Corinthians", "2Cor" to "II Corinthians", "2 Corinthians" to "II Corinthians", "Galatians" to "Galatians", "Ephesians" to "Ephesians", "Philippians" to "Philippians", "Colossians" to "Colossians",
    "1Thess" to "I Thessalonians", "1 Thessalonians" to "I Thessalonians", "2Thess" to "II Thessalonians", "2 Thessalonians" to "II Thessalonians", "1Tim" to "I Timothy", "1 Timothy" to "I Timothy", "2Tim" to "II Timothy", "2 Timothy" to "II Timothy", "Philemon" to "Philemon", "Hebrews" to "Hebrews", "James" to "James", "1Pet" to "I Peter", "1 Peter" to "I Peter", "2Pet" to "II Peter", "2 Peter" to "II Peter", "1John" to "I John", "1 John" to "I John", "2John" to "II John", "2 John" to "II John", "3John" to "III John", "3 John" to "III John", "Revelation" to "Revelation of John",
)

private val bookAliasesForParsing = mapOf(
    "Gen" to "Genesis", "Genesis" to "Genesis", "Ex" to "Exodus", "Exod" to "Exodus", "Exodus" to "Exodus", "Lev" to "Leviticus", "Num" to "Numbers",
    "Deut" to "Deuteronomy", "Josh" to "Joshua", "Judg" to "Judges", "Ruth" to "Ruth",
    "1 Sam" to "I Samuel", "2 Sam" to "II Samuel", "1 Kgs" to "I Kings", "2 Kgs" to "II Kings",
    "1 Chr" to "I Chronicles", "2 Chr" to "II Chronicles", "Ezra" to "Ezra", "Neh" to "Nehemiah",
    "Tob" to "Tobit", "Jdt" to "Judith", "Esth" to "Esther", "1 Macc" to "I Maccabees",
    "2 Macc" to "II Maccabees", "Job" to "Job", "Ps" to "Psalms", "Prov" to "Proverbs",
    "Eccles" to "Ecclesiastes", "Song" to "Song of Solomon", "Wis" to "Wisdom", "Sir" to "Sirach",
    "Isa" to "Isaiah", "Isaiah" to "Isaiah", "Jer" to "Jeremiah", "Lam" to "Lamentations", "Bar" to "Baruch",
    "Ezek" to "Ezekiel", "Ezekiel" to "Ezekiel", "Dan" to "Daniel", "Hos" to "Hosea", "Joel" to "Joel",
    "Amos" to "Amos", "Obad" to "Obadiah", "Jonah" to "Jonah", "Mic" to "Micah",
    "Nah" to "Nahum", "Hab" to "Habakkuk", "Zeph" to "Zephaniah", "Hag" to "Haggai",
    "Zech" to "Zechariah", "Mal" to "Malachi", "Matt" to "Matthew", "Matthew" to "Matthew", "Mark" to "Mark",
    "Luke" to "Luke", "Lk" to "Luke", "John" to "John", "Jn" to "John", "Acts" to "Acts",
    "Rom" to "Romans", "1 Cor" to "I Corinthians", "2 Cor" to "II Corinthians",
    "Gal" to "Galatians", "Eph" to "Ephesians", "Phil" to "Philippians", "Col" to "Colossians",
    "1 Thess" to "I Thessalonians", "2 Thess" to "II Thessalonians", "1 Tim" to "I Timothy",
    "2 Tim" to "II Timothy", "Titus" to "Titus", "Phlm" to "Philemon", "Heb" to "Hebrews",
    "Jas" to "James", "1 Pet" to "I Peter", "2 Pet" to "II Peter", "1 John" to "I John",
    "2 John" to "II John", "3 John" to "III John", "Jude" to "Jude", "Rev" to "Revelation of John",
) + additionalBookAliasesForParsing
