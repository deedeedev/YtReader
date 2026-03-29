# Piano di Ottimizzazione Performance Reader (Livello 1)

## Diagnosi del Problema

Il `JustifiedStudyTextView` ha un **problema architetturale fondamentale** che causa tutti i sintomi descritti:

> `rebuildDisplayText()` crea una nuova `SpannableString` dell'intero testo, applica tutti gli span, poi chiama `requestLayout()` → `onMeasure()` → `rebuildLayout()` → `StaticLayout.build()` con `BREAK_STRATEGY_HIGH_QUALITY` + giustificazione + sillabazione. **Questo avviene ad ogni `ACTION_MOVE` durante la selezione e ad ogni ricomposizione di Compose durante lo scroll.**

Il costo di `StaticLayout.build()` su un testo di 50.000+ caratteri con `BREAK_STRATEGY_HIGH_QUALITY` è circa **10-50ms per invocazione**. A 60fps il budget per frame è 16ms. Risultato: frame saltati = UI scattosa.

### La Catena di Eventi durante lo Scroll

1. Scroll → `studyScrollState.value` cambia → `derivedStateOf` ricalcola `fullscreenProgressPercent` (Int 0-100)
2. Quando il valore cambia (circa 100 volte in uno scroll completo) → `ReaderScreen` ricompone
3. Ricomposizione genera nuove lambda → `ReaderStudyPane` ricompone → `update` block di AndroidView
4. `bindStudyContent()` chiama `setTextSizeSp()` + `applyTypeface()` + `setContentWithHighlights()`
5. Ognuno di questi 3 metodi chiama `rebuildDisplayText()` → **3x `StaticLayout.build()` completo**

### Tabella dei Colli di Bottiglia

| Collo di bottiglia | Dove | Impatto |
|---|---|---|
| `StaticLayout` monolitico per tutto il testo | `JustifiedStudyTextView.kt:452-463` | Critico |
| `requestLayout()` su ogni cambio selezione | `JustifiedStudyTextView.kt:448` | Critico |
| `BREAK_STRATEGY_HIGH_QUALITY` su tutto il testo | `JustifiedStudyTextView.kt:459` | Alto |
| Nessuna virtualizzazione (`verticalScroll`) | `ReaderContentPanes.kt:298-359` | Alto |
| Nuova `SpannableString` su ogni update | `JustifiedStudyTextView.kt:414` | Medio |

---

## Fasi di Implementazione

### Fase A — Separare Layout da Visualizzazione (Core Change)

**Principio**: Il `StaticLayout` viene costruito una sola volta (a caricamento o cambio contenuto/font). Le evidenziazioni e la selezione sono disegnate come rettangoli in `onDraw()` senza mai toccare il layout.

#### A1. Nuovi campi in `JustifiedStudyTextView`

Aggiungere:

```kotlin
private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}
private var cachedLayoutWidth = -1
private var layoutDirty = true
```

Rimuovere:

```kotlin
private var displayText: CharSequence = ""  // ELIMINARE
```

#### A2. Rimuovere `rebuildDisplayText()`

Eliminare completamente il metodo `rebuildDisplayText()` (righe 413-449).

#### A3. Modificare `rebuildLayout()`

Cambiare da `displayText` a `content` (plain String, senza span):

```kotlin
// Prima:
StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, width)

// Dopo:
StaticLayout.Builder.obtain(content, 0, content.length, textPaint, width)
```

#### A4. Ottimizzare `onMeasure()` con cache

```kotlin
override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val resolvedWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
    val availableWidth = (resolvedWidth - paddingLeft - paddingRight).coerceAtLeast(0)
    if (availableWidth != cachedLayoutWidth || layoutDirty) {
        rebuildLayout(availableWidth)
        cachedLayoutWidth = availableWidth
        layoutDirty = false
    }
    val contentHeight = (layout?.height ?: 0) + paddingTop + paddingBottom + extraHandleBottomInset()
    val resolvedHeight = resolveSize(contentHeight.toInt(), heightMeasureSpec)
    setMeasuredDimension(resolvedWidth, resolvedHeight)
}
```

