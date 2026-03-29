# Agent Guidelines for YtReader

## 1. Project Overview
- **Structure**:
  - `app/`: Android application (Kotlin, Jetpack Compose).
  - `extractor/`: Data extraction library (Java), located at `extractor/extractor`.
  - `gradle/libs.versions.toml`: Version catalog for dependencies.
- **Build System**: Gradle with Kotlin DSL (`.gradle.kts`).
- **JDK**: 
  - App module: Version 11 (Source and Target compatibility).
  - Extractor module: Java 21 toolchain.
- **Min SDK**: 26
- **Target SDK**: 36
- **Compile SDK**: 36

## 2. Build & Test Commands
Use `./gradlew` (or `gradlew.bat` on Windows) for all tasks.

### Build
- **App Debug**: `./gradlew :app:assembleDebug`
- **Extractor**: `./gradlew :extractor:assemble`
- **Clean**: `./gradlew clean`
- **Sync Dependencies**: `./gradlew --refresh-dependencies`

### Test
- **Run all unit tests**: `./gradlew test`
- **Run app unit tests**: `./gradlew :app:testDebugUnitTest`
- **Run extractor tests**: `./gradlew :extractor:test`
- **Run a single test class**:
  `./gradlew :app:testDebugUnitTest --tests "com.deedeedev.ytreader.ExampleUnitTest"`
- **Run a single test method**:
  `./gradlew :app:testDebugUnitTest --tests "com.deedeedev.ytreader.ExampleUnitTest.testMethodName"`
- **Android Instrumented Tests**: `./gradlew connectedAndroidTest`

### Linting & Code Quality
- **Lint**: `./gradlew lint`
- **Check**: `./gradlew check` (Runs tests and lint)

## 3. Code Style & Conventions

### General
- **Indentation**: 4 spaces.
- **File Encoding**: UTF-8.
- **Absolute Paths**: When using tools, always resolve paths relative to the project root.

### Kotlin (Android App)
- **UI Framework**: Jetpack Compose (Material3).
- **Architecture**: MVVM (Model-View-ViewModel).
  - ViewModels expose `UiState` (data class) via `StateFlow` (`_uiState.asStateFlow()`).
  - UI observes state using `collectAsState()` or `collectAsStateWithLifecycle()`.
  - **State Pattern**:
    ```kotlin
    data class MyUiState(
        val isLoading: Boolean = false,
        val data: List<String> = emptyList(),
        val error: String? = null
    )
    ```
- **Dependency Injection**: Manual DI via `AppContainer` interface in `AppContainer.kt`.
  - Implementation: `DefaultAppContainer` in same file.
  - Container stored in `YtReaderApplication.container`.
  - Pass dependencies via ViewModel factory methods (e.g., `HomeViewModel.provideFactory(...)`).
  - Do not use Hilt or Dagger unless explicitly requested to refactor.
- **Concurrency**: Kotlin Coroutines.
  - Use `viewModelScope.launch` for ViewModel operations.
  - Use `withContext(Dispatchers.IO)` for repository/data operations.
  - Use `LaunchedEffect` for side effects in Composables.
- **Naming**:
  - Composables: `PascalCase` (e.g., `MainScreen`).
  - Classes: `PascalCase`.
  - Functions/Properties: `camelCase`.
  - Constants: `SCREAMING_SNAKE_CASE` (e.g., `const val TIMEOUT_MS = 5000`).
  - Backing Properties: `_propertyName` (private mutable) and `propertyName` (public immutable).
- **Imports**:
  1. Android/AndroidX
  2. Third-party libraries
  3. Project imports (com.deedeedev...)
  4. Java standard library
- **File Structure**:
  - One class per file generally.
  - Extension functions can go in the file of the class they extend if small, or a dedicated `Extensions.kt`.

### Java (Extractor Module)
- **Style**: Follows standard Java conventions (inherited from NewPipe Extractor).
- **Nullability**: Explicitly use `@Nonnull` and `@Nullable` annotations (`javax.annotation`).
- **Immutability**: Prefer `final` for fields and parameters where applicable.
- **Exceptions**: Use checked exceptions (`ExtractionException`, `ParsingException`, `IOException`) for data fetching/parsing errors.
- **Documentation**: Use Javadoc for public APIs.

## 4. Architecture Specifics

### Navigation
- **Jetpack Compose Navigation**: Used for screen transitions.
- **Routes**: Defined in a `Screen` sealed class with `@StringRes` for labels.
  ```kotlin
  sealed class Screen(
      val route: String,
      @StringRes val labelRes: Int,
      val icon: ImageVector
  ) {
      object Search : Screen("search", R.string.screen_search, Icons.Default.Search)
      object Library : Screen("library", R.string.library, Icons.Default.Home)
      object Collections : Screen("collections", R.string.collections, Icons.Default.CollectionsBookmark)
      object Settings : Screen("settings", R.string.screen_settings, Icons.Default.Settings)
      // Reader has complex route with parameters
      object Reader : Screen(
          "reader/{subtitleId}?highlightStart={highlightStart}&highlightEnd={highlightEnd}&bookmarkStart={bookmarkStart}",
          R.string.screen_reader,
          Icons.AutoMirrored.Filled.MenuBook
      )
  }
  ```
