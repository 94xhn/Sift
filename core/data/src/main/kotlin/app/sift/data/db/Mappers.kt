package app.sift.data.db

import app.sift.domain.model.DailyUsage
import app.sift.domain.model.KnowledgeNote
import app.sift.domain.model.NoteRelation

fun NoteEntity.toDomain() = KnowledgeNote(
    id = id,
    createdAt = createdAt,
    title = title,
    summary = summary,
    keyPoints = keyPoints,
    category = category,
    tags = tags,
    sourceApp = sourceApp,
    rawImagePath = rawImagePath,
    reviewCount = reviewCount,
    lastReviewedAt = lastReviewedAt,
)

fun KnowledgeNote.toEntity() = NoteEntity(
    id = id,
    createdAt = createdAt,
    title = title,
    summary = summary,
    keyPoints = keyPoints,
    category = category,
    tags = tags,
    sourceApp = sourceApp,
    rawImagePath = rawImagePath,
    reviewCount = reviewCount,
    lastReviewedAt = lastReviewedAt,
)

fun NoteRelationEntity.toDomain() = NoteRelation(id, fromNoteId, toNoteId, reason)
fun NoteRelation.toEntity() = NoteRelationEntity(id, fromNoteId, toNoteId, reason)

fun DailyUsageEntity.toDomain() = DailyUsage(date, scrollMillis, capturedCount, keptCount)
