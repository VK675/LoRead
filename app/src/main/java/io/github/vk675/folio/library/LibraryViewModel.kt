package io.github.vk675.folio.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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

// The library has no database yet. Books come from three places:
//
// 1. The app's own folder on shared storage:
//      Android/data/io.github.vk675.folio/files/books
//    The app can read it without asking, so any FB2 file copied there shows up by itself.
//
// 2. A folder the user linked with "Folder" (for example Download or Documents/Books).
//    Every FB2 in it and its subfolders shows up by itself, so a downloaded book
//    appears without any extra step.
//
// 3. Files picked one by one with "Add books".
//
// For 2 and 3 Android remembers what the user gave us long-term access to
// ("persisted URI permissions"), and we list those. A folder grant is a "tree" URI,
// a single file grant is a document URI; DocumentsContract.isTreeUri tells them apart.
//
// Stage 2 replaces this with Room.
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val positions = ReadingPositionStore(application)
    private val infos = BookInfoStore(application)

    // null means "still loading": the first run reads every book file once.
    private val _sections = MutableStateFlow<List<LibrarySection>?>(null)
    val sections: StateFlow<List<LibrarySection>?> = _sections.asStateFlow()

    // The linked folder as a readable path, e.g. "Download/Books"; null if none is linked.
    private val _folderName = MutableStateFlow<String?>(null)
    val folderName: StateFlow<String?> = _folderName.asStateFlow()

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

    // Links a folder chosen in the system picker. Only one folder at a time:
    // choosing a new one unlinks the old one.
    fun linkFolder(tree: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (old in linkedFolders()) {
                    if (old != tree) release(old)
                }
                try {
                    resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // This provider does not offer long-term access to the folder.
                }
            }
            refresh()
        }
    }

    // Removes a picked book from the library only. The file itself stays on the phone.
    fun remove(uri: Uri) {
        release(uri)
        refresh()
    }

    private fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // The permission is already gone; nothing to release.
        }
    }

    // Called every time the library screen comes to the front, so new files
    // and fresh reading positions show up without restarting the app.
    fun refresh() {
        viewModelScope.launch {
            _sections.value = withContext(Dispatchers.IO) { loadSections() }
            _folderName.value = linkedFolders().firstOrNull()?.let { folderLabel(it) }
        }
    }

    private fun loadSections(): List<LibrarySection> {
        // The same file can be both in the app folder and picked by hand.
        // distinctBy keeps the first of each name, and the app folder comes first.
        val books = (filesInAppFolder() + filesInLinkedFolders() + pickedFiles())
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

    private fun linkedFolders(): List<Uri> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }

    private fun filesInLinkedFolders(): List<FoundFile> =
        linkedFolders().flatMap { tree ->
            // The folder itself is a "document" too; listing starts from its id.
            listFolder(tree, DocumentsContract.getTreeDocumentId(tree), depth = 0)
        }

    // Lists one folder through the file provider and goes into subfolders.
    // Like pickedFileName below, this is a database-style query: one row per child.
    private fun listFolder(tree: Uri, folderId: String, depth: Int): List<FoundFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val found = mutableListOf<FoundFile>()
        try {
            resolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val isFolder = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (isFolder) {
                        // A depth limit, in case someone links the root of the whole phone.
                        if (depth < MAX_DEPTH) found += listFolder(tree, id, depth + 1)
                    } else if (isFb2(name)) {
                        found += FoundFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            fileName = name,
                            // Size and date change when the file changes, so the cached
                            // title and cover are read again only then.
                            stamp = "${c.getLong(3)}:${c.getLong(4)}",
                            removable = false   // to drop it, link another folder
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // The folder was deleted or access was lost: show what we have.
        }
        return found
    }

    // "primary:Download/Books" -> "Download/Books". The part before ":" is the storage
    // ("primary" = internal memory); an empty path means the storage root.
    private fun folderLabel(tree: Uri): String {
        val path = DocumentsContract.getTreeDocumentId(tree).substringAfter(':')
        return path.ifEmpty { "/" }
    }

    private fun pickedFiles(): List<FoundFile> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && !DocumentsContract.isTreeUri(it.uri) }
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

    private companion object {
        const val MAX_DEPTH = 4
    }
}
