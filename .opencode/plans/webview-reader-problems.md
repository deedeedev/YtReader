# Piano di Risoluzione Problemi Reader WebView

## Problema 1: Posizione nel testo non persistente

### Diagnosi
- La posizione di scroll dovrebbe essere salvata tramite `updateLastStudyScroll` nel database (`lastStudyScroll` nella tabella `subtitle_reading_states`)
- Il problema è che il WebView non comunica la posizione di scroll al ViewModel quando l'utente legge
- In `WebViewStudyContentPane.kt:104-107`, `onScrollProgress` viene chiamato ma non触发 il salvataggio
- La funzione `persistReadingProgress` in `ReaderScreen.kt:254-267` calcola la posizione in percentuale ma non viene mai chiamata automaticamente durante la navigazione

### Soluzione
1. **Aggiungere metodo JS per ottenere la posizione di scroll**: In `reader.js` aggiungere funzione che espone `getCharOffsetAtTop()` o usa `scrollY` corrente
2. **Modificare `WebViewReaderJs.kt`**: Aggiungere metodo `getCharOffsetAtPosition(position: Int)` che chiama JS e restituisce l'offset carattere
3. **Modificare `ReaderScreen.kt`**: Hookup di `persistReadingProgress` per essere chiamato:
   - Quando l'utente esce dal reader (`onDispose` o al `onBack`)
   - Periodicamente durante la lettura (es. ogni 5 secondi se c'è attività di scroll)
   - Quando cambia modalità reader
4. **Verificare restore**: Assicurarsi che `initialScrollPercent` in `WebViewStudyContentPane.kt:204-218` funzioni correttamente con la percentuale salvata

---

## Problema 2: Bookmark non viene aggiunto

### Diagnosi
- La funzione `isBookmarkCornerTap` in `ReaderScreenController.kt:19-23` controlla se il tap è nell'angolo in alto a destra
- Quando rilevato, `handleReaderTap` in `ReaderScreen.kt:913-916` chiama `openBookmarkDialog()`
- Il dialog viene aperto ma manca l'**anchorStart** (posizione del bookmark nel testo)
- Il codice non passa la posizione corrente al dialog quando si crea un nuovo bookmark
- L'attributo `pendingBookmarkAnchorStart` in `ReaderScreen.kt:202` esiste ma non viene popolato

### Soluzione
1. **Modificare `reader.js`**: Aggiungere funzione `getCharOffsetAtPosition(y: Int)` o usare `getCharOffsetAtTop()` per ottenere l'offset dalla posizione corrente dello scroll
2. **Modificare `WebViewReaderJs.kt`**: Aggiungere wrapper per chiamare questa funzione
3. **Modificare `ReaderScreen.kt`**: 
   - In `handleReaderTap`, quando `isBookmarkCornerTap` è true:
     - Ottenere l'offset carattere dalla posizione corrente di scroll
     - Popolare `pendingBookmarkAnchorStart` con questo valore
     - Generare un fallback title dal testo circostante (`pendingBookmarkFallbackTitle`)
   - Verificare che `openBookmarkDialog()` usi questi valori per creare il bookmark

---

## Problema 3: Highlight non viene creato / Annotation bar non scompare

### Diagnosi
- La selezione di testo nel WebView attiva `Bridge.onSelectionChanged` (chiamato da `reader.js:293`)
- `WebViewStudyContentPane.kt:79-85` gestisce questo evento e chiama `onSelectionRangeChanged`
- `ReaderScreen.kt:1161-1173` gestisce `onSelectionRangeChanged` e imposta `selectionRange`
- `showSelectionToolbar` viene calcolato in `ReaderScreen.kt:1059-1062` e dovrebbe mostrare la toolbar
- Il problema è che quando l'utente seleziona un colore in `HighlightSelectionToolbar`:
  - `onSelectionColorSelected` viene chiamato (`ReaderScreen.kt:1311-1321`)
  - Se non c'è `activeHighlight`, chiama `viewModel.applyHighlight(...)`
  - Dopo, imposta `selectionRange = null` e chiama `studyTextView?.clearSelection()`
  - **Problema**: `studyTextView` è un AndroidView (JustifiedStudyTextView), non il WebView!
  - Il WebView non riceve l'istruzione di deselezionare il testo

### Soluzione
1. **Modificare `WebViewReaderJs.kt`**: Aggiungere `clearSelection()` che chiama `clearSelection()` in JS
2. **Modificare `ReaderScreen.kt`**: 
   - Dopo `viewModel.applyHighlight()`, chiamare anche la funzione per deselezionare nel WebView
   - Servirà un riferimento al WebView (già esiste `webViewStudyRef`)
   - Importare `WebViewReaderJs` e chiamare `WebViewReaderJs.clearSelection(webViewStudyRef)`
3. **Annotation bar non scompare**:
   - Dopo che l'highlight viene creato, `selectionRange` viene impostato a `null`
   - La toolbar dovrebbe nascondersi automaticamente perché `showSelectionToolbar` dipende da `selectionRange`
   - Verificare che `suppressSelectionToolbar` non sia accidentalmente `true`
   - Potrebbe essere necessario chiamare anche `clearSearchResultsMode()` per pulire lo stato

---

## Riepilogo Modifiche

### File `reader.js` (assets/reader/)
- Aggiungere funzione per ottenere char offset da posizione Y

### File `WebViewReaderJs.kt`
- Aggiungere `clearSelection()` per deselezionare nel WebView

### File `ReaderScreen.kt`
- Hookup `persistReadingProgress` per essere chiamato automaticamente
- Popolare `pendingBookmarkAnchorStart` quando si aggiunge bookmark dal tap corner
- Chiamare deselezione nel WebView dopo creazione highlight

### File `WebViewStudyContentPane.kt`
- Assicurarsi che `initialScrollPercent` funzioni correttamente

---

## Note Aggiuntive
- I test esistenti per il reader (`ReaderScreenTest`) dovrebbero coprire questi casi dopo le modifiche
- Verificare che il comportamento sia consistente con la vecchia implementazione AndroidView (JustifiedStudyTextView)
- Considerare di aggiungere test specifici per WebView interaction se non già presenti
