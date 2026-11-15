package za.co.hawkiesza.lectiosa.content

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "content_metadata")
data class ContentMetadataEntity(
    @androidx.room.PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "edition")
data class EditionEntity(
    @androidx.room.PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "display_name")
    val displayName: String,
    @androidx.room.ColumnInfo(name = "source_module")
    val sourceModule: String,
    val versification: String,
    val attribution: String,
    @androidx.room.ColumnInfo(name = "rights_notice")
    val rightsNotice: String,
)

@Entity(
    tableName = "verse_unit",
    foreignKeys = [
        ForeignKey(
            entity = EditionEntity::class,
            parentColumns = ["id"],
            childColumns = ["edition_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("edition_id", "book_code", "chapter", "sort_order")],
)
data class VerseUnitEntity(
    @androidx.room.PrimaryKey val id: Long,
    @androidx.room.ColumnInfo(name = "edition_id") val editionId: String,
    @androidx.room.ColumnInfo(name = "book_code") val bookCode: String,
    val chapter: Int,
    @androidx.room.ColumnInfo(name = "native_verse_label") val nativeVerseLabel: String,
    @androidx.room.ColumnInfo(name = "sort_order") val sortOrder: Int,
    val text: String,
    @androidx.room.ColumnInfo(name = "osis_fragment") val osisFragment: String,
)

@Entity(
    tableName = "calendar_day",
    primaryKeys = ["date", "calendar_scope"],
    indices = [Index("date", "calendar_scope")],
)
data class CalendarDayEntity(
    val date: String,
    @androidx.room.ColumnInfo(name = "calendar_scope") val calendarScope: String,
    val celebration: String,
    val rank: String,
    @androidx.room.ColumnInfo(name = "liturgical_colour") val liturgicalColour: String,
    @androidx.room.ColumnInfo(name = "review_status") val reviewStatus: String,
    @androidx.room.ColumnInfo(name = "source_url") val sourceUrl: String,
)

@Entity(
    tableName = "reading_assignment",
    foreignKeys = [
        ForeignKey(
            entity = CalendarDayEntity::class,
            parentColumns = ["date", "calendar_scope"],
            childColumns = ["date", "calendar_scope"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date", "calendar_scope", "role")],
)
data class ReadingAssignmentEntity(
    @androidx.room.PrimaryKey val id: Long,
    val date: String,
    @androidx.room.ColumnInfo(name = "calendar_scope") val calendarScope: String,
    val role: String,
    @androidx.room.ColumnInfo(name = "mass_form") val massForm: String,
    @androidx.room.ColumnInfo(name = "original_citation") val originalCitation: String,
    @androidx.room.ColumnInfo(name = "review_status") val reviewStatus: String,
)
