package app.sift.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [NoteEntity::class, NoteRelationEntity::class, DailyUsageEntity::class],
    version = 2, // v2: notes 增加 embedding 列（RAG）
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SiftDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun usageDao(): UsageDao
}
