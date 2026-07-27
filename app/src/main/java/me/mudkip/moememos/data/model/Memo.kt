package me.mudkip.moememos.data.model

import java.time.Instant

enum class MemoVisibility {
    PRIVATE,
    PROTECTED,
    PUBLIC
}

data class Memo(
    override val remoteId: String,
    override val content: String,
    override val date: Instant,
    override val pinned: Boolean,
    override val visibility: MemoVisibility,
    override val resources: List<Resource>,
    val tags: List<String>,
    val creator: User? = null,
    override val archived: Boolean = false,
    val updatedAt: Instant? = null,
    val relations: List<MemoRelation> = emptyList(),
) : MemoRepresentable

/**
 * A directional link from one memo to another.
 *
 * [relatedMemoName] is the related memo's resource name, e.g. "memos/123".
 * [type] describes the nature of the connection (reference, comment, ...).
 * [memo] carries the full related memo when returned by the backend, otherwise null.
 */
data class MemoRelation(
    val relatedMemoName: String,
    val type: RelationType = RelationType.REFERENCE,
    val memo: Memo? = null
)

enum class RelationType {
    UNSPECIFIED,
    REFERENCE,
    COMMENT,
    ATTACHMENT
}
