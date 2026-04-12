# YtReader Codebase Analysis & Improvement Suggestions

## LOW PRIORITY

### 15. Extractor Module Issues

- **Tests completely disabled** (`tasks.test { enabled = false }`) — upstream has 125 test files that could catch regressions
- **Forked source** instead of git submodule — makes upstream sync manual and error-prone
- **Java toolchain conflict**: root `build.gradle.kts` sets Java 11 via `allprojects {}`, subproject overrides to 21
- **Gratuitous `Set`→`List` change** in `StreamingService`/`ServiceInfo` makes merging upstream changes harder

**Fix:** Consider consuming the extractor as a git submodule or published Maven artifact. Re-enable tests. Resolve toolchain conflict at the root level.

---

### 16. Build Performance (`gradle.properties`)

Current config leaves performance on the table:
- **Enable build cache:** Add `org.gradle.caching=true`
- **Enable parallel execution:** Uncomment `org.gradle.parallel=true` (2 modules can build in parallel)
- **Increase JVM heap:** Consider `-Xmx4096m` for KSP + Compose + Protobuf compilation
- **Add `android.enableJetifier=false`:** Project uses AndroidX exclusively; explicit setting speeds builds

---

### 17. Test Coverage Gaps

**Critical untested files:**
- `HomeViewModel.kt` — the primary ViewModel for 4 screens
- `YoutubeRepository.kt` — the main data gateway
- `AiCleaningWorker.kt` — foreground worker for AI cleaning
- `AutoBackupWorker.kt` — background backup worker
- 5 of 6 DAOs (only `SubtitleDao` is tested at the DAO level)
- `EpubExporter.kt` — EPUB generation
- `NewPipeDownloader.kt` — network downloader
- `SettingsViewModel.kt` — settings ViewModel

**Thin coverage needing expansion:**
- `CollectionRepositoryTest` — only 3 tests (missing delete, video management, errors)
- `SubtitleParserTest` — only 3 tests (missing malformed input, encoding, nested tags)
- `VideoNotesViewModelTest` — only 1 test method
- `VideoThumbnailStoreTest` — only 2 tests (no file I/O testing)

**Strengths in existing tests:**
- `ReaderScreenTest` (1001 lines) — outstanding Compose UI test
- `SubtitleDaoTest` (488 lines) — thorough DAO testing with in-memory Room
- `ReaderViewModelTest` — excellent ViewModel testing with proper coroutine dispatchers
- Good edge case coverage in `TextHighlightTest`, `ReaderFindTest`, `AnnotationRemapperTest`

---

## Positive Notes

The codebase has genuine strengths:
- Excellent `ReaderScreen` Compose UI tests (1001 lines) — comprehensive gesture and lifecycle testing
- Thorough `SubtitleDaoTest` (488 lines) with in-memory Room
- Proper use of `StateFlow`/`collectAsStateWithLifecycle` throughout
- Clean version catalog management (`libs.versions.toml`) with no hardcoded versions
- Well-structured manual DI via `AppContainer` interface
- Domain layer (`SubtitleParser`, `YouTubeVideoIdNormalizer`, `SubtitleIdentity`) properly separated from Android
- Pure function testing pattern — many tests target extracted pure functions rather than testing composables directly
- `FAIL_ON_PROJECT_REPOS` enabled in `settings.gradle.kts`
- Non-transitive R classes enabled
- Configuration cache enabled for faster Gradle startup
