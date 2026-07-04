package io.github.linreal.cascade.screens.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.screens.TitledEditorTopBar
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.ui.PageScaffold

/**
 * Comments sample — showcases [io.github.linreal.cascade.editor.ui.CascadeEditor]
 * as a chat-style comment composer. A feed of rich-text bubbles sits above a
 * composer whose formatting bar fades in on focus; Send appends the comment and
 * clears focus.
 */
@Composable
fun CommentsScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
    val model = remember { CommentsScreenModel() }

    val listState = rememberLazyListState()
    LaunchedEffect(model.comments.size) {
        if (model.comments.isNotEmpty()) {
            listState.animateScrollToItem(model.comments.lastIndex)
        }
    }

    PageScaffold {
        TitledEditorTopBar(
            title = "Comments",
            isDark = isDark,
            onBack = onBack,
            onToggleTheme = onToggleTheme,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = FeedBottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items = model.comments, key = { it.id }) { comment ->
                    CommentBubble(comment)
                }
            }

            CommentComposer(
                model = model,
                editorTheme = editorTheme,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

private val FeedBottomInset = 150.dp

@Composable
private fun CommentBubble(comment: Comment) {
    val bubbleColor: Color
    val textColor: Color
    val inlineCodeBackground: Color
    val linkColor: Color
    if (comment.isOwn) {
        bubbleColor = MaterialTheme.colorScheme.primary
        textColor = MaterialTheme.colorScheme.onPrimary
        inlineCodeBackground = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
        linkColor = MaterialTheme.colorScheme.onPrimary
    } else {
        bubbleColor = MaterialTheme.colorScheme.surface
        textColor = MaterialTheme.colorScheme.onSurface
        inlineCodeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        linkColor = MaterialTheme.colorScheme.primary
    }

    val annotated = rememberCommentAnnotatedString(
        text = comment.text,
        spans = comment.spans,
        inlineCodeBackground = inlineCodeBackground,
        linkColor = linkColor,
    )

    val bubbleShape = if (comment.isOwn) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (comment.isOwn) Arrangement.End else Arrangement.Start,
    ) {
        if (comment.isOwn) {
            Spacer(modifier = Modifier.widthIn(min = 40.dp))
        } else {
            Avatar(comment)
            Spacer(modifier = Modifier.size(11.dp))
        }

        Column(
            horizontalAlignment = if (comment.isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = comment.timestamp,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = annotated,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = textColor,
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }

        if (comment.isOwn) {
            Spacer(modifier = Modifier.size(11.dp))
            Avatar(comment)
        } else {
            Spacer(modifier = Modifier.widthIn(min = 40.dp))
        }
    }
}

@Composable
private fun Avatar(comment: Comment) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(comment.avatarColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = comment.initials,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
