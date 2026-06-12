# Pre-Publish Security Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the YtReader codebase for safe publication on a public GitHub repository by masking secrets, adding a license, improving .gitignore, and removing tracked IDE files.

**Architecture:** This is a security & hygiene pass — no new features. Changes touch: UI (password masking), data layer (excluding API key from backup exports), dependency config (EncryptedSharedPreferences), git config (.gitignore + tracked file cleanup), and project docs (LICENSE + README).

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Security Crypto, Git

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `LICENSE` | Project root license file (GPL-3.0) |
| Create | `README.md` | Project description, setup, usage |
| Modify | `.gitignore` | Add missing security-relevant entries |
| Modify | `app/src/main/java/com/deedeedev/ytreader/ui/settings/SettingsAiScreen.kt` | Mask API key in UI |
| Modify | `app/src/main/java/com/deedeedev/ytreader/data/UserPreferencesRepository.kt` | Use EncryptedSharedPreferences, exclude API key from exports |
| Modify | `gradle/libs.versions.toml` | Add androidx.security dependency |
| Modify | `app/build.gradle.kts` | Add security-crypto dependency |
| Delete (git rm) | `.idea/AndroidProjectSystem.xml`, `.idea/appInsightsSettings.xml`, `.idea/deploymentTargetSelector.xml`, `.idea/deviceManager.xml`, `.idea/studiobot.xml`, `.idea/misc.xml`, `.idea/gradle.xml`, `.idea/compiler.xml`, `.idea/vcs.xml`, `.idea/migrations.xml`, `.idea/runConfigurations.xml` | Remove developer-specific IDE files from tracking |

---

### Task 1: Add GPL-3.0 LICENSE file

**Files:**
- Create: `LICENSE`

The extractor module uses GPL-3.0. Since the app links to it, the entire project must be GPL-3.0.

- [ ] **Step 1: Copy the extractor LICENSE to the project root**

The extractor already has a GPL-3.0 LICENSE at `extractor/LICENSE`. Copy it to the project root.

```bash
cp extractor/LICENSE LICENSE
```

- [ ] **Step 2: Verify the LICENSE file exists**

Run: `head -5 LICENSE`
Expected: First lines of GPL-3.0 text

- [ ] **Step 3: Commit**

```bash
git add LICENSE
git commit -m "chore: add GPL-3.0 license file at project root"
```

---

### Task 2: Create README.md

**Files:**
- Create: `README.md`

- [ ] **Step 1: Create the README.md**

```markdown
# YtReader

An Android app for reading YouTube video subtitles with AI-powered text cleaning.

## Features

- Browse and search YouTube videos
- Read and navigate subtitles with highlighting
- AI-powered subtitle text cleaning (OpenAI compatible API)
- Organize videos into collections
- Highlight and annotate text
- Home screen widget for quick access

## Tech Stack

- Kotlin + Jetpack Compose
- Room database
- OkHttp + Jsoup
- NewPipe Extractor
- WorkManager for background tasks

## Building

1. Clone the repository
2. Open in Android Studio
3. Build and run

Requirements:
- Android SDK 36 (compile)
- Min SDK 26
- JDK 11 (app module)

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.
```

- [ ] **Step 2: Verify the README renders correctly**

