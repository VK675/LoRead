package io.github.vk675.folio.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

enum class ReaderError { FILE_UNAVAILABLE, NOT_A_BOOK }

// Everything the reader screen can show. A sealed interface is a closed set of variants:
// the compiler knows all of them, so a "when" over the state must handle each one.
sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Failed(val error: ReaderError) : ReaderUiState
    data class Ready(
        val book: Fb2Book,
        val openChapter: Int?,     // null: show the chapter list; a number: show that chapter
        val lastChapter: Int?,     // the chapter read most recently, marked in the list
        val startItem: Int = 0,    // where to scroll when openChapter is first shown
        val startOffset: Int = 0
    ) : ReaderUiState
}

// AndroidViewModel is a ViewModel that also holds the Application,
// which we need to read files through ContentResolver.
class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val positions = ReadingPositionStore(application)

    // Private and mutable inside, public and read-only outside:
    // only the ViewModel changes the state, the screen just observes it.
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var loadedUri: Uri? = null

    fun open(uri: Uri) {
        if (uri == loadedUri) return   // already loaded, e.g. after a screen rotation
        loadedUri = uri
        _state.value = ReaderUiState.Loading

        // A coroutine tied to this ViewModel: it is cancelled when the screen is closed for good.
        viewModelScope.launch {
            // Parsing a 5 MB file takes a moment, so it runs on a background thread.
            // withContext is like "await Task.Run(...)" in C#.
            _state.value = withContext(Dispatchers.IO) { load(uri) }
        }
    }

    private fun load(uri: Uri): ReaderUiState {
        return try {
            val resolver = getApplication<Application>().contentResolver
            // "use" closes the stream when the block ends, like "using" in C#.
            val book = resolver.openInputStream(uri)?.use { Fb2Parser.parse(it) }
            when {
                book == null -> ReaderUiState.Failed(ReaderError.FILE_UNAVAILABLE)
                book.chapters.isEmpty() -> ReaderUiState.Failed(ReaderError.NOT_A_BOOK)
                else -> {
                    // Continue where the reader stopped, if we have a position for this book.
                    val saved = positions.load(uri.toString())
                        ?.takeIf { it.chapter in book.chapters.indices }
                    ReaderUiState.Ready(
                        book = book,
                        openChapter = saved?.chapter,
                        lastChapter = saved?.chapter,
                        startItem = saved?.item ?: 0,
                        startOffset = saved?.offset ?: 0
                    )
                }
            }
        } catch (e: XmlPullParserException) {
            ReaderUiState.Failed(ReaderError.NOT_A_BOOK)
        } catch (e: Exception) {
            // File moved or deleted, permission revoked, read error: file providers
            // report these with different exception types, so we treat them alike.
            ReaderUiState.Failed(ReaderError.FILE_UNAVAILABLE)
        }
    }

    // The user picked a chapter (from the list, or with Previous/Next): show it from its top.
    fun openChapter(index: Int) {
        _state.update { current ->
            if (current is ReaderUiState.Ready && index in current.book.chapters.indices) {
                current.copy(openChapter = index, lastChapter = index, startItem = 0, startOffset = 0)
            } else {
                current
            }
        }
        savePosition(chapter = index, item = 0, offset = 0)
    }

    fun closeChapter() {
        _state.update { current ->
            if (current is ReaderUiState.Ready) current.copy(openChapter = null) else current
        }
    }

    // Called by the screen every time scrolling stops.
    // It only writes to storage; the screen already knows its own scroll position.
    fun savePosition(chapter: Int, item: Int, offset: Int) {
        val uri = loadedUri ?: return
        val book = (_state.value as? ReaderUiState.Ready)?.book ?: return
        val title = book.chapters.getOrNull(chapter)?.title
        positions.save(uri.toString(), ReadingPosition(chapter, item, offset, title))
    }
}
