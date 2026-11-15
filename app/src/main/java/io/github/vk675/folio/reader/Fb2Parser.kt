package io.github.vk675.folio.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipInputStream

// Reads an FB2 file (plain or zipped).
//
// FB2 is XML. The parts we care about:
//   <description><title-info>
//       <book-title>, <author>, <sequence name="Series" number="8"/>,
//       <coverpage><image l:href="#cover.jpg"/></coverpage>
//   <body> <section> <title><p>Chapter name</p></title> <p>text</p> ... </section> ... </body>
//   <binary id="cover.jpg"> the picture as base64 text </binary>
//
// XmlPullParser walks the file as a stream of events (START_TAG, TEXT, END_TAG),
// so we never build the whole XML tree in memory. In C# terms it is XmlReader, not XDocument.
//
// "object" declares a singleton: one instance, no constructor, like a C# static class.
object Fb2Parser {

    private val whitespace = Regex("\\s+")

    // Some files put every chapter in one <section> and mark the chapters
    // with ordinary paragraphs such as "Глава 1591: Годы спустя".
    private val chapterHeading = Regex("^(Глава|Chapter)\\s+\\d+.{0,200}$", RegexOption.IGNORE_CASE)

    private val authorNameTags = setOf("first-name", "middle-name", "last-name", "nickname")
    private val titleLineTags = setOf("p")

    // What <title-info> tells us. A private helper type used only inside the parser.
    private class TitleInfo(
        val title: String? = null,
        val author: String? = null,
        val series: String? = null,
        val volume: Int? = null,
        val coverId: String? = null
    )

