package app.sift.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [NoteEntity::class, NoteRelationEntity::class, DailyUsageEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SiftDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun usageDao(): UsageDao
}
