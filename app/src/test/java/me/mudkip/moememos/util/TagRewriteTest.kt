package me.mudkip.moememos.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TagRewriteTest {
    private fun rename(content: String, old: String, new: String): String =
        content.replace(tagRewritePattern(old), "#$new")

    private fun remove(content: String, tag: String): String =
        content.replace(tagRewritePattern(tag), "")

    @Test
    fun renamesExactTag() {
        assertEquals("#书籍 note", rename("#读书 note", "读书", "书籍"))
        assertEquals("note #书籍", rename("note #读书", "读书", "书籍"))
    }

    @Test
    fun renamesParentOfNestedTags() {
        assertEquals("#书籍/小说", rename("#读书/小说", "读书", "书籍"))
    }

    @Test
    fun doesNotMatchLongerTagPrefix() {
        assertEquals("#读书笔记", rename("#读书笔记", "读书", "书籍"))
    }

    @Test
    fun doesNotMatchInsideOtherTagText() {
        assertEquals("see #我的读书清单 end", rename("see #我的读书清单 end", "读书", "书籍"))
    }

    @Test
    fun onlyMatchesTagTokenNotPlainWord() {
        assertEquals("读书 content", rename("读书 content", "读书", "书籍"))
    }

    @Test
    fun renamesMultipleOccurrences() {
        assertEquals("#a and #a and #a/x", rename("#old and #old and #old/x", "old", "a"))
    }

    @Test
    fun removesTagKeepsText() {
        // The tag token itself is removed; surrounding whitespace is kept as-is.
        assertEquals("hello  world", remove("hello #todo world", "todo"))
        assertEquals("clean", remove("clean", "todo"))
        assertEquals("#other kept", remove("#other kept", "todo"))
    }

    @Test
    fun handlesTagWithRegexSpecialChars() {
        assertEquals("#cpp rocks", rename("#c++ rocks", "c++", "cpp"))
    }
}
