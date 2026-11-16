package za.co.hawkiesza.lectiosa.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ContentDao {
    @Query("SELECT COUNT(*) FROM verse_unit")
    suspend fun verseCount(): Int

    @Query("SELECT COUNT(*) FROM calendar_day")
    suspend fun calendarDayCount(): Int

    @Query("SELECT * FROM content_metadata ORDER BY key")
    suspend fun metadata(): List<ContentMetadataEntity>

    @Query("SELECT * FROM calendar_day WHERE date = :date AND calendar_scope = :scope LIMIT 1")
    suspend fun day(date: String, scope: String): CalendarDayEntity?

    @Query("SELECT * FROM reading_assignment WHERE date = :date AND calendar_scope = :scope ORDER BY sequence, id")
    suspend fun assignments(date: String, scope: String): List<ReadingAssignmentEntity>

    @Query(
        "SELECT * FROM verse_unit WHERE edition_id = :editionId AND book_code = :book " +
            "AND chapter = :chapter AND native_verse_label = :verse ORDER BY sort_order",
    )
    suspend fun verse(
        editionId: String,
        book: String,
        chapter: Int,
        verse: String,
    ): List<VerseUnitEntity>

    @Query(
        "SELECT * FROM verse_unit WHERE edition_id = :editionId AND book_code = :book " +
            "AND chapter = :chapter ORDER BY sort_order",
    )
    suspend fun chapter(
        editionId: String,
        book: String,
        chapter: Int,
    ): List<VerseUnitEntity>
}
