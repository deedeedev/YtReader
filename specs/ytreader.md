# Spec: YtReader (Subtitle Reader)

## Product Goal
A privacy-focused Android app to extract and read YouTube subtitles offline in a book-like format using Jetpack Compose and Room.

## Core Requirements
- **No Auth**: Use NewPipeExtractor for parsing (no YouTube API keys).
- **Storage**: Save subtitles (Title, Lang, Content) in a local Room DB.
- **Reader UI**: A clean, scrollable text view with font size controls.
- **Integration**: Handle shared YouTube URLs via Intent Filters.

## Technical Stack
- Kotlin / Jetpack Compose (Material 3)
- Room Database / MVVM Architecture
- NewPipeExtractor (OkHttp + Jsoup)
- ThreeTenABP for time handling

## Definition of Done (Success Criteria)
1. App compiles and runs without errors.
2. User can paste a URL, see a list of available subtitles, and download one or more.
3. Downloaded subtitles appear in a Library list.
4. Clicking a library item opens the Reader with the cleaned text.
5. Font size adjustment in the Reader works.
6. Sharing a YouTube link from the YT app populates the search field.
