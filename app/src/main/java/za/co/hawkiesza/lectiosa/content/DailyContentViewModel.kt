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
                    ResolvedAlternative(
                        citation = alternative,
                        text = resolveCitation(dao, alternative)
                            ?: "Passage mapping pending for $alternative",
                    )
                }
                val pairedPsalmAlternatives = splitAlternatives(assignment.pairedPsalmCitation)
                    .map { alternative ->
                        ResolvedAlternative(
                            citation = alternative,
                            text = resolveCitation(dao, alternative)
                                ?: "Passage mapping pending for $alternative",
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

    private suspend fun resolveCitation(dao: ContentDao, citation: String): String? {
        val parsed = parseCitation(citation) ?: return null
        val book = bookAliasesForParsing.getValue(parsed.alias)
        val output = buildList {
            for (span in parsed.spans) {
                val units = dao.chapter("web-catholic", book, span.chapter)
                val end = span.end ?: units.maxOfOrNull {
                    it.nativeVerseLabel.takeWhile(Char::isDigit).toIntOrNull() ?: 0
                } ?: 0
                addAll(units.filter { unit ->
                    val verse = unit.nativeVerseLabel.takeWhile(Char::isDigit).toIntOrNull()
                    verse != null && verse in span.start..end
                })
            }
        }
        return output.joinToString(" ") { it.text }.ifBlank { null }
    }

    private companion object {
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
            else alternatives.map { alternative ->
                if (alternative.firstOrNull()?.isDigit() == true) "$bookPrefix $alternative" else alternative
            }
        }

internal data class CitationSpan(val chapter: Int, val start: Int, val end: Int?)

internal data class ParsedCitation(val alias: String, val spans: List<CitationSpan>)

internal fun parseCitation(citation: String): ParsedCitation? {
    val alias = bookAliasesForParsing.keys
        .sortedByDescending(String::length)
        .firstOrNull { citation.startsWith("$it ") }
        ?: return null
    val remainder = citation.removePrefix(alias).trim()
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

private fun parseReference(value: String, inheritedChapter: Int?): Pair<Int, Int>? {
    val cleaned = value.trim().trim('(', ')', ',', '.', ';')
    val parts = cleaned.split(':', limit = 2)
    val chapter = if (parts.size == 2) parts[0].toIntOrNull() else inheritedChapter
    val verse = (parts.lastOrNull() ?: "").takeWhile(Char::isDigit).toIntOrNull()
    return if (chapter != null && verse != null) chapter to verse else null
}

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
)