#### A5. Riscrivere `onDraw()` — disegno manuale di evidenziazioni e selezione

Nuovo ordine di disegno:

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (backgroundColorInt != Color.TRANSPARENT) {
        canvas.drawColor(backgroundColorInt)
    }
    val textLayout = layout ?: return
    canvas.save()
    canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

    // 1. Sfondi dietro al testo
    drawHighlightBackgrounds(canvas, textLayout)
    drawSearchResultBackground(canvas, textLayout)
    drawSelectionBackground(canvas, textLayout)

    // 2. Testo (nessuno span, solo glyph)
    textLayout.draw(canvas)

    // 3. Overlay sopra il testo
    drawBookmarkIndicators(canvas, textLayout)
    drawHighlightNoteIndicators(canvas, textLayout)
    selectionHandleVisuals(textLayout).forEach { handle ->
        drawHandle(canvas, handle)
    }
    canvas.restore()
}
```

#### A6. Nuovo metodo `drawRangeBackground()`

```kotlin
private fun drawRangeBackground(
    canvas: Canvas,
    textLayout: StaticLayout,
    startOffset: Int,
    endOffset: Int,
    color: Int
) {
    if (startOffset >= endOffset || content.isEmpty()) return
    rangePaint.color = color
    val startLine = textLayout.getLineForOffset(startOffset)
    val endCharOffset = (endOffset - 1).coerceIn(0, content.length - 1)
    val endLine = textLayout.getLineForOffset(endCharOffset)
    for (line in startLine..endLine) {
        val left = if (line == startLine) {
            textLayout.getPrimaryHorizontal(startOffset)
        } else {
            textLayout.getLineLeft(line)
        }
        val right = if (line == endLine) {
            if (endOffset >= content.length) textLayout.getLineRight(line)
            else textLayout.getPrimaryHorizontal(endOffset)
        } else {
            textLayout.getLineRight(line)
        }
        val top = textLayout.getLineTop(line).toFloat()
        val bottom = textLayout.getLineBottom(line).toFloat()
        if (right > left) {
            canvas.drawRect(left, top, right, bottom, rangePaint)
        }
    }
}
```

#### A7. Metodi helper per il disegno

```kotlin
private fun drawHighlightBackgrounds(canvas: Canvas, textLayout: StaticLayout) {
    highlights.forEach { highlight ->
        val color = when (highlight.color) {
            HighlightColor.RED -> redColor
            HighlightColor.BLUE -> blueColor
            HighlightColor.GREEN -> greenColor
            HighlightColor.YELLOW -> yellowColor
        }
        drawRangeBackground(canvas, textLayout, highlight.start, highlight.end, color)
    }
}

private fun drawSearchResultBackground(canvas: Canvas, textLayout: StaticLayout) {
    searchResultRange?.let { range ->
        drawRangeBackground(canvas, textLayout, range.start, range.end, searchResultColor)
    }
}

private fun drawSelectionBackground(canvas: Canvas, textLayout: StaticLayout) {
    selectionRange?.let { range ->
        drawRangeBackground(canvas, textLayout, range.start, range.end, selectionColor)
    }
}
```

---

### Fase B — Eliminare i Rebuild Non Necessari

Ogni metodo che chiama `rebuildDisplayText()` va aggiornato per non fare mai `requestLayout()` per cambi puramente visivi.

#### B1. `updateSelectionRange()` (riga 403) — IL METODO CRITICO

```kotlin
// Prima:
private fun updateSelectionRange(range: TextRange) {
    if (range.end <= range.start) { clearSelection(); return }
    selectionRange = range
    rebuildDisplayText()                    // ← ELIMINARE
    onSelectionChangedListener?.invoke(range.start, range.end)
}

