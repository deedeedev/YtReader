# YtReader Remaining Implementation Plan (Extracted)

This document contains only the work that still needs implementation from the previous Phase 1 + 2 plan.

## In Scope (Remaining)

- **Phase 2 scalability:** Library/Collection UI extraction completion.

## Remaining Gaps

1. Library/Collection extraction is partially complete.
   - Already shared: `LibraryItemCard`, `SubtitleChip`, `AddToCollectionDialog`.
   - Still duplicated between `LibraryScreen` and `CollectionDetailScreen`:
     - filter/sort controls
     - empty-state scaffolding

2. Migration coverage gap in Android DB tests.
   - `app/src/androidTest/java/com/deedeedev/ytreader/data/local/AppDatabaseMigrationTest.kt` validates latest schema/hash and indexes.
   - Upgrade paths across historical versions are not yet validated.

## Delivery Strategy (Remaining)

- Keep behavior-preserving changes first, then data/query reshaping, then UI extraction.
- Add tests with each remaining ticket.

## Remaining Ticket

### P2-SC1. Library/Collection UI extraction completion

**Why needed**
- Duplicated controls/scaffolding across `LibraryScreen` and `CollectionDetailScreen` increase maintenance cost and drift risk.

**Implementation**
1. Extract shared filter/sort controls into reusable composables.
2. Extract shared empty-state scaffolding into reusable composables.
3. Integrate shared components in both screens while preserving behavior.

**Primary files**
- `app/src/main/java/com/deedeedev/ytreader/ui/` (Library/Collection screen files and extracted shared UI files)

**Tests needed**
- Keep existing Library/Collection UI tests green.
- Add regression checks for:
  - sort option selection
  - sort direction toggle
  - filter selection behavior
  - empty-state rendering parity

**Acceptance criteria**
- No duplicated filter/sort and empty-state scaffolding across Library and Collection screens.
- No functional regressions in Library/Collection workflows.

## Additional Remaining Coverage Item

### DB migration upgrade-path tests

**Implementation**
1. Extend migration tests to verify upgrades from historical schema versions to current.
2. Validate data integrity and required indexes after migration.

**Primary file**
- `app/src/androidTest/java/com/deedeedev/ytreader/data/local/AppDatabaseMigrationTest.kt`

## Validation Checklist (Per Release)

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedAndroidTest`
- `./gradlew lint`

Manual checks:
- Subtitle download/save/redownload.
- Collection add/remove paths.
- AI clean start/cancel/complete flows.
- Notification open-to-reader deep link.
- Library + Collection sorting/filtering at scale.

## Remaining Risks & Mitigations

- **Room query complexity** may reduce maintainability.
  - Mitigation: add projection data classes and query tests before UI switch-over.
- **Behavior drift during UI extraction** across Library/Collection.
  - Mitigation: extract incrementally with parity checks.

## Definition of Done (Remaining)

- Phase 2 scalability tickets completed with tests.
- Migration upgrade-path coverage added.
- No blocking regressions in library/AI-clean flows.
- Library interactions remain smooth with large subtitle datasets.
