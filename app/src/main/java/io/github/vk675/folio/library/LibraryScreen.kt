package io.github.vk675.folio.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.vk675.folio.R
import io.github.vk675.folio.reader.ReadingPosition
import io.github.vk675.folio.ui.components.AppBackground
import io.github.vk675.folio.ui.components.Panel
import io.github.vk675.folio.ui.components.PanelButton
import io.github.vk675.folio.ui.theme.FolioTheme
import io.github.vk675.folio.ui.theme.RimBottom
import io.github.vk675.folio.ui.theme.RimTop
import java.io.File

private val CoverShape = RoundedCornerShape(20.dp)
private val CoverRim = Brush.verticalGradient(listOf(RimTop, RimBottom))
private val CoverScrim = Color(0x99000000)   // black at 60%, behind labels drawn over a cover
private const val COVER_ASPECT = 0.7f        // width / height of a typical book cover

// The "stateful" half: it owns the ViewModel and the file picker,
// and passes plain data and callbacks down to LibraryContent.
@Composable
fun LibraryScreen(
    onOpenBook: (Uri) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    // Runs every time this screen comes to the front: on launch, on return from a book,
    // on return from another app. The block after it runs when the screen goes away.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // The system file picker (Storage Access Framework). OpenMultipleDocuments lets the user
    // tick several files at once; the lambda receives the chosen URIs (empty if cancelled).
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.add(uris)
    }

    LibraryContent(
        sections = sections,
        onOpenBook = onOpenBook,
        onRemoveBook = viewModel::remove,
        // FB2 has no reliable MIME type, so we show all files and check the name ourselves.
        onAddBooks = { picker.launch(arrayOf("*/*")) },
        onOpenPlayer = onOpenPlayer,
        modifier = modifier
    )
}

// The "stateless" half: it only draws what it is given.
// That is why the previews below can show it without a ViewModel.
@Composable
private fun LibraryContent(
    sections: List<LibrarySection>?,   // null while the library is loading
    onOpenBook: (Uri) -> Unit,
    onRemoveBook: (Uri) -> Unit,
    onAddBooks: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding() // keep content clear of the status bar, notch and gesture bar
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(12.dp))

        // weight(1f) gives this part all the height the title and the buttons leave free.
        val contentModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        when {
            sections == null -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
            }

            sections.isEmpty() -> Box(contentModifier, contentAlignment = Alignment.Center) {
                EmptyLibraryCard()
            }

            else -> BookGrid(
                sections = sections,
                onOpenBook = onOpenBook,
                onRemoveBook = onRemoveBook,
                modifier = contentModifier
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelButton(
                text = stringResource(R.string.add_books),
                onClick = onAddBooks,
                primary = true
            )
            PanelButton(
                text = stringResource(R.string.open_player),
                onClick = onOpenPlayer
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BookGrid(
    sections: List<LibrarySection>,
    onOpenBook: (Uri) -> Unit,
    onRemoveBook: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    // A lazy grid with two columns. Each section is a full-width heading
    // followed by its books, two per row.
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        for (section in sections) {
            // A lone group of books without a series needs no heading.
            if (section.title != null || sections.size > 1) {
                item(
                    key = "section:${section.title}",
                    span = { GridItemSpan(maxLineSpan) }   // stretch across both columns
                ) {
                    Text(
                        text = section.title ?: stringResource(R.string.other_books),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            // key tells Compose which card is which when the list changes.
            items(section.books, key = { it.uri.toString() }) { book ->
                BookCard(
                    book = book,
                    onOpen = { onOpenBook(book.uri) },
                    onRemove = { onRemoveBook(book.uri) }
                )
            }
        }
    }
}

@Composable
private fun BookCard(
    book: BookFile,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(CoverShape)
            .clickable(onClick = onOpen)
    ) {
        Box {
            val coverModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT)
            if (book.coverPath != null) {
                // AsyncImage (the Coil library) loads and scales the picture on a background
                // thread. Crop fills the whole card and cuts off what does not fit.
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = null,   // decorative: the title is written below
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier
                        .clip(CoverShape)
                        .border(1.dp, CoverRim, CoverShape)
                )
            } else {
                Panel(modifier = coverModifier, shape = CoverShape) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
            }

            // The volume number on the cover: several volumes can share one picture.
            if (book.volume != null) {
                Text(
                    text = book.volume.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(CoverScrim)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (book.removable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CoverScrim)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.remove_book),
                            onClick = onRemove
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // "?.let { }" runs the block only when lastRead is not null.
            book.lastRead?.let { position ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = position.chapterTitle
                        ?: stringResource(R.string.chapter_untitled, position.chapter + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.library_empty),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.library_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
private fun LibraryEmptyPreview() {
    FolioTheme {
        AppBackground {
            LibraryContent(
                sections = emptyList(),
                onOpenBook = {},
                onRemoveBook = {},
                onAddBooks = {},
                onOpenPlayer = {}
            )
        }
    }
}

@Preview
@Composable
private fun LibraryWithBooksPreview() {
    FolioTheme {
        AppBackground {
            LibraryContent(
                sections = listOf(
                    LibrarySection(
                        title = "A series",
                        books = listOf(
                            BookFile(
                                uri = Uri.parse("content://preview/1"),
                                title = "First volume",
                                series = "A series",
                                volume = 1,
                                coverPath = null,
                                removable = false,
                                lastRead = ReadingPosition(11, 0, 0, "Chapter 12. The road")
                            ),
                            BookFile(
                                uri = Uri.parse("content://preview/2"),
                                title = "Second volume with a rather long title",
                                series = "A series",
                                volume = 2,
                                coverPath = null,
                                removable = true,
                                lastRead = null
                            )
                        )
                    )
                ),
                onOpenBook = {},
                onRemoveBook = {},
                onAddBooks = {},
                onOpenPlayer = {}
            )
        }
    }
}
