package app.sift.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val category: String,
    val tags: List<String>,
    val sourceApp: String?,
    val rawImagePath: String?,
    val reviewCount: Int,
    val lastReviewedAt: Long?,
)

@Entity(tableName = "note_relations")
data class NoteRelationEntity(
    @PrimaryKey val id: String,
    val fromNoteId: String,
    val toNoteId: String,
    val reason: String,
)

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val date: String,
    val scrollMillis: Long,
    val capturedCount: Int,
    val keptCount: Int,
)
