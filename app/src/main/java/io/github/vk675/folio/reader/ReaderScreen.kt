package io.github.vk675.folio.reader

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vk675.folio.R
import kotlinx.coroutines.flow.filter
import io.github.vk675.folio.ui.components.Panel
import io.github.vk675.folio.ui.components.PanelButton
import io.github.vk675.folio.ui.components.PanelIconButton

private val ChapterRowShape = RoundedCornerShape(20.dp)

// One destination, two views: the chapter list and the text of an opened chapter.
// Which one is shown depends on ReaderUiState.Ready.openChapter.
@Composable
fun ReaderScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = viewModel()
) {
    // Runs once when the screen appears (and again only if uri changes).
    LaunchedEffect(uri) { viewModel.open(uri) }

    // Turns the StateFlow into Compose state: every new value redraws the screen.
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Created here, above the "when", so the list keeps its scroll position
    // while a chapter is open and the list itself is not on screen.
    val chapterListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // "val current = state" lets the compiler smart-cast it inside each branch:
        // in the Ready branch "current" is known to be Ready, no cast needed.
        when (val current = state) {
            ReaderUiState.Loading -> {
                Header(title = "", onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
            }

            is ReaderUiState.Failed -> {
                Header(title = "", onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(
                            when (current.error) {
                                ReaderError.FILE_UNAVAILABLE -> R.string.reader_error_unavailable
                                ReaderError.NOT_A_BOOK -> R.string.reader_error_format
                            }
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is ReaderUiState.Ready -> {
                val book = current.book
                val index = current.openChapter
                if (index == null) {
                    Header(title = book.title.orEmpty(), onBack = onBack)
                    Spacer(Modifier.height(16.dp))
                    ChapterList(
                        book = book,
                        lastChapter = current.lastChapter,
                        listState = chapterListState,
                        onOpenChapter = viewModel::openChapter
                    )
                } else {
                    // While a chapter is open, the system Back gesture returns to the list
                    // instead of leaving the book.
                    BackHandler { viewModel.closeChapter() }
                    Header(title = book.title.orEmpty(), onBack = viewModel::closeChapter)
                    Spacer(Modifier.height(16.dp))
                    ChapterText(
                        chapter = book.chapters[index],
                        index = index,
                        hasPrevious = index > 0,
                        hasNext = index < book.chapters.lastIndex,
                        startItem = current.startItem,
                        startOffset = current.startOffset,
                        onScrollStopped = { item, offset -> viewModel.savePosition(index, item, offset) },
                        onOpenChapter = viewModel::openChapter
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelIconButton(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.back),
            onClick = onBack
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun chapterTitle(chapter: Fb2Chapter, index: Int): String =
    chapter.title
        ?: if (index == 0) stringResource(R.string.chapter_intro)
        else stringResource(R.string.chapter_untitled, index + 1)

@Composable
private fun ChapterList(
    book: Fb2Book,
    lastChapter: Int?,
    listState: LazyListState,
    onOpenChapter: (Int) -> Unit
) {
    // Each time the list appears, make sure the chapter read last is on screen.
    LaunchedEffect(Unit) {
        if (lastChapter != null && listState.layoutInfo.visibleItemsInfo.none { it.index == lastChapter }) {
            listState.scrollToItem((lastChapter - 2).coerceAtLeast(0))
        }
    }

    // LazyColumn composes only the rows that are on screen,
    // so a list of 460 chapters costs the same as a list of 10.
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(book.chapters) { index, chapter ->
            Panel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ChapterRowShape)
                    .clickable { onOpenChapter(index) },
                shape = ChapterRowShape
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chapterTitle(chapter, index),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                    // A dot marks the chapter where reading stopped.
                    if (index == lastChapter) {
                        Text(
                            text = "●",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterText(
    chapter: Fb2Chapter,
    index: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    startItem: Int,
    startOffset: Int,
    onScrollStopped: (item: Int, offset: Int) -> Unit,
    onOpenChapter: (Int) -> Unit
) {
    // remember(index) makes a fresh scroll state for every chapter. It starts at the saved
    // position when the book is reopened, and at the top when the user picks a chapter.
    val listState = remember(index) { LazyListState(startItem, startOffset) }

    // snapshotFlow turns Compose state into a Flow. Here it emits whenever scrolling
    // starts or stops; we keep the "stopped" moments and report where the list is.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .collect {
                onScrollStopped(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = chapterTitle(chapter, index),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
        items(chapter.paragraphs) { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (hasPrevious) {
                    PanelButton(
                        text = stringResource(R.string.previous_chapter),
                        onClick = { onOpenChapter(index - 1) }
                    )
                } else {
                    Spacer(Modifier.width(1.dp))   // keeps "Next" on the right side
                }
                if (hasNext) {
                    PanelButton(
                        text = stringResource(R.string.next_chapter),
                        onClick = { onOpenChapter(index + 1) },
                        primary = true
                    )
                }
            }
        }
    }
}
