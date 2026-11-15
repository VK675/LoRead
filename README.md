# Folio

A personal Android app for listening to audiobooks and reading books.
Built as a learning project; installed manually, not published on Google Play.

**Status:** early work in progress. The FB2 reader works; the audio player is not built yet.

## What works now

- FB2 reader (plain and zipped files): chapter list, chapter text, previous/next chapter
- Library with covers and titles read from the books, volumes grouped by series
- Remembers the chapter and scroll position of every book
- Books are picked with the system file picker or dropped into the app's own folder

## Planned features

1. Audio player: background playback, media notification, resume position
2. Library: covers, authors, narrators, chapters, search
3. Playback speed, sleep timer, bookmarks
4. Reader: TXT, FB2, EPUB
5. Text-to-speech with highlighting
6. Optional YouTube source module

## Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- MVVM with ViewModel + StateFlow, Coroutines and Flow
- Coil for cover images
- AndroidX Media3, Room, DataStore (added as the stages need them)

## Build

Open the project in Android Studio and run the `app` configuration, or:

```
./gradlew assembleDebug
```

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
