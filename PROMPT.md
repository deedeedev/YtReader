# Task: Build YtReader Android App

You are an expert Android Engineer. Your goal is to implement the YtReader application as defined in `specs/ytreader.md`.

## Implementation Strategy
Follow the phases in order. Do not skip to UI until the data layer is verified.

### Phase 1: Infrastructure
- Setup `YtReaderApplication` and Manual DI (`AppContainer`).
- Implement Room DB (Entity, DAO, Database).
- Initialize NewPipeExtractor with a custom `Downloader`.

### Phase 2: Data & Logic
- Create `SubtitleRepository`.
- Implement `SubtitleCleaner` to strip HTML/Timestamps from XML/VTT.

### Phase 3: UI Implementation
- Setup Compose Navigation (Library, Search, Reader).
- Build Search Screen (NewPipe integration).
- Build Library & Reader screens.

## Operating Instructions
1. **Fresh Context**: Read the spec and existing code at the start of every turn.
2. **Atomic Commits**: Implement one small piece at a time (e.g., one DAO method, then one ViewModel).
3. **Backpressure**: After writing code, run `./gradlew assembleDebug` or lint checks to ensure stability.
4. **Verification**: If you finish a task, update a `progress.md` file (or scratchpad).

## Completion Signal
When all features in the spec are implemented, verified, and committed to git, output exactly:
<promise>LOOP_COMPLETE</promise>
