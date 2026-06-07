package app.sift.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: String): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE notes SET reviewCount = reviewCount + 1, lastReviewedAt = :at WHERE id = :id")
    suspend fun markReviewed(id: String, at: Long)

    @Query("UPDATE notes SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: String, embedding: ByteArray)

    @Query("SELECT * FROM notes WHERE embedding IS NOT NULL")
    suspend fun allWithEmbedding(): List<NoteEntity>

    @Query("SELECT DISTINCT category FROM notes WHERE category != '' ORDER BY category")
    suspend fun categories(): List<String>

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :q || '%'
           OR summary LIKE '%' || :q || '%'
           OR category LIKE '%' || :q || '%'
           OR tags LIKE '%' || :q || '%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(q: String, limit: Int): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRelation(relation: NoteRelationEntity)

    @Query("SELECT * FROM note_relations WHERE fromNoteId = :id OR toNoteId = :id")
    suspend fun relationsOf(id: String): List<NoteRelationEntity>
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT :days")
    fun observeRecent(days: Int): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun get(date: String): DailyUsageEntity?

    @Upsert
    suspend fun upsert(usage: DailyUsageEntity)
}
