# YtReader Implementation Plan (Phase 1 + Phase 2)

## Scope

This plan covers the roadmap items already agreed for:

- **Phase 2 (scalability):** Room query/index improvements, lifecycle-aware flow collection, Library/Collection UI extraction.

Out of scope for this plan: ReaderScreen large refactor, DataStore migration, i18n pass, and new product features (Phase 3).

---

## Current Baseline (verified)

- DB is Room v11 in `app/src/main/java/com/deedeedev/ytreader/data/local/AppDatabase.kt`, with migrations defined in `app/src/main/java/com/deedeedev/ytreader/AppContainer.kt`.
- Migration tests currently cover only 8→9 in `app/src/androidTest/java/com/deedeedev/ytreader/data/local/AppDatabaseMigrationTest.kt`.
- Library/Collection screens do in-memory grouping/filter/sort in:
  - `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
  - `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`
- Multiple screens collect flows with `collectAsState()` (not lifecycle-aware), including `app/src/main/java/com/deedeedev/ytreader/MainActivity.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `CollectionDetailScreen.kt`, `CollectionsScreen.kt`, `SettingsScreen.kt`, and `ReaderScreen.kt`.

---

## Delivery Strategy

- Ship **Phase 2** as one scalability release.
- Keep behavior-compatible changes first, then internal cleanup/extraction.
- Add tests in parallel with each ticket to avoid backlog risk.

---

## Phase 2 — Scalability

## P2-1. Push library filtering/sorting/grouping into Room + add indexes

**Goal**
Reduce UI-thread and memory pressure as subtitle library scales.

**Implementation**
1. Add targeted indexes on frequently queried/sorted columns:
   - `videoId`, `createdAt`, `lastOpenedAt`, `channelName`, and any new identity keys.
2. Introduce DAO query models for library summaries (grouped by video) and subtitle tracks per video.
3. Move filter/sort computation from composables into DAO/ViewModel pipeline.
4. Keep UI pure-rendering: it receives already grouped/sorted state.

**Primary files**
- `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleEntity.kt`
- `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`

**Tests**
- DAO/instrumentation:
  - query returns expected order for each `SortOption`
  - channel filter correctness
  - grouping correctness with multiple tracks/video
- Performance sanity:
  - synthetic large dataset benchmark (or macro-level timing logs)

**Acceptance criteria**
- No large in-memory group/sort in composables.
- Scrolling and screen open time remain stable on larger libraries.

**Effort**
- **L**

---

## P2-2. Lifecycle-aware flow collection in Compose

**Goal**
Avoid unnecessary collection/work while screens are backgrounded.

**Implementation**
1. Add lifecycle compose dependency (`lifecycle-runtime-compose`) if missing.
2. Replace `collectAsState()` with `collectAsStateWithLifecycle()` in all UI screens and activity compose trees.
3. Verify no behavior regressions for immediate state updates.

**Primary files**
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/deedeedev/ytreader/MainActivity.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/SearchScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionsScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreen.kt`

**Tests**
- Build and UI smoke tests.
- Manual: background/foreground app while long-running updates happen.

**Acceptance criteria**
- No plain `collectAsState()` remains for long-lived app flows.
- UI state still updates correctly when returning to foreground.

**Effort**
- **S**

---

## P2-3. Extract duplicated Library/Collection UI logic

**Goal**
Cut maintenance overhead and keep filter/sort behavior consistent.

**Implementation**
1. Extract shared composables/state holders:
   - filter/sort toolbar
   - empty/error list states
   - shared list controller for sort/filter selections
2. Keep `LibraryItemCard`, `AddToCollectionDialog`, and subtitle chip behaviors centralized.
3. Ensure Collection-specific behaviors (swipe remove, missing count) stay local.

**Primary files**
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`
- (new) shared UI file(s), e.g.:
  - `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryControls.kt`
  - `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryListState.kt`

**Tests**
- UI behavior regression checks for both screens.
- Snapshot/manual verification for sort/filter interactions.

**Acceptance criteria**
- Shared logic exists in one place.
- Library and Collection screens still behave identically where intended.

**Effort**
- **M**

---

## Suggested Sequence

1. **P2-2** (lifecycle collection) — low-risk scalability hygiene.
2. **P2-1** (Room queries/indexes) — core scalability uplift.
3. **P2-3** (UI extraction) — maintainability cleanup after data flow stabilizes.

---

## Validation Checklist (per release)

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedAndroidTest`
- `./gradlew lint`

Manual release checks:
- Subtitle download/save/redownload
- Collection add/remove paths
- AI clean start/cancel/complete flows
- Notification open-to-reader deep link
- Library + Collection sorting/filtering at scale

---

## Risks & Mitigations

- **Query complexity in Room**: hard-to-maintain SQL.
  - Mitigation: introduce DAO projection models and add targeted query tests.
- **Behavior drift during UI extraction**:
  - Mitigation: extract in small commits with parity checks each step.

---

## Definition of Done

- All Phase 2 tickets merged with tests.
- No blocking regressions in reader/library/AI-clean flows.
- Library interactions remain smooth with large subtitle datasets.