Run: `head -5 README.md`
Expected: README header and intro

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add project README"
```

---

### Task 3: Mask AI API key in Settings UI

**Files:**
- Modify: `app/src/main/java/com/deedeedev/ytreader/ui/settings/SettingsAiScreen.kt:67-74`

- [ ] **Step 1: Add PasswordVisualTransformation import**

At the top of `SettingsAiScreen.kt`, add the import:

```kotlin
import androidx.compose.ui.text.input.PasswordVisualTransformation
```

- [ ] **Step 2: Add visualTransformation to the API key OutlinedTextField**

In `SettingsAiScreen.kt`, change the `OutlinedTextField` for `aiApiKey` (lines 67-74) from:

```kotlin
OutlinedTextField(
    value = uiState.aiApiKey,
    onValueChange = { viewModel.setAiApiKey(it) },
    modifier = Modifier.fillMaxWidth(),
    label = { Text(stringResource(R.string.settings_ai_api_key)) },
    placeholder = { Text(stringResource(R.string.settings_ai_api_key_placeholder)) },
    singleLine = true
)
```

to:

```kotlin
OutlinedTextField(
    value = uiState.aiApiKey,
    onValueChange = { viewModel.setAiApiKey(it) },
    modifier = Modifier.fillMaxWidth(),
    label = { Text(stringResource(R.string.settings_ai_api_key)) },
    placeholder = { Text(stringResource(R.string.settings_ai_api_key_placeholder)) },
    visualTransformation = PasswordVisualTransformation(),
    singleLine = true
)
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/deedeedev/ytreader/ui/settings/SettingsAiScreen.kt
git commit -m "feat: mask AI API key in settings UI"
```

---

### Task 4: Exclude API key from preferences backup exports

**Files:**
- Modify: `app/src/main/java/com/deedeedev/ytreader/data/UserPreferencesRepository.kt:396-436`

The API key should NOT be exported in plaintext JSON backups. On import, preserve the existing key if the backup doesn't contain one.

- [ ] **Step 1: Modify exportPreferencesJson to exclude aiApiKey**

In `UserPreferencesRepository.kt`, change `exportPreferencesJson()` (lines 396-412) from:

```kotlin
fun exportPreferencesJson(): String {
    val backup = PreferencesBackup(
        favoriteLanguages = _favoriteLanguages.value,
        defaultFontSize = _defaultFontSize.value,
        fontFamily = _fontFamily.value,
        lineHeightMultiplier = _lineHeightMultiplier.value,
        progressIndicatorMode = _progressIndicatorMode.value.storageValue,
        appTheme = _appTheme.value.storageValue,
        appBrightness = _appBrightness.value,
        appLanguage = _appLanguage.value.storageValue,
        aiEndpoint = _aiEndpoint.value,
        aiApiKey = _aiApiKey.value,
        aiModel = _aiModel.value,
        aiPrompt = _aiPrompt.value
    )
    return gson.toJson(backup)
}
```

to:

```kotlin
fun exportPreferencesJson(): String {
    val backup = PreferencesBackup(
        favoriteLanguages = _favoriteLanguages.value,
        defaultFontSize = _defaultFontSize.value,
        fontFamily = _fontFamily.value,
        lineHeightMultiplier = _lineHeightMultiplier.value,
        progressIndicatorMode = _progressIndicatorMode.value.storageValue,
        appTheme = _appTheme.value.storageValue,
        appBrightness = _appBrightness.value,
        appLanguage = _appLanguage.value.storageValue,
        aiEndpoint = _aiEndpoint.value,
        aiApiKey = "",
        aiModel = _aiModel.value,
        aiPrompt = _aiPrompt.value
    )
    return gson.toJson(backup)
}
```

- [ ] **Step 2: Modify importPreferencesJson to skip empty api key**

Change `importPreferencesJson()` (lines 414-436) to only set the API key if the backup contains a non-empty value. Replace the current method body with:

```kotlin
fun importPreferencesJson(json: String): Boolean {
    val backup = try {
        gson.fromJson(json, PreferencesBackup::class.java)
    } catch (_: Exception) {
        null
    } ?: return false

    val editor = prefs.edit()
        .putStringSet(KEY_FAVORITE_LANGUAGES, backup.favoriteLanguages.toMutableSet())
        .putFloat(KEY_DEFAULT_FONT_SIZE, backup.defaultFontSize)
        .putString(KEY_FONT_FAMILY, backup.fontFamily)
        .putFloat(KEY_LINE_HEIGHT_MULTIPLIER, backup.lineHeightMultiplier)
        .putString(KEY_PROGRESS_INDICATOR_MODE, backup.progressIndicatorMode)
        .putString(KEY_APP_THEME, backup.appTheme)
        .putFloat(KEY_APP_BRIGHTNESS, backup.appBrightness)
        .putString(KEY_APP_LANGUAGE, backup.appLanguage)
        .putString(KEY_AI_ENDPOINT, backup.aiEndpoint)
        .putString(KEY_AI_MODEL, backup.aiModel)
        .putString(KEY_AI_PROMPT, backup.aiPrompt)

    if (backup.aiApiKey.isNotEmpty()) {
        editor.putString(KEY_AI_API_KEY, backup.aiApiKey)
    }

    editor.apply()
    return true
}
```

This way, existing exports with a key still import correctly, but new exports won't leak the key in plaintext. Old backups remain importable.

- [ ] **Step 3: Update the test for exportPreferencesJson**

Check the existing test for `exportPreferencesJson` in the test files. After the change, the exported JSON should contain `"aiApiKey": ""` instead of the actual key value.

Run: `./gradlew :app:testDebugUnitTest --tests "com.deedeedev.ytreader.*"`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/deedeedev/ytreader/data/UserPreferencesRepository.kt
git commit -m "security: exclude API key from plaintext backup exports"
```

