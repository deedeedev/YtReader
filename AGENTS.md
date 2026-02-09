# Agent Guidelines for YtReader

## 1. Project Overview
- **Structure**:
  - `app/`: Android application (Kotlin, Jetpack Compose).
  - `extractor/`: Data extraction library (Java), located at `extractor/extractor`.
  - `gradle/libs.versions.toml`: Version catalog for dependencies.
- **Build System**: Gradle with Kotlin DSL (`.gradle.kts`).
- **JDK**: Version 11 (Source and Target compatibility).
- **Min SDK**: 24
- **Target SDK**: 36

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
  - UI observes state using `collectAsState()`.
  - **State Pattern**:
    ```kotlin
    data class MyUiState(
        val isLoading: Boolean = false,
        val data: List<String> = emptyList(),
        val error: String? = null
    )
    ```
- **Dependency Injection**: Manual DI via `AppContainer` (located in `YtReaderApplication.kt`).
  - Pass dependencies via ViewModel factory.
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
- **Style**: Follows standard Java conventions (likely inherited from NewPipe Extractor).
- **Nullability**: Explicitly use `@Nonnull` and `@Nullable` annotations (`javax.annotation`).
- **Immutability**: Prefer `final` for fields and parameters where applicable.
- **Exceptions**: Use checked exceptions (`ExtractionException`, `ParsingException`, `IOException`) for data fetching/parsing errors.
- **Documentation**: Use Javadoc for public APIs.

## 4. Architecture Specifics

### Navigation
- **Jetpack Compose Navigation**: Used for screen transitions.
- **Routes**: Defined in a `Screen` sealed class.
  ```kotlin
  sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
      object Search : Screen("search", "Search", Icons.Default.Search)
      object Library : Screen("library", "Library", Icons.Default.Home)
  }
  ```
- **NavHost**: Located in `MainScreen.kt`.

### Data Layer
- **Repository Pattern**: `YoutubeRepository` abstracts data sources.
- **Local Data**: Room Database (`AppDatabase`, `SubtitleDao`, `SubtitleEntity`).
  - Entities annotated with `@Entity`.
  - DAOs annotated with `@Dao` and use suspend functions or Flow.
- **Remote Data**: `NewPipeDownloader` and `OkHttp`.
- **Parsing**: `Jsoup` or custom parsers.

## 5. Implementation Rules for Agents
- **Context First**: Always read the surrounding code before editing. Match the existing style exactly.
- **Tests**: Write unit tests for logic changes. Verify using the commands above.
- **UI Changes**: When modifying UI, check `MainScreen.kt` or relevant screen files for composition structure.
- **Manifest**: If adding permissions or activities, remember to update `app/src/main/AndroidManifest.xml`.
- **Resources**: Put strings in `res/values/strings.xml`, colors in `res/values/colors.xml` (or `Color.kt` for Compose theme).
- **Version Catalog**: Use `libs.versions.toml` for adding new dependencies. Do not hardcode versions in `build.gradle.kts`.

## 6. Common Paths
- **App Source**: `app/src/main/java/com/deedeedev/ytreader/`
- **App Res**: `app/src/main/res/`
- **Extractor Source**: `extractor/extractor/src/main/java/org/schabi/newpipe/extractor/`
- **Manifest**: `app/src/main/AndroidManifest.xml`
- **Dependencies**: `gradle/libs.versions.toml`

## 7. Key Libraries
- **Networking**: OkHttp, Jsoup.
- **Extractor**: NewPipe Extractor (local module).
- **Database**: Room.
- **Date/Time**: ThreeTenABP.
- **Testing**: JUnit 4, Mockito, Espresso.

## 8. Common Tasks
- **Adding a new Screen**:
  1. Create Composable in `ui/`.
  2. Add entry to `Screen` sealed class in `ui/Screen.kt` (or inside `MainScreen.kt` if small).
  3. Add `composable` entry in `NavHost` in `MainScreen.kt`.
- **Adding a Dependency**:
  1. Add version and library to `gradle/libs.versions.toml`.
  2. Add `implementation(libs.my.library)` to `app/build.gradle.kts`.
  3. Sync gradle.