    // The whole book: used by the reader.
    // The parser is a parameter with a default value, so a unit test on the computer
    // can pass its own parser instead of the Android one.
    fun parse(raw: InputStream, parser: XmlPullParser = Xml.newPullParser()): Fb2Book {
        // null encoding: the parser takes it from the <?xml encoding="..."?> line itself
        parser.setInput(unzipIfNeeded(raw), null)

        var info = TitleInfo()
        val sections = mutableListOf<Fb2Chapter>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title-info" -> info = readTitleInfo(parser)
                    // Only the main body is the book; <body name="notes"> holds footnotes.
                    "body" -> if (parser.getAttributeValue(null, "name") == null) {
                        readBody(parser, sections)
                    } else {
                        skip(parser)
                    }
                    "binary" -> skip(parser)
                }
            }
            event = parser.next()
        }

        return Fb2Book(
            title = info.title,
            author = info.author,
            chapters = sections.flatMap { splitByHeadings(it) }
        )
    }

    // Only the description and the cover: used by the library, which does not need the text.
    fun readInfo(raw: InputStream, parser: XmlPullParser = Xml.newPullParser()): Fb2Info {
        parser.setInput(unzipIfNeeded(raw), null)

        var info = TitleInfo()
        var cover: ByteArray? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && cover == null) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title-info" -> info = readTitleInfo(parser)
                    "body" -> skip(parser)
                    "binary" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val type = parser.getAttributeValue(null, "content-type").orEmpty()
                        // The picture named by <coverpage>; if the file names none, the first picture.
                        val isCover = if (info.coverId != null) id == info.coverId else type.startsWith("image")
                        if (isCover) cover = decodeBase64(readRawText(parser)) else skip(parser)
                    }
                }
            }
            event = parser.next()
        }

        return Fb2Info(info.title, info.author, info.series, info.volume, cover)
    }

    // A zip file always starts with the bytes "PK". If we see them,
    // we read the first file inside the archive instead of the archive itself.
    private fun unzipIfNeeded(raw: InputStream): InputStream {
        val input = BufferedInputStream(raw)
        input.mark(2)
        val isZip = input.read() == 'P'.code && input.read() == 'K'.code
        input.reset()
        if (!isZip) return input

        val zip = ZipInputStream(input)
        zip.nextEntry ?: throw IOException("Empty archive")
        return zip
    }

    private fun decodeBase64(text: String): ByteArray? = try {
        // The MIME decoder ignores the line breaks FB2 files put inside base64.
        Base64.getMimeDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }

    // The helpers below share one rule: they are called when the parser stands
    // on a START_TAG and return when it stands on the matching END_TAG.
    // "depth" counts how many unclosed tags we are inside.

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    // All text inside the current element exactly as written.
    private fun readRawText(parser: XmlPullParser): String {
        val text = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return text.toString()
    }

    // Readable text of the current element, including text in nested <strong>, <emphasis>, <a>.
    // Line breaks and indentation inside the XML are not part of the text, so they are collapsed.
    private fun readText(parser: XmlPullParser): String =
        readRawText(parser).replace(whitespace, " ").trim()

    // Texts of the direct children with the given names, e.g. every <p> inside <title>.
    private fun readChildTexts(parser: XmlPullParser, names: Set<String>): List<String> {
        val parts = mutableListOf<String>()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG ->
                    if (parser.name in names) {
                        val part = readText(parser)
                        if (part.isNotEmpty()) parts += part
                    } else {
                        depth++
                    }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return parts
    }

    private fun readTitleInfo(parser: XmlPullParser): TitleInfo {
        var title: String? = null
        var author: String? = null
        var series: String? = null
        var volume: Int? = null
        var coverId: String? = null

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "book-title" -> title = readText(parser).ifEmpty { null }
                    "author" -> {
                        val name = readChildTexts(parser, authorNameTags).joinToString(" ")
                        if (author == null && name.isNotEmpty()) author = name
                    }
                    "sequence" -> {
                        // <sequence name="Теневой раб" number="8"/>: the data is in the attributes.
                        if (series == null) {
                            series = parser.getAttributeValue(null, "name")?.trim()?.ifEmpty { null }
                            volume = parser.getAttributeValue(null, "number")?.trim()?.toIntOrNull()
                        }
                        depth++
                    }
                    "image" -> {
                        // Inside <title-info> an image appears only in <coverpage>.
                        if (coverId == null) coverId = hrefOf(parser)?.removePrefix("#")
                        depth++
                    }
                    else -> depth++
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return TitleInfo(title, author, series, volume, coverId)
    }

    // The attribute is written "l:href" or "xlink:href" depending on the file,
    // so we look for any attribute whose name ends with "href".
    private fun hrefOf(parser: XmlPullParser): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).endsWith("href")) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun readBody(parser: XmlPullParser, out: MutableList<Fb2Chapter>) {
        var title: String? = null
        var paragraphs = mutableListOf<String>()
        var sectionDepth = 0

        // A local function: it can read and change the variables declared above it.
        fun finishChapter() {
            if (paragraphs.isNotEmpty()) out += Fb2Chapter(title, paragraphs)
            title = null
            paragraphs = mutableListOf()
        }

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "section" -> {
                        finishChapter()
                        sectionDepth++
                        depth++
                    }
                    "title" -> {
                        // A title can have several lines: "Глава 1841" + "Злые умы...".
                        val text = readChildTexts(parser, titleLineTags).joinToString(". ")
                        when {
                            sectionDepth == 0 -> Unit                  // the book title, shown elsewhere
                            title == null && paragraphs.isEmpty() -> title = text.ifEmpty { null }
                            text.isNotEmpty() -> paragraphs += text     // a heading in the middle of a section
                        }
                    }
                    "p", "v", "subtitle", "text-author" -> {
                        val text = readText(parser)
                        if (text.isNotEmpty()) paragraphs += text
                    }
                    // epigraph, poem, stanza, cite...: step inside, their <p>/<v> are picked up above
                    else -> depth++
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "section") {
                        finishChapter()
                        sectionDepth--
                    }
                    depth--
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        finishChapter()
    }

    // Splits a section that contains several "Глава N" paragraphs into real chapters.
    // A heading with no text after it (a table of contents line) produces nothing.
    private fun splitByHeadings(chapter: Fb2Chapter): List<Fb2Chapter> {
        if (chapter.paragraphs.count { chapterHeading.matches(it) } < 2) return listOf(chapter)

        val result = mutableListOf<Fb2Chapter>()
        var title = chapter.title
        var body = mutableListOf<String>()
        for (paragraph in chapter.paragraphs) {
            if (chapterHeading.matches(paragraph)) {
                if (body.isNotEmpty()) result += Fb2Chapter(title, body)
                title = paragraph
                body = mutableListOf()
            } else {
                body += paragraph
            }
        }
        if (body.isNotEmpty()) result += Fb2Chapter(title, body)

        // Such files often start with a table of contents, so a heading can appear twice:
        // once in the contents (followed by leftovers, not the chapter) and once for real.
        // The real one comes last, so for a repeated title we keep only the last chapter.
        val lastIndexOfTitle = result.withIndex().associate { (index, part) -> part.title to index }
        return result.filterIndexed { index, part ->
            part.title == null || lastIndexOfTitle[part.title] == index
        }
    }
}