---

### Task 5: Use EncryptedSharedPreferences for AI API key storage

**Files:**
- Modify: `gradle/libs.versions.toml` — add security-crypto version and library entry
- Modify: `app/build.gradle.kts` — add dependency
- Modify: `app/src/main/java/com/deedeedev/ytreader/data/UserPreferencesRepository.kt` — migrate API key storage

This task migrates the AI API key from regular SharedPreferences to EncryptedSharedPreferences so it's encrypted at rest on the device.

- [ ] **Step 1: Add security-crypto dependency to version catalog**

In `gradle/libs.versions.toml`, add to the `[versions]` section:

```toml
security-crypto = "1.1.0-alpha06"
```

Add to the `[libraries]` section:

```toml
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }
```

Note: Version `1.1.0-alpha06` is the latest that supports `minSdk = 23+` with Android Keystore. It's widely used in production apps.

- [ ] **Step 2: Add the dependency in app/build.gradle.kts**

In `app/build.gradle.kts`, add after the Room dependencies (around line 98):

```kotlin
    // Encrypted preferences for API key
    implementation(libs.security.crypto)
```

- [ ] **Step 3: Migrate API key to EncryptedSharedPreferences in UserPreferencesRepository**

In `UserPreferencesRepository.kt`, add imports:

```kotlin
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
```

Add a private property for encrypted prefs right after the existing `prefs` declaration (line 55). Change:

```kotlin
class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
```

to:

```kotlin
class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ytreader_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
```

Now update the three methods that read/write the API key to use `encryptedPrefs` instead of `prefs`:

1. **Load the key** — around line 179, change:
   ```kotlin
   _aiApiKey.value = prefs.getString(KEY_AI_API_KEY, "") ?: ""
   ```
   to:
   ```kotlin
   _aiApiKey.value = encryptedPrefs.getString(KEY_AI_API_KEY, "") ?: ""
   ```

2. **Set the key** — around line 249, change:
   ```kotlin
   prefs.edit().putString(KEY_AI_API_KEY, key).apply()
   ```
   to:
   ```kotlin
   encryptedPrefs.edit().putString(KEY_AI_API_KEY, key).apply()
   ```
   Note: also update the `_aiApiKey.value = key` assignment context.

3. **Import backup** — in the `importPreferencesJson` method, change the API key line from:
   ```kotlin
   editor.putString(KEY_AI_API_KEY, backup.aiApiKey)
   ```
   to use `encryptedPrefs` for the key write separately after `editor.apply()`:
   ```kotlin
   // After editor.apply() add:
   if (backup.aiApiKey.isNotEmpty()) {
       encryptedPrefs.edit().putString(KEY_AI_API_KEY, backup.aiApiKey).apply()
       _aiApiKey.value = backup.aiApiKey
   }
   ```
   And remove `putString(KEY_AI_API_KEY, backup.aiApiKey)` from the `editor` chain (it will no longer be in the regular prefs chain — this was already handled in Task 4).

