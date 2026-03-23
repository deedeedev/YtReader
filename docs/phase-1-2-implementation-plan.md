# YtReader Implementation Plan (Phase 1 + Phase 2)

## Scope

This is a status-updated implementation plan focused on Phase 2 scalability work, plus one required follow-up refactor identified from the current codebase.

In scope:

- **Phase 2 (scalability):** Room query/index improvements, lifecycle-aware flow collection, Library/Collection UI extraction.
- **Reader maintainability follow-up:** split `ReaderScreen.kt` if kept in active development.

Still out of scope: DataStore migration, i18n pass, and Phase 3 feature work.

---

## Current Baseline (verified against code)

- Room DB is now **v12** in `app/src/main/java/com/deedeedev/ytreader/data/local/AppDatabase.kt`.
- `SubtitleEntity` currently has a unique index only on `videoId + trackIdentity` in `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleEntity.kt`.
- `SubtitleDao` still exposes broad list access (`getAll`) and does not yet provide DAO projections/queries for grouped library rows by video.
- Library/Collection screens still do in-memory grouping/filtering/sorting in:
  - `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
  - `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`
- Lifecycle-aware collection is not yet adopted:
  - `collectAsStateWithLifecycle()` is not used.
  - `collectAsState()` is still used in `MainActivity`, `SearchScreen`, `LibraryScreen`, `CollectionDetailScreen`, `CollectionsScreen`, `SettingsScreen`, and `ReaderScreen`.
- Migration coverage changed: `app/src/androidTest/java/com/deedeedev/ytreader/data/local/AppDatabaseMigrationTest.kt` validates latest schema/hash and index presence, but does not verify upgrade paths across historical versions.
- `ReaderScreen.kt` is currently very large (~2169 lines), mixing screen orchestration, gesture handling, dialog state, persistence side-effects, and AndroidView configuration.

---

## Delivery Strategy

- Keep low-risk behavior-preserving changes first (lifecycle collection), then data/query reshaping, then UI extraction.
- Add tests with each ticket.
- Treat Reader refactor as an immediate follow-up after Phase 2 core items (or in parallel if touching reader features).

---

## Phase 2 — Scalability

## P2-1. Push library filtering/sorting/grouping into Room + add indexes

**Status**
- **Partially implemented**
  - Done: identity keying and unique index on `videoId + trackIdentity`.
  - Remaining: query/index work for scalable library browsing.

**Remaining implementation**
1. Add targeted non-unique indexes for sorting/filtering paths (at minimum `createdAt`, `lastOpenedAt`, `channelName`; retain existing identity index).
2. Add DAO projection/query models for:
   - library rows grouped by `videoId`
   - subtitle tracks per video
3. Move filter/sort/group logic from `LibraryScreen` and `CollectionDetailScreen` into DAO + ViewModel.
4. Keep composables render-only with precomputed UI models.

**Primary files**
- `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleEntity.kt`
- `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`

**Tests needed**
- DAO/instrumentation tests for sort order, channel filtering, grouping behavior.
- Regression tests for collection membership + missing-item behavior.
- Optional performance sanity test with large synthetic subtitle set.

**Acceptance criteria**
- No large in-memory group/sort remains in `LibraryScreen` / `CollectionDetailScreen`.
- Sorting/filtering/grouping behavior remains functionally equivalent.

**Effort**
- **L**

---

## P2-2. Lifecycle-aware flow collection in Compose

**Status**
- **Not implemented**

**Remaining implementation**
1. Add `lifecycle-runtime-compose` to version catalog and app dependencies.
2. Replace long-lived `collectAsState()` usages with `collectAsStateWithLifecycle()`.
3. Verify behavior when app backgrounds/foregrounds during updates.

**Primary files**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/deedeedev/ytreader/MainActivity.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/SearchScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionsScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreen.kt`

**Tests needed**
- Build and smoke tests.
- Manual lifecycle checks (foreground/background) during active flows.

**Acceptance criteria**
- No long-lived UI flows rely on plain `collectAsState()`.
- No missed updates after returning to foreground.

**Effort**
- **S**

---

## P2-3. Extract duplicated Library/Collection UI logic

**Status**
- **Not implemented**

**Remaining implementation**
1. Extract shared controls (channel filter + sort selector + sort direction).
2. Extract shared empty-state logic and common list section scaffolding.
3. Keep collection-only behavior local (swipe remove from collection, missing-count text).

**Primary files**
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt`
- New shared UI files under `app/src/main/java/com/deedeedev/ytreader/ui/home/`.

**Tests needed**
- UI behavior parity checks for both screens.
- Manual regression on filter/sort behavior.

**Acceptance criteria**
- Shared filter/sort UI logic exists in one place.
- Library and Collection screens remain behaviorally consistent.

**Effort**
- **M**

---

## P2-FU1. ReaderScreen refactor (maintainability follow-up)

**Why this is now needed**
- `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreen.kt` has grown very large and mixes many concerns.
- Current structure increases regression risk when changing reader features.

**Implementation**
1. Split Reader screen into focused components:
   - top/bottom bars and actions
   - study/original content panes
   - find/find-replace dialogs
   - brightness gesture/indicator
2. Move non-UI orchestration/state transitions into a dedicated reader state holder or ViewModel helpers.
3. Remove duplicated AndroidView setup code by introducing reusable setup/update helpers.

**Primary files**
- `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreen.kt`
- New files under `app/src/main/java/com/deedeedev/ytreader/ui/reader/` (suggested split by feature area).

**Tests needed**
- Existing reader UI tests kept green.
- Focused regression checks for selection, highlight, find, editing, and brightness gestures.

**Acceptance criteria**
- `ReaderScreen.kt` is substantially smaller and delegates to subcomponents.
- No functional regressions in reader workflows.

**Effort**
- **L**

---

## Suggested Sequence

1. **P2-2** (lifecycle collection).
2. **P2-1** (Room query/index scalability).
3. **P2-3** (Library/Collection UI extraction).
4. **P2-FU1** (ReaderScreen refactor).

---

## Validation Checklist (per release)

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedAndroidTest`
- `./gradlew lint`

Manual release checks:
- Subtitle download/save/redownload.
- Collection add/remove paths.
- AI clean start/cancel/complete flows.
- Notification open-to-reader deep link.
- Library + Collection sorting/filtering at scale.
- Reader selection/highlight/find/edit/brightness interactions.

---

## Risks & Mitigations

- **Room query complexity** can reduce maintainability.
  - Mitigation: add projection data classes and query tests before UI switch-over.
- **Behavior drift during UI extraction** across Library/Collection.
  - Mitigation: extract incrementally with parity checks.
- **Reader regressions during refactor** due to cross-cutting logic.
  - Mitigation: keep behavior-preserving commits and validate feature-by-feature.

---

## Definition of Done

- Phase 2 scalability tickets completed with tests.
- Reader follow-up refactor ticket completed (or explicitly deferred in roadmap).
- No blocking regressions in reader/library/AI-clean flows.
- Library interactions remain smooth with large subtitle datasets.
