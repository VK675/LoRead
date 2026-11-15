package io.github.vk675.folio.library

import android.content.Context
import android.net.Uri
import io.github.vk675.folio.reader.Fb2Parser
import org.json.JSONObject
import java.io.File

// Title, series and cover of one book, as shown in the library.
class BookInfo(
    val title: String?,
    val author: String?,
    val series: String?,
    val volume: Int?,
    val coverPath: String?   // path of the cover picture saved in the app's private storage
)

// Reading a book's description means going through the whole 5 MB file (the cover sits
// at its very end), so we do it once per book and remember the result:
// the text fields as JSON in SharedPreferences, the cover as a file in files/covers.
// Stage 2 moves this into Room.
class BookInfoStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("book_info", Context.MODE_PRIVATE)
    private val coverFolder = File(context.filesDir, "covers")

    // "stamp" describes the file version (size and modification time).
    // If the file is replaced, the stamp changes and the book is read again.
    fun get(uri: Uri, stamp: String): BookInfo {
        val key = uri.toString()
        loadCached(key, stamp)?.let { return it }

        val info = read(uri, key)
        val json = JSONObject()
            .put("stamp", stamp)
            .put("title", info.title)       // put() with a null value simply leaves the key out
            .put("author", info.author)
            .put("series", info.series)
            .put("volume", info.volume)
            .put("cover", info.coverPath)
        prefs.edit().putString(key, json.toString()).apply()
        return info
    }

    private fun loadCached(key: String, stamp: String): BookInfo? {
        val saved = prefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(saved)
            if (json.optString("stamp") != stamp) return null
            val coverPath = json.stringOrNull("cover")
            // The cover file can disappear if the app's data is partly cleared.
            if (coverPath != null && !File(coverPath).exists()) return null
            BookInfo(
                title = json.stringOrNull("title"),
                author = json.stringOrNull("author"),
                series = json.stringOrNull("series"),
                volume = if (json.has("volume")) json.getInt("volume") else null,
                coverPath = coverPath
            )
        } catch (e: Exception) {
            null   // unreadable entry: read the book again
        }
    }

    private fun read(uri: Uri, key: String): BookInfo {
        return try {
            val fb2 = context.contentResolver.openInputStream(uri)?.use { Fb2Parser.readInfo(it) }
                ?: return BookInfo(null, null, null, null, null)

            // "?.let { }" runs the block only when there is a cover, and returns its result.
            val coverPath = fb2.cover?.let { bytes ->
                coverFolder.mkdirs()
                // A short file name made from the book's address.
                val file = File(coverFolder, key.hashCode().toUInt().toString(16) + ".img")
                file.writeBytes(bytes)
                file.path
            }
            BookInfo(fb2.title, fb2.author, fb2.series, fb2.volume, coverPath)
        } catch (e: Exception) {
            // Broken or missing file: show it by file name, without a cover.
            BookInfo(null, null, null, null, null)
        }
    }

    // An extension function: adds a method to JSONObject without changing the class,
    // the same idea as "static string StringOrNull(this JSONObject json, ...)" in C#.
    private fun JSONObject.stringOrNull(name: String): String? =
        if (has(name)) getString(name) else null
}
