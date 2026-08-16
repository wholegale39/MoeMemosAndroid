package me.mudkip.moememos.ui.page.memos

import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.util.extractCustomTags

/**
 * flomo-style content type filters for search.
 */
enum class SearchTypeFilter(val labelRes: Int) {
    ALL(R.string.search_type_all),
    IMAGE(R.string.search_type_image),
    LINK(R.string.search_type_link),
    ATTACHMENT(R.string.search_type_attachment),
    UNTAGGED(R.string.search_type_untagged);

    fun matches(memo: MemoEntity): Boolean = when (this) {
        ALL -> true
        IMAGE -> memo.resources.any { it.mimeType?.startsWith("image/", ignoreCase = true) == true }
        ATTACHMENT -> memo.resources.any { it.mimeType?.startsWith("image/", ignoreCase = true) != true }
        LINK -> linkPattern.containsMatchIn(memo.content)
        UNTAGGED -> extractCustomTags(memo.content).isEmpty()
    }

    private companion object {
        val linkPattern = Regex("https?://", RegexOption.IGNORE_CASE)
    }
}
