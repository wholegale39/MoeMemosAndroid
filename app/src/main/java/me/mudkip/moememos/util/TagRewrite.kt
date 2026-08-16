package me.mudkip.moememos.util

/**
 * Matches the `#tag` text of [tag] inside memo content, including when it is
 * the parent of nested tags (`#tag/sub`) but not when it is a prefix of a
 * longer tag (`#tagLong`). Used by tag rename/remove, which rewrite the
 * embedded text because tags have no storage of their own.
 */
fun tagRewritePattern(tag: String): Regex {
    return Regex("${Regex.escape("#$tag")}(?=[\\s/]|$)")
}
