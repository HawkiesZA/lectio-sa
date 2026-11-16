package za.co.hawkiesza.lectiosa.content

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ContentMetadataEntity::class,
        EditionEntity::class,
        VerseUnitEntity::class,
        CalendarDayEntity::class,
        ReadingAssignmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        fun create(context: Context): ContentDatabase =
            Room.databaseBuilder(context, ContentDatabase::class.java, "lectio-content.db")
                .createFromAsset("content/lectio-content.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reading_assignment ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reading_assignment ADD COLUMN paired_psalm_citation TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE reading_assignment ADD COLUMN selection_rule TEXT NOT NULL DEFAULT 'obligatory'")
                database.execSQL("DROP INDEX IF EXISTS index_reading_assignment_date_calendar_scope_role")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reading_assignment_date_calendar_scope_sequence ON reading_assignment(date, calendar_scope, sequence)")
            }
        }
    }
}