// Dopo:
private fun updateSelectionRange(range: TextRange) {
    if (range.end <= range.start) { clearSelection(); return }
    selectionRange = range
    invalidate()                            // ← solo redraw, niente layout
    onSelectionChangedListener?.invoke(range.start, range.end)
}
```

#### B2. `clearSelection()` (riga 222)

```kotlin
// Prima:
fun clearSelection() {
    selectionRange = null
    selectionAnchor = null
    activeHandle = null
    rebuildDisplayText()                    // ← ELIMINARE
    onSelectionChangedListener?.invoke(0, 0)
}

// Dopo:
fun clearSelection() {
    selectionRange = null
    selectionAnchor = null
    activeHandle = null
    invalidate()                            // ← solo redraw
    onSelectionChangedListener?.invoke(0, 0)
}
```

#### B3. `setContentWithHighlights()` (riga 178)

```kotlin
internal fun setContentWithHighlights(
    content: String,
    highlights: List<TextHighlight>,
    bookmarks: List<BookmarkEntity>,
    searchResultRange: SelectionRange?,
    redColor: Int, blueColor: Int, greenColor: Int, yellowColor: Int,
    searchResultColor: Int
) {
    val contentChanged = this.content != content
    this.content = content
    this.highlights = highlights.mapNotNull { /* stesso codice di prima */ }
    this.bookmarks = bookmarks.mapNotNull { /* stesso codice di prima */ }
    this.redColor = redColor
    this.blueColor = blueColor
    this.greenColor = greenColor
    this.yellowColor = yellowColor
    this.searchResultColor = searchResultColor
    this.searchResultRange = searchResultRange?.let { /* stesso codice di prima */ }
    selectionRange = selectionRange?.let { /* stesso codice di prima */ }

    if (contentChanged) {
        layoutDirty = true
        requestLayout()
    }
    invalidate()
}
```

#### B4. `setTextSizeSp()`, `setLineHeightMultiplier()`, `applyTypeface()`

```kotlin
fun setTextSizeSp(size: Float) {
    val newSize = spToPx(size)
    if (textPaint.textSize == newSize) return
    textPaint.textSize = newSize
    layoutDirty = true
    requestLayout()
    invalidate()
}

fun setLineHeightMultiplier(multiplier: Float) {
    if (lineSpacingMultiplier == multiplier) return
    lineSpacingMultiplier = multiplier
    layoutDirty = true
    requestLayout()
    invalidate()
}

fun applyTypeface(fontFamilyName: String) {
    val newTypeface = when (fontFamilyName) {
        "Serif" -> Typeface.SERIF
        "SansSerif" -> Typeface.SANS_SERIF
        "Monospace" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }
    if (textPaint.typeface == newTypeface) return
    textPaint.typeface = newTypeface
    layoutDirty = true
    requestLayout()
    invalidate()
}
```

---

### Fase C — Ottimizzazione Compose Accessoria (Opzionale)

Riduce la **frequenza** delle ricomposizioni, complementare alle Fasi A/B che riducono il **costo**.

#### C1. Rimuovere chiavi `remember` ridondanti in `ReaderScreen.kt`

**Righe 1037-1078** (`fullscreenProgressPercent`):

```kotlin
// Prima:
val fullscreenProgressPercent by remember(
    readerMode,
    originalSegments,
    originalListState.firstVisibleItemIndex,
    originalListState.canScrollForward,
    originalListState.canScrollBackward,
    studyScrollState.value,           // ← RIMUOVERE
    studyScrollState.maxValue,
    originalFallbackScrollState.value, // ← RIMUOVERE
    originalFallbackScrollState.maxValue
) { derivedStateOf { ... } }

