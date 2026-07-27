package me.mudkip.moememos.ui.component

import android.net.Uri
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import me.mudkip.moememos.util.findCustomTagMatches
import me.mudkip.moememos.util.findMemoLinkMatches
import me.mudkip.moememos.util.getCustomTagName
import me.mudkip.moememos.util.getMemoLinkTarget
import me.mudkip.moememos.util.isCustomTagSupportedNode
import kotlin.text.MatchResult
import org.intellij.markdown.MarkdownTokenTypes
import com.mikepenz.markdown.m3.Markdown as M3Markdown

@Composable
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    imageBaseUrl: String? = null,
    checkboxChange: ((checked: Boolean, startOffset: Int, endOffset: Int) -> Unit)? = null,
    selectable: Boolean = false,
    onTagClick: ((tag: String) -> Unit)? = null,
    onMemoLinkClick: ((target: String) -> Unit)? = null,
) {
    fun withOptionalTextAlign(style: TextStyle): TextStyle {
        return if (textAlign == null) style else style.copy(textAlign = textAlign)
    }

    val bodyTextStyle = withOptionalTextAlign(MaterialTheme.typography.bodyLarge)
    val h1TextStyle = withOptionalTextAlign(MaterialTheme.typography.headlineLarge)
    val h2TextStyle = withOptionalTextAlign(MaterialTheme.typography.headlineMedium)
    val h3TextStyle = withOptionalTextAlign(MaterialTheme.typography.headlineSmall)
    val h4TextStyle = withOptionalTextAlign(MaterialTheme.typography.titleLarge)
    val h5TextStyle = withOptionalTextAlign(MaterialTheme.typography.titleMedium)
    val h6TextStyle = withOptionalTextAlign(MaterialTheme.typography.titleSmall)
    val uriHandler = LocalUriHandler.current
    val tagLinkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    )
    val tagLinkListener = remember(uriHandler, onTagClick) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            if (url.startsWith(TAG_LINK_PREFIX)) {
                onTagClick?.invoke(Uri.decode(url.removePrefix(TAG_LINK_PREFIX)))
                return@LinkInteractionListener
            }
            uriHandler.openUri(url)
        }
    }
    val memoLinkPrimary = MaterialTheme.colorScheme.primary
    val memoLinkStyle = remember(memoLinkPrimary) {
        TextLinkStyles(
            style = SpanStyle(
                color = memoLinkPrimary,
                textDecoration = TextDecoration.Underline,
            )
        )
    }
    val memoLinkListener = remember(uriHandler, onMemoLinkClick) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            if (url.startsWith(MEMO_LINK_PREFIX)) {
                onMemoLinkClick?.invoke(Uri.decode(url.removePrefix(MEMO_LINK_PREFIX)))
                return@LinkInteractionListener
            }
            if (url.startsWith(TAG_LINK_PREFIX)) {
                onTagClick?.invoke(Uri.decode(url.removePrefix(TAG_LINK_PREFIX)))
                return@LinkInteractionListener
            }
            uriHandler.openUri(url)
        }
    }
    val imageTransformer = remember(imageBaseUrl) {
        object : ImageTransformer {
            @Composable
            override fun transform(link: String): ImageData {
                return Coil3ImageTransformerImpl.transform(resolveMarkdownImageLink(link, imageBaseUrl))
            }

            @Composable
            override fun intrinsicSize(painter: Painter): Size {
                return Coil3ImageTransformerImpl.intrinsicSize(painter)
            }
        }
    }
    val markdownState = rememberMarkdownState(
        content = text,
        retainState = true
    )

    data class LinkSpan(
        val start: Int,
        val endInclusive: Int,
        val kind: SpanKind,
        val match: MatchResult
    )

    val markdownContent: @Composable () -> Unit = {
        M3Markdown(
            markdownState = markdownState,
            modifier = modifier,
            imageTransformer = imageTransformer,
            typography = markdownTypography(
                h1 = h1TextStyle,
                h2 = h2TextStyle,
                h3 = h3TextStyle,
                h4 = h4TextStyle,
                h5 = h5TextStyle,
                h6 = h6TextStyle,
                text = bodyTextStyle,
                paragraph = bodyTextStyle,
                ordered = bodyTextStyle,
                bullet = bodyTextStyle,
                list = bodyTextStyle
            ),
            annotator = markdownAnnotator(
                config = markdownAnnotatorConfig(eolAsNewLine = true),
                annotate = { content, child ->
                    if (child.type != MarkdownTokenTypes.TEXT) {
                        return@markdownAnnotator false
                    }
                    if (!isCustomTagSupportedNode(child)) {
                        return@markdownAnnotator false
                    }
                    val source = child.getUnescapedTextInNode(content)
                    val tagMatches = findCustomTagMatches(source).toList()
                    val memoMatches = findMemoLinkMatches(source).toList()
                    if (tagMatches.isEmpty() && memoMatches.isEmpty()) {
                        return@markdownAnnotator false
                    }

                    val spans = buildList {
                        tagMatches.forEach { add(LinkSpan(it.range.first, it.range.last, SpanKind.TAG, it)) }
                        memoMatches.forEach { add(LinkSpan(it.range.first, it.range.last, SpanKind.MEMO, it)) }
                    }.sortedBy { it.start }

                    var cursor = 0
                    spans.forEach { span ->
                        if (span.start > cursor) {
                            append(source.substring(cursor, span.start))
                        }
                        when (span.kind) {
                            SpanKind.TAG -> {
                                val tagRaw = getCustomTagName(span.match)
                                withLink(
                                    LinkAnnotation.Url(
                                        url = TAG_LINK_PREFIX + Uri.encode(tagRaw),
                                        styles = tagLinkStyle,
                                        linkInteractionListener = tagLinkListener
                                    )
                                ) {
                                    append(span.match.value)
                                }
                            }
                            SpanKind.MEMO -> {
                                val target = getMemoLinkTarget(span.match)
                                withLink(
                                    LinkAnnotation.Url(
                                        url = MEMO_LINK_PREFIX + Uri.encode(target),
                                        styles = memoLinkStyle,
                                        linkInteractionListener = memoLinkListener
                                    )
                                ) {
                                    append(span.match.value)
                                }
                            }
                        }
                        cursor = span.endInclusive + 1
                    }
                    if (cursor < source.length) {
                        append(source.substring(cursor))
                    }
                    true
                }
            ),
            components = markdownComponents(
                codeFence = highlightedCodeFence,
                codeBlock = highlightedCodeBlock,
                checkbox = {
                    val node = it.node
                    MarkdownCheckBox(
                        content = it.content,
                        node = it.node,
                        style = it.typography.text,
                        checkedIndicator = { checked, modifier ->
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = if (checkboxChange != null) {
                                        { checkboxChange(!checked, node.startOffset, node.endOffset) }
                                    } else {
                                        null
                                    },
                                    modifier = modifier.semantics {
                                        role = Role.Checkbox
                                        stateDescription = if (checked) "Checked" else "Unchecked"
                                    },
                                )
                            }
                        }
                    )
                }
            )
        )
    }

    if (selectable) {
        SelectionContainer {
            markdownContent()
        }
    } else {
        markdownContent()
    }
}

private fun resolveMarkdownImageLink(link: String, imageBaseUrl: String?): String {
    val uri = link.toUri()
    if (uri.scheme != null || imageBaseUrl.isNullOrBlank()) {
        return link
    }
    return imageBaseUrl.toUri().buildUpon().path(link).build().toString()
}

private const val TAG_LINK_PREFIX = "moememos://tag/"
private const val MEMO_LINK_PREFIX = "moememos://memo/"

private enum class SpanKind { TAG, MEMO }
