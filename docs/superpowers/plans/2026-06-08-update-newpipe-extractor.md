# Update NewPipe Extractor to v0.26.2 — Implementation Plan

**Goal:** Update the NewPipe Extractor from v0.25.2 to v0.26.2 without breaking any app features.

**Architecture:** Replace the extractor source files with v0.26.2 upstream content, re-apply minimal local customizations (disabled tests, Java 21 toolchain), and fix the one breaking API change (`List<MediaCapability>` → `Set<MediaCapability>`). The app only uses a narrow surface of the extractor API (`NewPipe`, `ServiceList`, `StreamInfo`, `SubtitlesStream`, `Image`, `Downloader`/`Request`/`Response`, `ReCaptchaException`) — none of which changed signatures.

**Tech Stack:** Kotlin (app), Java (extractor), Gradle, Room, Jetpack Compose

---

## Current State Summary

- **Version:** v0.25.2 (declared in `extractor/build.gradle.kts`)
- **Integration:** Local module via `include(":extractor")` with `projectDir = file("extractor/extractor")`
- **Git:** The `extractor/` directory is a separate Git repo (NOT a submodule). Its `origin` points to `deedeedev/YtReader.git`. No merge base exists with upstream `TeamNewPipe/NewPipeExtractor` — histories are completely unrelated.
- **Local modifications from upstream v0.25.2:** Only one auto-generated timeago pattern file (`zu.java` for Zulu language) was added. The build config was also customized (tests disabled, Java 21 toolchain override).
- **App API usage:** `NewPipe.init()`, `ServiceList.YouTube`, `StreamInfo.getInfo()`, `SubtitlesStream`, `Image`, `Downloader`, `Request`, `Response`, `ReCaptchaException`. The app does **NOT** call `getMediaCapabilities()`.

## Breaking Changes (v0.25.2 → v0.26.2)

| Change | Impact | App Affected? |
|--------|--------|---------------|
| `ServiceInfo.getMediaCapabilities()` returns `Set<MediaCapability>` instead of `List<MediaCapability>` | Type change in return value | **No** — app doesn't call this method |
| `ServiceInfo(String, Set<MediaCapability>)` constructor takes `Set` instead of `List` | Type change in constructor | **No** — only called internally in `ServiceList.java` |
| New `ExtractorLogger` class added | New optional feature | **No** — no app integration needed |
| Dependencies updated (protobuf, jsoup, gson, rhino, etc.) | Build-time only | **Yes** — must update version catalog |

## Strategy

Since the Git histories are unrelated (no merge base), we cannot use `git merge`. Instead:

1. **Replace extractor source** by checking out v0.26.2 tag files from the upstream remote (already added)
2. **Re-apply local build customizations** (disabled tests, Java 21 toolchain, local test dependencies)
3. **Update version catalog** with v0.26.2 dependency versions
4. **Update version string** in root `build.gradle.kts`

---

### Task 1: Prepare Upstream Remote and Verify Tags

**Files:**
- Inspect: `extractor/` (git repo)

- [ ] **Step 1: Verify upstream remote and tags are available**

```bash
cd extractor
git remote -v  # Should show origin (deedeedev) and upstream (TeamNewPipe)
git tag -l 'v0.2*'  # Should list v0.25.2, v0.26.0, v0.26.1, v0.26.2
```

The upstream remote was already added in research. If missing:
```bash
git remote add upstream https://github.com/TeamNewPipe/NewPipeExtractor.git
git fetch upstream --tags
```

- [ ] **Step 2: Create a feature branch from current HEAD**

```bash
cd extractor
git checkout -b update-v0.26.2 main
```

- [ ] **Step 3: In the main project, also create a feature branch**

```bash
cd ..
git checkout -b update-extractor-v0.26.2 main
```

---

### Task 2: Replace Extractor Source with v0.26.2 Content