// Dopo:
val fullscreenProgressPercent by remember(
    readerMode,
    originalSegments
) {
    derivedStateOf { ... }
}
```

**Righe 1080-1121** (`fullscreenPageProgress`): rimuovere `studyScrollState.value`, `originalListState.firstVisibleItemIndex`, `originalListState.layoutInfo.visibleItemsInfo.size`, `originalFallbackScrollState.value`. Tenere solo `readerMode`, `originalSegments`, `studyViewportHeightPx`, `originalFallbackViewportHeightPx`.

> **Nota**: `derivedStateOf` traccia automaticamente le letture di stato nel suo lambda. Le chiavi ridondanti possono essere rimosse. Le chiavi che non sono letture di stato Compose (come `originalSegments`) devono restare.

---

## Riepilogo delle Modifiche per File

| File | Modifiche | Impatto |
|---|---|---|
| `JustifiedStudyTextView.kt` | ~12 metodi modificati/aggiunti. Il 90% del lavoro è qui. | Elimina `StaticLayout.build()` da selezione e scroll. |
| `ReaderScreen.kt` | Rimozione chiavi `remember` ridondanti (Fase C, opzionale). | Riduce frequenza ricomposizioni. |
| `ReaderAndroidViewBindings.kt` | Nessuna modifica necessaria. | I metodi di binding chiamano gli stessi metodi, ora ottimizzati. |
| `ReaderContentPanes.kt` | Nessuna modifica necessaria. | |
| `SelectableHighlightTextView.kt` | Nessuna modifica (max priority = Study Mode). | |

---

## Cosa NON Cambia

- Tutte le feature esistenti sono preservate:
  - Selezione testo con handle custom (drag START/END)
  - Evidenziazioni in 4 colori con indicatori note
  - Bookmark con marker visivi nel margine
  - Ricerca con evidenziazione risultati + navigazione
  - Find & Replace interattivo
  - Tap zones (sinistra/centro/destra) + tap angolo bookmark
  - Gesture luminosità
  - Font size/family/line height
  - Testo giustificato
  - Progresso lettura (% + pagine)
  - Persistenza posizione scroll
  - Modalità editing
  - Jump-back navigation
  - AI cleaning
  - Toggle Study/Original mode
- Nessun cambiamento alla API pubblica di `JustifiedStudyTextView`
- Nessun cambiamento a `ReaderAndroidViewBindings.kt` o `ReaderContentPanes.kt`
- Nessun cambiamento al `ReaderViewModel` o al data layer

---

## Risultato Atteso

| Operazione | Prima | Dopo |
|---|---|---|
| Selezione testo (per frame) | ~50ms (`StaticLayout.build`) | <1ms (`invalidate` + `drawRect`) |
| Latenza selezione | ~1s | Istantanea |
| Scroll (per ricomposizione) | 3x `StaticLayout.build` | 0x |
| Frame rate scroll | ~20fps | 60fps |
| Evidenziazione / note / bookmark | Stesso problema | Stessa improvement |
| Caricamento iniziale | Invariato | Invariato |

---

## Strategia di Implementazione

1. **Fase A + B insieme**: Le Fasi A e B sono interdipendenti (A introduce il nuovo draw, B rimuove le chiamate a `rebuildDisplayText`). Implementare together in un singolo commit.

2. **Test manuale**: Dopo le Fasi A+B, testare:
   - Aprire un testo di 60+ pagine
   - Verificare scroll fluido
   - Verificare selezione reattiva
   - Verificare evidenziazioni visivamente identiche a prima
   - Verificare tutte le altre feature (bookmark, note, ricerca, find/replace)

3. **Fase C (opzionale)**: Solo se dopo A+B persistono problemi di scroll a livello Compose.

---

## Feature da Preservare (Checklist)

- [ ] Selezione testo con handle custom (drag START/END)
- [ ] Evidenziazioni in 4 colori con indicatori note
- [ ] Bookmark con marker visivi nel margine
- [ ] Ricerca con evidenziazione risultati + navigazione
- [ ] Find & Replace interattivo
- [ ] Tap zones (sinistra/centro/destra) + tap angolo bookmark
- [ ] Gesture luminosità
- [ ] Font size/family/line height
- [ ] Testo giustificato
- [ ] Progresso lettura (% + pagine)
- [ ] Persistenza posizione scroll
- [ ] Modalità editing
- [ ] Jump-back navigation
- [ ] AI cleaning
- [ ] Toggle Study/Original mode
