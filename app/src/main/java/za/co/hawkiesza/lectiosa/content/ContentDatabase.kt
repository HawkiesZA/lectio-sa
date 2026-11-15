package za.co.hawkiesza.lectiosa.content

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContentMetadataEntity::class,
        EditionEntity::class,
        VerseUnitEntity::class,
        CalendarDayEntity::class,
        ReadingAssignmentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        fun create(context: Context): ContentDatabase =
            Room.databaseBuilder(context, ContentDatabase::class.java, "lectio-content.db")
                .createFromAsset("content/lectio-content.db")
                .build()
    }
}