**Files:**
- Modify: `extractor/extractor/src/` (all Java source files)
- Modify: `extractor/timeago-parser/` (timeago parser source)
- Modify: `extractor/build.gradle.kts` (root build, version string)
- Modify: `extractor/extractor/build.gradle.kts` (module build)
- Modify: `extractor/gradle/libs.versions.toml` (dependency versions)
- Modify: `extractor/settings.gradle.kts`
- Modify: `extractor/gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Checkout v0.26.2 source files from the upstream tag**

```bash
cd extractor

# Replace extractor module source code
git checkout v0.26.2 -- extractor/src/
git checkout v0.26.2 -- timeago-parser/src/
```

- [ ] **Step 2: Update root build.gradle.kts**

```bash
git checkout v0.26.2 -- build.gradle.kts
```

Verify the version is `v0.26.2` after checkout. The local version should say `version = "v0.26.2"`.

- [ ] **Step 3: Update gradle libs.versions.toml with v0.26.2 dependency versions**

```bash
git checkout v0.26.2 -- gradle/libs.versions.toml
```

The v0.26.2 versions are:
- `checkstyle = "12.3.0"`
- `gson = "2.14.0"`
- `jsr305 = "3.0.2"`
- `junit = "5.14.4"`
- `jsoup = "1.22.2"`
- `okhttp = "5.3.2"`
- `protobuf-lib = "4.35.0"`
- `protobuf-plugin = "0.10.0"`
- `rhino = "1.8.1"`
- `teamnewpipe-nanojson = "e9d656ddb49a412a5a0a5d5ef20ca7ef09549996"`

- [ ] **Step 4: Update extractor module build.gradle.kts**

```bash
git checkout v0.26.2 -- extractor/build.gradle.kts
```

- [ ] **Step 5: Update gradle wrapper**

```bash
git checkout v0.26.2 -- gradle/wrapper/gradle-wrapper.properties
git checkout v0.26.2 -- gradle/wrapper/gradle-wrapper.jar
git checkout v0.26.2 -- gradlew
git checkout v0.26.2 -- gradlew.bat
```

- [ ] **Step 6: Update settings.gradle.kts**

```bash
git checkout v0.26.2 -- settings.gradle.kts
```

- [ ] **Step 7: Re-apply local build customizations to extractor/build.gradle.kts**

The local build file disables tests and simplifies the build. Edit `extractor/extractor/build.gradle.kts` to re-add local customizations:

1. **Keep tests disabled** — Add/modify the test task block:
```kotlin
tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests.set(false)
    enabled = false  // LOCAL: disable tests
}
```

2. **Remove checkstyle plugin** — The local build doesn't need CI enforcement. Remove the `checkstyle` plugin and related configuration.

3. **Remove signing/publishing plugins** — Not needed for local builds. Remove `maven-publish` and `signing` plugins and their configurations.

4. **Keep protobuf configuration** — Preserved from upstream.

5. **Add Gson dependency** — The local build may need `implementation(libs.google.gson)` if it was in the original local build file. Check the pre-update version and preserve it if present.

- [ ] **Step 8: Verify Java toolchain compatibility**

The root `build.gradle.kts` (just checked out from v0.26.2) sets Java 11 for all projects:
```kotlin
languageVersion.set(JavaLanguageVersion.of(11))
```

The local extractor `build.gradle.kts` overrides this to Java 21:
```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

**Important:** The v0.26.2 extractor module `build.gradle.kts` may also set Java 21 for checkstyle. Keep the Java 21 toolchain in the extractor module build file for compilation. The app module targets JDK 11 for source/target compatibility separately.

---

### Task 3: Handle Breaking API Change (Set vs List)

**Files:**
- Inspect: `extractor/extractor/src/main/java/org/schabi/newpipe/extractor/StreamingService.java`
- Inspect: `extractor/extractor/src/main/java/org/schabi/newpipe/extractor/ServiceList.java`

- [ ] **Step 1: Verify ServiceList.java uses EnumSet**

After the v0.26.2 checkout, `ServiceList.java` should use `EnumSet.of()` instead of `List.of()`. Verify:

```bash
cd extractor
grep -n "MediaCapability\|EnumSet" extractor/src/main/java/org/schabi/newpipe/extractor/ServiceList.java | head -10
```

