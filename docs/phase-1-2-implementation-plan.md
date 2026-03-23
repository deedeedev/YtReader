# YtReader Implementation Plan (Phase 1 + Phase 2)

## Scope

This is a status-updated implementation plan focused on Phase 2 scalability work, plus one required follow-up refactor identified from the current codebase.

In scope:

- **Phase 2 (remaining scalability):** Library/Collection UI extraction.
- **Reader maintainability follow-up:** split `ReaderScreen.kt` if kept in active development.

Still out of scope: DataStore migration, i18n pass, and Phase 3 feature work.

---

## Current Gaps (verified against code)

- Library/Collection UI extraction is only partially done:
  - Shared rendering pieces already exist (`LibraryItemCard`, `SubtitleChip`, `AddToCollectionDialog`), and are reused by both screens.
  - Filter/sort controls and empty-state scaffolding are still duplicated between `LibraryScreen` and `CollectionDetailScreen`.
- Migration coverage gap: `app/src/androidTest/java/com/deedeedev/ytreader/data/local/AppDatabaseMigrationTest.kt` validates latest schema/hash and index presence, but does not verify upgrade paths across historical versions.
- Reader refactor is only partially done:
  - Logic helpers exist in separate files (for example `ReaderFind.kt`), but screen composition/orchestration remains concentrated in `ReaderScreen.kt`.
  - `ReaderScreen.kt` is still very large (~2169 lines), mixing orchestration, gesture handling, dialog state, persistence side-effects, and AndroidView configuration.

---

## Delivery Strategy

- Keep low-risk behavior-preserving changes first (lifecycle collection), then data/query reshaping, then UI extraction.
- Add tests with each ticket.
- Treat Reader refactor as an immediate follow-up after Phase 2 core items (or in parallel if touching reader features).

---

## Phase 2 — Scalability

## P2-3. Extract duplicated Library/Collection UI logic

**Status**
- **Partially implemented**

**Remaining implementation**
1. Extract shared controls (channel filter + sort selector + sort direction) into reusable home UI components.
2. Extract shared empty-state logic and common list-section scaffolding.
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

**Status**
- **Partially implemented**

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

1. **P2-3** (Library/Collection UI extraction).
2. **P2-FU1** (ReaderScreen refactor).
3. **P2-2** manual lifecycle validation (foreground/background updates).

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
