package me.mudkip.moememos.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoLinkTest {
    @Test
    fun extractMemoLinks_basicWikilink() {
        val markdown = "see [[memos/123]] for details"

        assertEquals(listOf("memos/123"), extractMemoLinks(markdown))
    }

    @Test
    fun extractMemoLinks_multipleLinksPreserveOrderAndDeduplicate() {
        val markdown = """
            [[memos/1]] then [[memos/2]] and again [[memos/1]]
        """.trimIndent()

        assertEquals(listOf("memos/1", "memos/2"), extractMemoLinks(markdown))
    }

    @Test
    fun extractMemoLinks_trimsSurroundingWhitespace() {
        assertEquals(listOf("memos/42"), extractMemoLinks("[[ memos/42 ]]"))
    }

    @Test
    fun extractMemoLinks_excludesLinksInsideCode() {
        val markdown = """
            `[[memos/inline]]`

            ```kotlin
            val ref = "[[memos/fenced]]"
            ```

            [[memos/real]]
        """.trimIndent()

        assertEquals(listOf("memos/real"), extractMemoLinks(markdown))
    }

    @Test
    fun extractMemoLinks_excludesLinksInsideLinkDestinations() {
        val markdown = """
            [docs](https://example.com/[[memos/url]])
            [[memos/real]]
        """.trimIndent()

        assertEquals(listOf("memos/real"), extractMemoLinks(markdown))
    }

    @Test
    fun extractMemoLinks_emptyOrMalformedInput() {
        assertEquals(emptyList<String>(), extractMemoLinks(""))
        assertEquals(emptyList<String>(), extractMemoLinks("no links here"))
        assertEquals(emptyList<String>(), extractMemoLinks("[[ ]]")) // blank target only
        assertEquals(emptyList<String>(), extractMemoLinks("[[unclosed"))
        assertEquals(emptyList<String>(), extractMemoLinks("[[multi\nline]]")) // newline not allowed
    }
}
