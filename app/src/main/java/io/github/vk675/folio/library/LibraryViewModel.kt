package io.github.vk675.folio.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vk675.folio.reader.ReadingPosition
import io.github.vk675.folio.reader.ReadingPositionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// A book in the library, ready to be drawn.
data class BookFile(
    val uri: Uri,
    val title: String,               // from the book itself, or the file name if it has none
    val series: String?,
    val volume: Int?,
    val coverPath: String?,
    val removable: Boolean,          // false for files in the app's own folder
    val lastRead: ReadingPosition?   // null if the book was never opened
)

// A group of books under one heading: a series, or null for books without one.
data class LibrarySection(
    val title: String?,
    val books: List<BookFile>
)

// The library has no database yet. Books come from two places:
//
// 1. The app's own folder on shared storage:
//      Android/data/io.github.vk675.folio/files/books
//    The app can read it without asking, so any FB2 file copied there shows up by itself.
//
// 2. Files picked with "Add books". Android remembers which files the user gave us
//    long-term access to ("persisted URI permissions"), and we list those.
//
// Stage 2 replaces this with Room.
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val positions = ReadingPositionStore(application)
    private val infos = BookInfoStore(application)

    // null means "still loading": the first run reads every book file once.
    private val _sections = MutableStateFlow<List<LibrarySection>?>(null)
    val sections: StateFlow<List<LibrarySection>?> = _sections.asStateFlow()

    // A book file found on the phone, before we know anything about its contents.
    private class FoundFile(
        val uri: Uri,
        val fileName: String,
        val stamp: String,
        val removable: Boolean
    )

    fun add(uris: List<Uri>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    if (!isFb2(pickedFileName(uri))) continue
                    try {
                        // Without this the access ends when the app is closed.
                        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: SecurityException) {
                        // This file provider does not offer long-term access; skip the file.
                    }
                }
            }
            refresh()
        }
    }

    // Removes a picked book from the library only. The file itself stays on the phone.
    fun remove(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // The permission is already gone; nothing to release.
        }
        refresh()
    }

    // Called every time the library screen comes to the front, so new files
    // and fresh reading positions show up without restarting the app.
    fun refresh() {
        viewModelScope.launch {
            _sections.value = withContext(Dispatchers.IO) { loadSections() }
        }
    }

    private fun loadSections(): List<LibrarySection> {
        // The same file can be both in the app folder and picked by hand.
        // distinctBy keeps the first of each name, and the app folder comes first.
        val books = (filesInAppFolder() + pickedFiles())
            .distinctBy { it.fileName }
            .map { toBook(it) }

        return books
            .groupBy { it.series }
            .map { (series, volumes) ->
                LibrarySection(
                    title = series,
                    // By volume number; books without a number go last, by title.
                    books = volumes.sortedWith(
                        compareBy<BookFile>({ it.volume ?: Int.MAX_VALUE }, { it.title.lowercase() })
                    )
                )
            }
            // Series by name; the group without a series goes to the end.
            .sortedWith(compareBy<LibrarySection>({ it.title == null }, { it.title?.lowercase() }))
    }

    private fun toBook(file: FoundFile): BookFile {
        val info = infos.get(file.uri, file.stamp)
        return BookFile(
            uri = file.uri,
            title = info.title ?: file.fileName.removeSuffix(".zip").removeSuffix(".fb2"),
            series = info.series,
            volume = info.volume,
            coverPath = info.coverPath,
            removable = file.removable,
            lastRead = positions.load(file.uri.toString())
        )
    }

    private fun filesInAppFolder(): List<FoundFile> {
        // getExternalFilesDir also creates the folder on first use.
        val folder = getApplication<Application>().getExternalFilesDir("books") ?: return emptyList()
        return folder.listFiles().orEmpty()
            .filter { it.isFile && isFb2(it.name) }
            .map { file ->
                FoundFile(
                    uri = Uri.fromFile(file),
                    fileName = file.name,
                    stamp = "${file.length()}:${file.lastModified()}",
                    removable = false
                )
            }
    }

    private fun pickedFiles(): List<FoundFile> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { FoundFile(it.uri, pickedFileName(it.uri), stamp = "picked", removable = true) }
            .filter { isFb2(it.fileName) }

    private fun isFb2(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".fb2") || lower.endsWith(".fb2.zip")
    }

    // Asks the file provider for the name shown to the user. A content:// URI is not a path,
    // so the name has to be queried, like a row from a database table.
    private fun pickedFileName(uri: Uri): String {
        val queried = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null   // file deleted or access lost
        }
        // "?:" is the Elvis operator, the same as "??" in C#.
        return queried ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    }
}