Expected: `EnumSet.of(MediaCapability.AUDIO, MediaCapability.VIDEO, ...)` patterns.

- [ ] **Step 2: Verify app code doesn't reference getMediaCapabilities**

```bash
cd ..  # Back to project root
grep -rn "getMediaCapabilities\|MediaCapability\|EnumSet" app/src/main/java/ --include='*.kt' --include='*.java'
```

Expected: No results. If results appear, update those files to use `Set` instead of `List`.

- [ ] **Step 3: Verify ExtractorLogger exists**

```bash
ls extractor/extractor/src/main/java/org/schabi/newpipe/extractor/utils/ExtractorLogger.java
```

Expected: File exists (new in v0.26.x).

---

### Task 4: Update App-Level Version Reference

**Files:**
- Modify: `gradle/libs.versions.toml` (project root, not extractor)

- [ ] **Step 1: Update the NewPipe extractor version in project root libs.versions.toml**

In `/home/davide/code/tmp/YtReader/gradle/libs.versions.toml`, change:
```toml
newPipeExtractor = "v0.26.2"
```

Note: The app uses `implementation(project(":extractor"))` rather than a Maven dependency, so this version string is primarily for reference/tracking.

- [ ] **Step 2: Check if protobuf version needs updating in project root**

```bash
grep -n "protobuf" gradle/libs.versions.toml
```

The extractor module has its own `libs.versions.toml`. If the project root also declares protobuf versions (for the app), ensure compatibility. The extractor now uses protobuf `4.35.0` and plugin `0.10.0`.

---

### Task 5: Build and Verify

- [ ] **Step 1: Build the extractor module**

```bash
./gradlew :extractor:assemble
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Build the app**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run app unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass.

- [ ] **Step 4: Run lint check**

```bash
./gradlew :app:lint
```

Expected: No new errors.

---

### Task 6: Commit Changes

- [ ] **Step 1: Stage and commit extractor changes**

```bash
cd extractor
git add -A
git commit -m "update NewPipe Extractor from v0.25.2 to v0.26.2

- Replace source files with upstream v0.26.2 content
- Update dependency versions (protobuf 4.35.0, jsoup 1.22.2, etc.)
- Preserve local build customizations (disabled tests, Java 21 toolchain)
- No app code changes needed (API surface unchanged)"
```

- [ ] **Step 2: Stage and commit project-level changes**

```bash
cd ..  # Back to project root
git add gradle/libs.versions.toml  # If version was updated
git add -A extractor/  # Changed files from submodule
git commit -m "update NewPipe Extractor to v0.26.2

- Bumps extractor from v0.25.2 to v0.26.2
- No app code changes needed (API surface unchanged)
- Fixes: YouTube duration extraction, SoundCloud track ID overflow, etc."
```

---

### Task 7: Post-Update Smoke Tests

- [ ] **Step 1: Verify key extractor APIs still work in the app**

Test these specific flows:
1. **Search** — `ServiceList.YouTube` is still accessible
2. **Video loading** — `StreamInfo.getInfo()` still returns valid data
3. **Subtitle fetching** — `SubtitlesStream` still parses correctly
4. **Thumbnail loading** — `Image` class still works
5. **Network requests** — `NewPipeDownloader` (extends `Downloader`) still functions
6. **Error handling** — `ReCaptchaException` still thrown on HTTP 429

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Protobuf version incompatibility (4.35.0 vs 3.25.5) | Medium | The extractor uses protobuf-javalite (lite variant). Major version bump may cause issues. Test build thoroughly. |
| Java toolchain mismatch (extractor needs JDK 21) | Low | Already handled — extractor uses JDK 21 toolchain, app targets JDK 11 for source/target. |
| Gradle wrapper version mismatch | Low | Extractor has its own gradlew; project uses its own. Each builds independently. |
| InnerTube/YouTube API changes | Medium | v0.26.2 includes fixes for YouTube duration extraction and lockup view model parsing. These are improvements. |
| App build fails due to new transitive dependencies | Low | Verify with `./gradlew :app:assembleDebug` |
