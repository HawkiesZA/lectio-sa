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
        MassFormEntity::class,
        ReadingAssignmentEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        fun create(context: Context): ContentDatabase =
            Room.databaseBuilder(context, ContentDatabase::class.java, "lectio-content.db")
                .createFromAsset("content/lectio-content.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE mass_form (date TEXT NOT NULL, calendar_scope TEXT NOT NULL, form_id TEXT NOT NULL, label TEXT NOT NULL, sort_order INTEGER NOT NULL, is_default INTEGER NOT NULL, source_url TEXT NOT NULL, review_status TEXT NOT NULL, PRIMARY KEY(date, calendar_scope, form_id), FOREIGN KEY(date, calendar_scope) REFERENCES calendar_day(date, calendar_scope) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("INSERT INTO mass_form(date, calendar_scope, form_id, label, sort_order, is_default, source_url, review_status) SELECT date, calendar_scope, 'default', CASE WHEN mass_form = '' THEN 'Mass of the day' ELSE mass_form END, 1, 1, '', review_status FROM reading_assignment GROUP BY date, calendar_scope")
                database.execSQL("CREATE TABLE reading_assignment_new (id INTEGER NOT NULL PRIMARY KEY, date TEXT NOT NULL, calendar_scope TEXT NOT NULL, mass_form_id TEXT NOT NULL, sequence INTEGER NOT NULL, role TEXT NOT NULL, mass_form TEXT NOT NULL, original_citation TEXT NOT NULL, paired_psalm_citation TEXT NOT NULL, selection_rule TEXT NOT NULL, review_status TEXT NOT NULL, FOREIGN KEY(date, calendar_scope) REFERENCES calendar_day(date, calendar_scope) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(date, calendar_scope, mass_form_id) REFERENCES mass_form(date, calendar_scope, form_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("INSERT INTO reading_assignment_new SELECT id, date, calendar_scope, 'default', sequence, role, mass_form, original_citation, paired_psalm_citation, selection_rule, review_status FROM reading_assignment")
                database.execSQL("DROP TABLE reading_assignment")
                database.execSQL("ALTER TABLE reading_assignment_new RENAME TO reading_assignment")
                database.execSQL("DROP INDEX IF EXISTS index_reading_assignment_date_calendar_scope_sequence")
                database.execSQL("CREATE INDEX index_reading_assignment_date_calendar_scope_mass_form_id_sequence ON reading_assignment(date, calendar_scope, mass_form_id, sequence)")
                database.execSQL("CREATE INDEX index_mass_form_date_calendar_scope_sort_order ON mass_form(date, calendar_scope, sort_order)")
            }
        }
    }
}