- [ ] **Step 4: Build and run unit tests**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/deedeedev/ytreader/data/UserPreferencesRepository.kt
git commit -m "security: encrypt AI API key at rest using EncryptedSharedPreferences"
```

---

### Task 6: Improve .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add missing security-relevant entries**

Append the following lines to `.gitignore`:

```gitignore
# Certificate and key files
*.p12
*.pfx
*.pem

# Firebase / Google services
google-services.json

# Secret properties
secrets.properties
apikey.properties

# Logs
*.log

# IDE - track only shared code style configs
.idea/
!.idea/codeStyles/
!.idea/.gitignore

# Build artifacts
*.ap_
*.dex
```

Then remove the now-redundant individual `.idea/` exclusions that were previously in the file:
- `/.idea/caches`
- `/.idea/libraries`
- `/.idea/modules.xml`
- `/.idea/workspace.xml`
- `/.idea/navEditor.xml`
- `/.idea/assetWizardSettings.xml`

The final `.gitignore` should look like:

```gitignore
*.iml
.gradle
/local.properties
.DS_Store
build/
/captures
.externalNativeBuild
.cxx
local.properties
.kotlin
*.apk
*.aab
*.hprof
*.keystore
*.jks
.env
*.p12
*.pfx
*.pem
google-services.json
secrets.properties
apikey.properties
*.log
*.ap_
*.dex
.idea/
!.idea/codeStyles/
!.idea/.gitignore
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: improve .gitignore with security entries and IDE cleanup"
```

---

### Task 7: Remove tracked .idea files from git

This task must run AFTER Task 6 (so the updated .gitignore prevents re-adding them).

- [ ] **Step 1: Remove developer-specific .idea files from git tracking**

The `.idea/codeStyles/` directory and `.idea/.gitignore` should remain tracked. Remove everything else:

```bash
git rm -r --cached .idea/
git add .idea/
```

The `.gitignore` in `.idea/` already has entries for files that should not be committed. After `git rm -r --cached`, only files still matched by the `.idea/.gitignore` and our root `.gitignore` exceptions (`!.idea/codeStyles/` and `!.idea/.gitignore`) will remain tracked.

Then explicitly untrack the developer-specific files that were previously tracked:

```bash
git rm --cached .idea/AndroidProjectSystem.xml .idea/appInsightsSettings.xml .idea/compiler.xml .idea/deploymentTargetSelector.xml .idea/deviceManager.xml .idea/gradle.xml .idea/inspectionProfiles/Project_Default.xml .idea/misc.xml .idea/migrations.xml .idea/runConfigurations.xml .idea/studiobot.xml .idea/vcs.xml
```

- [ ] **Step 2: Verify only code styles remain tracked**

Run: `git ls-files .idea/`
Expected: Only `.idea/.gitignore` and `.idea/codeStyles/Project.xml` and `.idea/codeStyles/codeStyleConfig.xml`

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove developer-specific IDE files from git tracking"
```

---

## Self-Review Checklist

- [ ] **Spec coverage:** All 7 audit findings addressed (LICENSE, README, API key masking, backup exclusion, encrypted storage, .gitignore, .idea cleanup)
- [ ] **Placeholder scan:** No TBD/TODO placeholders — all steps contain actual code
- [ ] **Type consistency:** `PreferencesBackup.aiApiKey` stays as `String`, `EncryptedSharedPreferences` APIs match standard `SharedPreferences`, `PasswordVisualTransformation()` is a Compose foundation type
- [ ] **Dependency consistency:** `security-crypto` version `1.1.0-alpha06` is the latest stable-ish version supporting `minSdk = 23+` (we target 26, which is compatible)