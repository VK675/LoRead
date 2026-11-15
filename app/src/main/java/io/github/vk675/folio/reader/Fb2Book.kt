package io.github.vk675.folio.reader

// A parsed book held in memory.
// "data class" generates equals/hashCode/toString/copy for us, like a C# record.
data class Fb2Book(
    val title: String?,
    val author: String?,
    val chapters: List<Fb2Chapter>
)

data class Fb2Chapter(
    val title: String?,          // null when the file gives the chapter no title
    val paragraphs: List<String>
)

// What the library needs to show a book without loading its text.
// A plain class, not a data class: a ByteArray does not compare by content,
// so the generated equals() would be misleading.
class Fb2Info(
    val title: String?,
    val author: String?,
    val series: String?,     // <sequence name="...">, e.g. "Теневой раб"
    val volume: Int?,        // <sequence number="...">
    val cover: ByteArray?    // the cover picture file as it is stored in the book (JPEG or PNG)
)
