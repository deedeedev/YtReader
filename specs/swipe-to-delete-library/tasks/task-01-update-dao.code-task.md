---
status: pending
created: 2026-02-09
started: null
completed: null
---
# Task: Update SubtitleDao

## Description
Update the SubtitleDao to support deleting all subtitles associated with a specific video ID. This is necessary for deleting library items (videos) and their data.

## Background
Currently, the `SubtitleDao` only supports deleting a single subtitle by its entity. We need a way to bulk delete all subtitles for a given `videoId`.

## Reference Documentation
**Required:**
- Design: specs/swipe-to-delete-library/design.md

## Technical Requirements
1.  Add a `deleteByVideoId(videoId: String)` method to `SubtitleDao`.
2.  Use a `@Query` annotation to execute the SQL `DELETE FROM subtitles WHERE videoId = :videoId`.
3.  Ensure the method is marked as `suspend`.

## Dependencies
- `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt`

## Implementation Approach
1.  Open `SubtitleDao.kt`.
2.  Add the new query method.
3.  Create a unit test to verify the functionality if possible (or just manually verify via integration later).

## Acceptance Criteria

1.  **Delete by Video ID**
    - Given a `videoId` string
    - When `deleteByVideoId` is called with that ID
    - Then all rows in the `subtitles` table matching that `videoId` are removed.
    - And rows with other video IDs remain unaffected.

## Metadata
- **Complexity**: Low
- **Labels**: database, room, dao
- **Required Skills**: Android, Kotlin, Room
