# Fix: Reader text shifting when overlay appears (Option A - Transparent Bars)

## Problem
When the reader UI overlay appears/disappears, the text in the WebView shifts up then down.

## Root Cause
`ReaderSystemBarsEffect` toggles system bar visibility via `insetsController.show()/hide()`. This causes:
1. **Up-shift**: System bars appearing causes the WebView layout to change (Scaffold `innerPadding` changes, or WebView internal viewport adjusts)
2. **Down-shift**: CSS `env(safe-area-inset-top)` changes from 0 to status bar height, adding padding

## Solution: Option A - Transparent bars (always visible)

Instead of hiding/showing system bars, keep them always visible but transparent. No layout changes = no shift.

### Changes

#### 1. `ReaderScreenEffects.kt` - Remove bar toggling
Remove the `show()/hide()` calls in `ReaderSystemBarsEffect`. Keep the behavior setting and the cleanup.

```kotlin
// Remove:
if (isUiVisible || isEditing) {
    insetsController.show(WindowInsetsCompat.Type.systemBars())
} else {
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
}

// Keep:
insetsController.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
```

#### 2. `themes.xml` - Make bars transparent
Add transparent status/nav bar attributes to the theme:
```xml
<item name="android:statusBarColor">@android:color/transparent</item>
<item name="android:navigationBarColor">@android:color/transparent</item>
```

#### 3. CSS and WebView - Keep current safe-area padding
The `env(safe-area-inset-top)` in `reader.css` will now always return the same value since bars are always visible.

### Trade-off
- Status bar icons (time, battery) are always visible while reading
- Pro: Simplest fix, guaranteed no shift
- Con: Not fully immersive