- **NavHost**: Located in `MainScreen.kt`.
- **Bottom Bar**: Only shown on non-Reader screens. Items: Library, Search, Collections, Settings.
- **Deep Linking**: Supports `ACTION_SEND` (share text intent) to open Search.

### Data Layer
- **Repository Pattern**: `YoutubeRepository`, `CollectionRepository`, `UserPreferencesRepository`, `AiCleaningRepository`.
- **Local Data**: Room Database (`AppDatabase`, 6 DAOs, 7 Entities).
  - Entities: `SubtitleEntity`, `VideoEntity`, `HighlightNoteEntity`, `BookmarkEntity`, `CollectionEntity`, `CollectionVideoEntity`, `SearchHistoryEntity`
  - DAOs: `SubtitleDao`, `VideoDao`, `HighlightNoteDao`, `BookmarkDao`, `CollectionDao`, `SearchHistoryDao`
  - Database version: 22 with migrations.
- **Remote Data**: `NewPipeDownloader` and `OkHttp`.
- **Parsing**: `Jsoup` or custom parsers.
- **WorkManager**: Background tasks for `AiCleaningWorker` and `AutoBackupWorker`.

### Domain Layer
- Contains business logic that can be used independently of Android.
- `SubtitleParser.kt`: Parses subtitle formats.
- `YouTubeVideoIdNormalizer.kt`: Normalizes YouTube URLs/IDs.
- `SubtitleIdentity.kt`: Subtitle identification logic.

### Widgets
- Home screen widgets implemented in `widget/`:
  - `ReaderWidgetProvider`: Main widget.
  - `ReaderWidgetProviderIcon`: Icon widget variant.

## 5. Implementation Rules for Agents
- **Context First**: Always read the surrounding code before editing. Match the existing style exactly.
- **Tests**: Write unit tests for logic changes. Verify using the commands above.
- **UI Changes**: When modifying UI, check `MainScreen.kt` or relevant screen files for composition structure.
- **Manifest**: If adding permissions or activities, remember to update `app/src/main/AndroidManifest.xml`.
  - Current permissions: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- **Resources**: Put strings in `res/values/strings.xml`, colors in `res/values/colors.xml` (or `Color.kt` for Compose theme).
- **Version Catalog**: Use `libs.versions.toml` for adding new dependencies. Do not hardcode versions in `build.gradle.kts`.
- **Database Changes**: When adding new entities/DAOs, update `AppDatabase.kt` with migration path from previous version.

## 6. Common Paths
- **App Source**: `app/src/main/java/com/deedeedev/ytreader/`
  - `ui/` - Compose UI screens
  - `ui/home/` - Library, Search, Collections screens
  - `ui/reader/` - Reader, VideoNotes screens
  - `ui/settings/` - Settings screen
  - `ui/theme/` - Compose theme files
  - `data/` - Repositories, Workers
  - `data/local/` - Room database, DAOs, Entities
  - `data/remote/` - Network downloaders
  - `domain/` - Business logic (Android-independent)
  - `widget/` - Home screen widgets
- **App Res**: `app/src/main/res/`
- **Extractor Source**: `extractor/extractor/src/main/java/org/schabi/newpipe/extractor/`
- **Manifest**: `app/src/main/AndroidManifest.xml`
- **Dependencies**: `gradle/libs.versions.toml`
- **Database Schemas**: `app/schemas/` (auto-generated by Room)
- **Docs**: `docs/`

## 7. Key Libraries
- **Networking**: OkHttp, Jsoup.
- **Image Loading**: Coil.
- **JSON**: Gson.
- **Extractor**: NewPipe Extractor (local module).
- **Database**: Room.
- **Background Work**: WorkManager.
- **Date/Time**: ThreeTenABP.
- **Protobuf**: For extractor.
- **Rhino**: JavaScript engine (extractor dependency).
- **Desugar JDK**: Java 8+ API support.
- **Testing**: JUnit 4, Mockito, Mockito-Kotlin, Espresso, Coroutines Test.

## 8. Common Tasks

### Adding a new Screen
1. Create Composable in appropriate `ui/` subdirectory (`home/`, `reader/`, or `settings/`).
2. Add entry to `Screen` sealed class in `ui/MainScreen.kt` with route, `@StringRes` label, and icon.
3. Add `composable` entry in `NavHost` in `MainScreen.kt`.
4. Add navigation item to bottom bar if it's a main screen.

### Adding a DAO/Entity
1. Create Entity class in `data/local/`.
2. Create DAO interface in `data/local/`.
3. Add abstract method to `AppDatabase` in `data/local/AppDatabase.kt`.
4. Add migration if schema changes (increment version, add `Migration` object).
5. Add to `AppContainer` interface and `DefaultAppContainer` implementation.

### Adding a Worker
1. Create Worker class in `data/` using `CoroutineWorker`.
2. Register in manifest if needed (for `BroadcastReceiver` workers).
3. Schedule via `WorkManager` in appropriate repository or scheduler.

### Adding a Dependency
1. Add version to `[versions]` in `gradle/libs.versions.toml`.
2. Add library to `[libraries]` in same file.
3. Add `implementation(libs.my.library)` to `app/build.gradle.kts`.
4. Run `./gradlew` to sync.
