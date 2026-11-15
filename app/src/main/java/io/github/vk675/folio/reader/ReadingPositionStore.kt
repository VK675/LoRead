package io.github.vk675.folio.reader

import android.content.Context

// Where the reader stopped in one book.
data class ReadingPosition(
    val chapter: Int,            // index in Fb2Book.chapters
    val item: Int,               // first visible row of the chapter's list
    val offset: Int,             // how many pixels of that row are scrolled off the top
    val chapterTitle: String?    // kept so the library can show it without parsing the book
)

// Remembers the reading position of every book on this phone. No account is involved:
// SharedPreferences is a small key-value file in the app's private storage.
// Stage 2 moves this into Room together with the rest of the library.
class ReadingPositionStore(context: Context) {

    private val prefs = context.getSharedPreferences("reading_positions", Context.MODE_PRIVATE)

    // bookKey is the book's URI as text. One book = one line "chapter|item|offset|title".
    fun load(bookKey: String): ReadingPosition? {
        val saved = prefs.getString(bookKey, null) ?: return null
        // limit = 4: a "|" inside the title stays part of the title
        val parts = saved.split("|", limit = 4)
        if (parts.size < 4) return null
        return ReadingPosition(
            // toIntOrNull returns null instead of throwing, like int.TryParse in C#
            chapter = parts[0].toIntOrNull() ?: return null,
            item = parts[1].toIntOrNull() ?: 0,
            offset = parts[2].toIntOrNull() ?: 0,
            chapterTitle = parts[3].ifEmpty { null }
        )
    }

    fun save(bookKey: String, position: ReadingPosition) {
        val line = with(position) { "$chapter|$item|$offset|${chapterTitle.orEmpty()}" }
        // apply() writes to disk in the background, so it is safe to call while scrolling
        prefs.edit().putString(bookKey, line).apply()
    }
}
