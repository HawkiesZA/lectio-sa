package za.co.hawkiesza.lectiosa.content

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ResolvedReading(
    val role: String,
    val citation: String,
    val text: String,
    val reviewStatus: String,
)

sealed interface DailyContentState {
    data object Loading : DailyContentState
    data object Unavailable : DailyContentState
    data class Available(
        val celebration: String,
        val reviewStatus: String,
        val readings: List<ResolvedReading>,
    ) : DailyContentState
}

class DailyContentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ContentDatabase.create(application)
    private val _content = MutableStateFlow<DailyContentState>(DailyContentState.Unavailable)
    val content: StateFlow<DailyContentState> = _content

    fun load(date: String) {
        _content.value = DailyContentState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.contentDao()
            val day = dao.day(date, "SACBC")
            if (day == null) {
                _content.value = DailyContentState.Unavailable
                return@launch
            }
            val readings = dao.assignments(date, "SACBC").map { assignment ->
                val resolved = resolveCitation(dao, assignment.originalCitation)
                ResolvedReading(
                    role = assignment.role,
                    citation = assignment.originalCitation,
                    text = resolved ?: "Passage mapping pending for ${assignment.originalCitation}",
                    reviewStatus = assignment.reviewStatus,
                )
            }
            _content.value = DailyContentState.Available(day.celebration, day.reviewStatus, readings)
        }
    }

    private suspend fun resolveCitation(dao: ContentDao, citation: String): String? {
        val alias = bookAliases.keys.sortedByDescending(String::length).firstOrNull { citation.startsWith("$it ") }
            ?: return null
        val remainder = citation.removePrefix(alias).trim()
        val chapterNumber = remainder.substringBefore(':').toIntOrNull() ?: return null
        val verseSpec = remainder.substringAfter(':', "")
        if (verseSpec.isBlank() || verseSpec.contains(';')) return null
        val requestedVerses = verseSpec.split(' ')
            .filter(String::isNotBlank)
            .flatMap { token ->
                val range = token.split('-')
                val start = range.first().takeWhile(Char::isDigit).toIntOrNull() ?: return@flatMap emptyList()
                val end = range.getOrNull(1)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: start
                (start..end).toList()
            }
            .toSet()
        if (requestedVerses.isEmpty()) return null
        val units = dao.chapter("web-catholic", bookAliases.getValue(alias), chapterNumber)
        return units.filter { unit ->
            unit.nativeVerseLabel.takeWhile(Char::isDigit).toIntOrNull() in requestedVerses
        }.joinToString(" ") { it.text }.ifBlank { null }
    }

    private companion object {
        val bookAliases = mapOf(
            "Gen" to "Genesis", "Ex" to "Exodus", "Lev" to "Leviticus", "Num" to "Numbers",
            "Deut" to "Deuteronomy", "Josh" to "Joshua", "Judg" to "Judges", "Ruth" to "Ruth",
            "1 Sam" to "I Samuel", "2 Sam" to "II Samuel", "1 Kgs" to "I Kings", "2 Kgs" to "II Kings",
            "1 Chr" to "I Chronicles", "2 Chr" to "II Chronicles", "Ezra" to "Ezra", "Neh" to "Nehemiah",
            "Tob" to "Tobit", "Jdt" to "Judith", "Esth" to "Esther", "1 Macc" to "I Maccabees",
            "2 Macc" to "II Maccabees", "Job" to "Job", "Ps" to "Psalms", "Prov" to "Proverbs",
            "Eccles" to "Ecclesiastes", "Song" to "Song of Solomon", "Wis" to "Wisdom", "Sir" to "Sirach",
            "Isa" to "Isaiah", "Isaiah" to "Isaiah", "Jer" to "Jeremiah", "Lam" to "Lamentations", "Bar" to "Baruch",
            "Ezek" to "Ezekiel", "Dan" to "Daniel", "Hos" to "Hosea", "Joel" to "Joel",
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
