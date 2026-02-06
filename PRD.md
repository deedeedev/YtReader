# Project: YtReader (Subtitle Reader)

## Overview
An Android application to extract, save, and read YouTube subtitles in a clean, book-like format. The app allows users to import subtitles via link sharing or direct URL entry, saves them to a local library, and offers a customizable reading experience.

## Goals
- **Privacy:** No YouTube login required. No API keys.
- **Offline:** Saved subtitles are stored locally in a Room database.
- **Readability:** Clean text view without timestamps, with theme and font adjustments.
- **Integration:** Handle shared YouTube links directly.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Navigation:** Compose Navigation
- **Architecture:** MVVM + Manual Dependency Injection
- **Database:** Room (SQLite)
- **Network:** OkHttp + Jsoup (for NewPipeExtractor)
- **Core:** NewPipeExtractor (for YouTube parsing)

## Implementation Plan

### Phase 1: Infrastructure & Setup
1. [ ] **Setup Application & DI Container**:
    - Create `YtReaderApplication` class extending `Application`.
    - Create `AppContainer` class to hold application-wide dependencies (manual DI).
    - Update `AndroidManifest.xml` to use `android:name=".YtReaderApplication"`.
    - Ensure `ThreeTenABP.init(this)` is called in Application `onCreate`.
2. [ ] **Setup Room Database**:
    - Create `Subtitle` entity with fields: `id` (PrimaryKey), `videoId` (String), `videoTitle` (String), `languageCode` (String), `languageName` (String), `content` (String), `savedAt` (Long).
    - Create `SubtitleDao` with `getAll()` (Flow list), `getById()` (suspend), `insert()` (suspend), `delete()` (suspend).
    - Create `AppDatabase` abstract class extending `RoomDatabase`.
    - Instantiate the Database and Dao in `AppContainer`.
3. [ ] **Initialize NewPipeExtractor**:
    - Create `NewPipeDownloader` class implementing `org.schabi.newpipe.extractor.Downloader`. Use `OkHttpClient` to execute requests.
    - In `YtReaderApplication.onCreate`, call `NewPipe.init(NewPipeDownloader.getInstance())`.

### Phase 2: Core Logic (Data Layer)
4. [ ] **Subtitle Repository**:
    - Create `SubtitleRepository` in `AppContainer`.
    - Implement `getVideoInfo(url: String): StreamInfo`: Fetches metadata using NewPipe's `StreamInfo.getInfo(url)`.
    - Implement `downloadSubtitle(subStream: SubtitlesStream): String`: Downloads the subtitle content.
5. [ ] **Subtitle Parsing Logic**:
    - Create a utility object `SubtitleCleaner`.
    - Implement functions to parse TTML/VTT (XML) formats returned by YouTube.
    - Logic should extract text lines and strip out HTML tags (`<br>`, `<font>`) and timestamps, returning a clean String suitable for reading.

### Phase 3: UI - Navigation & Search
6. [ ] **Navigation Setup**:
    - Define a sealed class `Screen` with routes: `Library`, `Search`, `Reader/{subtitleId}`.
    - Set up `NavHost` in `MainActivity` with empty placeholder composables for now.
7. [ ] **Search ViewModel & Screen**:
    - Create `SearchViewModel`. Inputs: `onUrlChanged`, `onSearchClicked`, `onDownloadClicked`.
    - State: `isLoading`, `videoTitle`, `availableSubtitles` (List), `error`.
    - Create `SearchScreen`: Text field for URL, "Search" button, and a list of results.
    - On "Download", call Repository to fetch text, clean it, save to Room via DAO, and navigate to Library.

### Phase 4: UI - Library & Reader
8. [ ] **Library Screen**:
    - Create `LibraryViewModel` exposing a `Flow<List<Subtitle>>` from the DAO.
    - Create `LibraryScreen`: Display list of saved subtitles (Title + Language). Clicking an item navigates to `Reader/{id}`.
9. [ ] **Reader ViewModel & Screen**:
    - Create `ReaderViewModel` that takes `subtitleId` argument. Fetches `Subtitle` from DAO.
    - Create `ReaderScreen`:
        - Top Bar: Title of video.
        - Content: Scrollable `Text` displaying the full subtitle content.
10. [ ] **Reader Customization (Appearance)**:
    - Add state in `ReaderViewModel` (or a local CompositionLocal) for `fontSize` (Sp) and `isDarkMode` (Boolean).
    - Add UI controls (e.g., a bottom sheet or icon button) to increase/decrease font size.

### Phase 5: Integration & Polish
11. [ ] **Handle Share Intent**:
    - Add `<intent-filter>` to `MainActivity` in `AndroidManifest.xml` for `action.SEND` with `text/plain` type (to catch shared YouTube URLs).
    - In `MainActivity`, check `intent?.getStringExtra(Intent.EXTRA_TEXT)`. If present, prepopulate the Search Screen or auto-trigger search.
12. [ ] **Error Handling & UI Polish**:
    - Improve error messages in `SearchViewModel` (e.g., "Invalid URL", "No subtitles found").
    - Add proper loading spinners (CircularProgressIndicator) during network calls.
    - Ensure the app looks good in both Light and Dark themes.
