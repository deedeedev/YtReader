# Piano di Implementazione: View Annotations

## Panoramica

Aggiungere una nuova view "Annotazioni" all'applicazione YtReader che mostri tutte le annotazioni (bookmarks, highlights e note) dei video presenti nella libreria e nelle collezioni.

---

## Scelte di Design

| Aspetto | Decisione |
|---|---|
| Sorgente dati | Libreria + Collezioni (tutti i video accessibili) |
| Posizione navigation | 5° bottom bar item: Library → Search → **Annotations** → Collections → Settings |
| Filtro tipo | Toggle chip identici a VideoNotesScreen (Bookmark / Highlight / Note) |
| Ricerca | Espandibile via icona lente, cerca in: note, testo highlight, titoli bookmark, titoli video, canali |
| Raggruppamento | Toggle opzionale (piatta cronologica / raggruppata per video) |
| Click annotazione | Navigazione diretta al Reader alla posizione |

---

## 1. Data Layer

### 1.1 Nuova Query in SubtitleDao

**File:** `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt`

Aggiungere query per ottenere tutti i sottotitoli accessibili (libreria + collezioni):

```kotlin
@Query("""
    SELECT DISTINCT s.* FROM subtitles s
    LEFT JOIN collection_videos cv ON s.videoId = cv.videoId
    WHERE s.isInLibrary = 1 OR cv.videoId IS NOT NULL
    ORDER BY s.lastOpenedAt DESC
""")
fun observeAllAccessibleSubtitles(): Flow<List<SubtitleEntity>>
```

**Nota:** Non servono modifiche a `BookmarkDao` o `HighlightNoteDao`: esistono già i metodi `observeBySubtitleIds(List<Long>)`.

---

## 2. ViewModel

### 2.1 Nuovo File: AnnotationsViewModel

**File:** `app/src/main/java/com/deedeedev/ytreader/ui/annotations/AnnotationsViewModel.kt`

#### 2.1.1 Enum e Modelli

```kotlin
enum class AnnotationType { BOOKMARK, HIGHLIGHT, NOTE }

enum class AnnotationSortOption { NEWEST, OLDEST, VIDEO_TITLE }

data class AnnotationItem(
    val id: Long,
    val type: AnnotationType,
    val title: String,            // titolo bookmark / testo evidenziato
    val noteText: String?,        // testo nota (per NOTE)
    val color: HighlightColor?,   // colore highlight
    val videoTitle: String,
    val channelName: String,
    val videoId: String,
    val subtitleId: Long,
    val createdAt: Long,
    val progressPercent: Int,
    val navigationTarget: ReaderAnnotationTarget
)

data class AnnotationGroup(
    val videoId: String,
    val videoTitle: String,
    val channelName: String,
    val items: List<AnnotationItem>
)

data class AnnotationCounts(
    val bookmarks: Int,
    val highlights: Int,
    val notes: Int
)
```

#### 2.1.2 UiState

```kotlin
data class AnnotationsUiState(
    val isLoading: Boolean = false,
    val typeFilter: Set<AnnotationType> = AnnotationType.values().toSet(),
    val sortOption: AnnotationSortOption = AnnotationSortOption.NEWEST,
    val groupByVideo: Boolean = true,
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false
)
```

#### 2.1.3 StateFlows Pubblici

- `uiState: StateFlow<AnnotationsUiState>` — stato principale
- `allItems: StateFlow<List<AnnotationItem>>` — tutti gli item (non filtrati)
- `filteredItems: StateFlow<List<AnnotationItem>>` — item filtrati per tipo, ordinati, con search
- `groupedItems: StateFlow<List<AnnotationGroup>>` — item raggruppati per video (solo se `groupByVideo = true`)
- `counts: StateFlow<AnnotationCounts>` — conteggi per tipo (su `allItems`)

#### 2.1.4 Flusso Reattivo

Pattern identico a `VideoNotesViewModel`:

1. `subtitleDao.observeAllAccessibleSubtitles()` → `Flow<List<SubtitleEntity>>`
2. `flatMapLatest` → `bookmarkDao.observeBySubtitleIds(ids)` + `highlightNoteDao.observeBySubtitleIds(ids)`
3. `combine` → `buildAnnotationItems(subtitles, bookmarks, notes)` → `List<AnnotationItem>`

**Logica di `buildAnnotationItems()`:**

- Per ogni `BookmarkEntity`: crea `AnnotationItem` con:
  - `type = AnnotationType.BOOKMARK`
  - `title = bookmark.title`
  - `navigationTarget = ReaderAnnotationTarget(subtitleId, bookmarkStart = bookmark.anchorStart)`

- Per ogni `HighlightNoteEntity`: cerca l'highlight corrispondente in `subtitle.highlights` (tramite `highlightStart`, `highlightEnd`):
  - Se esiste highlight con stessa range: `type = AnnotationType.NOTE`, `title = testo evidenziato`, `noteText = note.noteText`, `color = highlight.color`
  - Se non esiste highlight: ignora (nota orfana)

- Per ogni `SubtitleEntity` con `highlights` serializzati: estrai le highlighted ranges:
  - Se esiste `HighlightNoteEntity` per quel range: già processato sopra
  - Se non esiste nota: `type = AnnotationType.HIGHLIGHT`, `title = testo evidenziato`, `color = highlight.color`

- Calcola `progressPercent` per ogni item: `(startOffset / content.length) * 100`

#### 2.1.5 Filtro di Ricerca

Filtro case-insensitive `contains` su:
- `title` (titolo bookmark / testo evidenziato)
- `noteText`
- `videoTitle`
- `channelName`

```kotlin
private fun List<AnnotationItem>.applySearch(query: String): List<AnnotationItem> {
    if (query.isBlank()) return this
    val q = query.lowercase()
    return filter { item ->
        item.title.lowercase().contains(q) ||
        item.noteText?.lowercase()?.contains(q) == true ||
        item.videoTitle.lowercase().contains(q) ||
        item.channelName.lowercase().contains(q)
    }
}
```

#### 2.1.6 Metodi Pubblici (Actions)

```kotlin
fun toggleTypeFilter(type: AnnotationType)
fun setSortOption(option: AnnotationSortOption)
fun toggleGroupByVideo()
fun setSearchQuery(query: String)
fun toggleSearch()
fun deleteAnnotation(item: AnnotationItem)
fun restoreAnnotation(item: AnnotationItem)
```

#### 2.1.7 Dependency Injection

```kotlin
class AnnotationsViewModel(
    private val subtitleDao: SubtitleDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    companion object {
        fun provideFactory(
            subtitleDao: SubtitleDao,
            highlightNoteDao: HighlightNoteDao,
            bookmarkDao: BookmarkDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnnotationsViewModel(subtitleDao, highlightNoteDao, bookmarkDao) as T
            }
        }
    }
}
```

---

## 3. UI - AnnotationsScreen

### 3.1 Nuovo File: AnnotationsScreen

**File:** `app/src/main/java/com/deedeedev/ytreader/ui/annotations/AnnotationsScreen.kt`

#### 3.1.1 Struttura Composable

```
Scaffold(
    topBar = {
        AnnotationsTopBar(
            isSearchExpanded = uiState.isSearchExpanded,
            onToggleSearch = viewModel::toggleSearch,
            onSearchQueryChange = viewModel::setSearchQuery,
            groupByVideo = uiState.groupByVideo,
            onToggleGroupByVideo = viewModel::toggleGroupByVideo,
            sortOption = uiState.sortOption,
            onSortOptionChange = viewModel::setSortOption
        )
    }
) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Summary Row con conteggi (chip cliccabili per toggle filtro)
        AnnotationsSummaryRow(
            counts = counts,
            selectedTypes = uiState.typeFilter,
            onTypeToggle = viewModel::toggleTypeFilter
        )

        // Filter Row (toggle button group)
        AnnotationsFilterRow(
            selectedTypes = uiState.typeFilter,
            onTypeToggle = viewModel::toggleTypeFilter
        )

        // Lista contenuto
        if (groupedItems.isEmpty() && filteredItems.isEmpty()) {
            AnnotationsEmptyState(
                hasData = counts.total > 0,
                searchQuery = uiState.searchQuery
            )
        } else {
            LazyColumn {
                if (uiState.groupByVideo) {
                    groupedItems.forEach { group ->
                        stickyHeader {
                            VideoGroupHeader(
                                videoTitle = group.videoTitle,
                                channelName = group.channelName,
                                annotationCount = group.items.size
                            )
                        }
                        items(group.items, key = { it.key }) { item ->
                            AnnotationCard(
                                item = item,
                                showVideoInfo = false, // info nel header
                                onClick = onAnnotationClick,
                                onDelete = { viewModel.deleteAnnotation(item) }
                            )
                        }
                    }
                } else {
                    items(filteredItems, key = { it.key }) { item ->
                        AnnotationCard(
                            item = item,
                            showVideoInfo = true, // info nella card
                            onClick = onAnnotationClick,
                            onDelete = { viewModel.deleteAnnotation(item) }
                        )
                    }
                }
            }
        }
    }
}
```

#### 3.1.2 Componenti UI

**AnnotationsTopBar:**
- Titolo "Annotazioni" (usa `SmallTopAppBar`)
- Icona Search (lente) → toggle espansione campo ricerca
- Campo TextField espanso (con clear button)
- Icona Toggle (grid/list) per `groupByVideo`
- Icona Sort (3 punti o similar) → dropdown menu con opzioni

**AnnotationsSummaryRow:**
- Riga con 3 `FilterChip` cliclcabili: "12 Bookmarks" / "8 Highlights" / "5 Notes"
- Colore: chip selezionato ha background colorato
- Click → toggle del filtro corrispondente

**AnnotationsFilterRow:**
- Row con 3 `FilterChip` non selezionabili in modalità esclusiva
- Comportamento identico a `VideoNotesFilterRow` in `VideoNotesScreen.kt:622`

**AnnotationCard:**
- Riutilizza stile `VideoAnnotationCard` da `VideoNotesScreen.kt`
- Barra colorata laterale:
  - BOOKMARK: rosso
  - HIGHLIGHT: colore `item.color`
  - NOTE: colore `item.color`
- Contenuto:
  - Icona tipo + label (`stringResource(annotationTypeLabel(item.type))`)
  - Titulo item (testo highlight o titolo bookmark)
  - Nota (se presente, in stile quote/secondo piano)
  - Video info (titolo + canale) — solo in vista piatta
  - Progress indicator
- `SwipeToDismissBox` per delete con undo snackbar
- Click → `onAnnotationClick(item.navigationTarget)`

**VideoGroupHeader:**
- Sticky header con:
  - Video title (bold)
  - Channel name (secondary)
  - Badge con conteggio annotazioni

**AnnotationsEmptyState:**
- Se `counts.total == 0`: icona + "Nessuna annotazione" + descrizione
- Se `counts.total > 0` ma filtered empty: icona + "Nessun risultato" + suggerimento di modificare filtri

#### 3.1.3 Navigazione

```kotlin
@Composable
fun AnnotationsScreen(
    viewModel: AnnotationsViewModel,
    onAnnotationClick: (ReaderAnnotationTarget) -> Unit,
    modifier: Modifier = Modifier
)
```

Il callback `onAnnotationClick` viene passato da `MainScreen` e naviga al Reader:

```kotlin
onAnnotationClick = { target ->
    navController.navigate(
        Screen.Reader.createRoute(
            subtitleId = target.subtitleId,
            highlightStart = target.highlightStart,
            highlightEnd = target.highlightEnd,
            bookmarkStart = target.bookmarkStart
        )
    )
}
```

---

## 4. Modifiche a MainScreen

### 4.1 Screen Sealed Class

**File:** `app/src/main/java/com/deedeedev/ytreader/ui/MainScreen.kt`

Aggiungere nuovo oggetto alla sealed class `Screen`:

```kotlin
object Annotations : Screen(
    route = "annotations",
    labelRes = R.string.screen_annotations,
    icon = Icons.Default.EditNote  // o Icons.Default.NoteAlt
)
```

Posizione: dopo `Search`, prima di `Collections`.

### 4.2 Bottom Bar

Aumentare da 4 a 5 item:

```kotlin
val bottomBarScreens = listOf(
    Screen.Library,
    Screen.Search,
    Screen.Annotations,  // NUOVO
    Screen.Collections,
    Screen.Settings
)
```

### 4.3 AnnotationsViewModel

Creare il ViewModel in `MainScreen` (stesso pattern di `HomeViewModel`):

```kotlin
val annotationsViewModel: AnnotationsViewModel = viewModel(
    factory = AnnotationsViewModel.provideFactory(
        appContainer.subtitleDao,
        appContainer.highlightNoteDao,
        appContainer.bookmarkDao
    )
)
```

### 4.4 NavHost

Aggiungere composable per la nuova route:

```kotlin
composable(
    route = Screen.Annotations.route,
    enterTransition = null,
    exitTransition = null
) {
    AnnotationsScreen(
        viewModel = annotationsViewModel,
        onAnnotationClick = { target ->
            navController.navigate(
                Screen.Reader.createRoute(
                    subtitleId = target.subtitleId,
                    highlightStart = target.highlightStart,
                    highlightEnd = target.highlightEnd,
                    bookmarkStart = target.bookmarkStart
                )
            )
        }
    )
}
```

---

## 5. Stringhe

### 5.1 File: res/values/strings.xml

Aggiungere:

```xml
<string name="screen_annotations">Annotations</string>
<string name="annotations_empty">No annotations yet</string>
<string name="annotations_empty_description">Add bookmarks and highlights while reading to see them here</string>
<string name="annotations_no_results">No annotations match your filters</string>
<string name="annotations_no_results_description">Try adjusting your search or filters</string>
<string name="annotations_search_hint">Search annotations…</string>
<string name="annotations_sort_newest">Newest first</string>
<string name="annotations_sort_oldest">Oldest first</string>
<string name="annotations_sort_video">By video title</string>
<string name="annotations_group_by_video">Group by video</string>
<string name="annotations_flat_view">Flat view</string>
<string name="annotations_bookmarks">Bookmarks</string>
<string name="annotations_highlights">Highlights</string>
<string name="annotations_notes">Notes</string>
<string name="annotations_deleted">Annotation deleted</string>
<string name="annotations_undo">Undo</string>
```

---

## 6. Riepilogo File

| Azione | File |
|---|---|
| **Nuovo** | `app/src/main/java/com/deedeedev/ytreader/ui/annotations/AnnotationsViewModel.kt` |
| **Nuovo** | `app/src/main/java/com/deedeedev/ytreader/ui/annotations/AnnotationsScreen.kt` |
| **Modifica** | `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt` (+1 query) |
| **Modifica** | `app/src/main/java/com/deedeedev/ytreader/ui/MainScreen.kt` (Screen, bottom bar, NavHost, ViewModel) |
| **Modifica** | `app/src/main/res/values/strings.xml` (nuove stringhe) |

---

## 7. Note Implementative

### 7.1 Riutilizzo Codice

La funzione `buildAnnotationItems()` in `VideoNotesViewModel` e la nuova in `AnnotationsViewModel` sono molto simili. Per una prima implementazione, si consiglia di **duplicare il pattern** per mantenere i due ViewModel indipendenti. In futuro si può estrarre la logica comune in una utility nel domain layer.

### 7.2 Prestazioni

Per librerie con molti video, il caricamento di tutte le annotazioni potrebbe essere oneroso. Questa implementazione carica tutto in memoria; una versione futura potrebbe aggiungere paginazione.

### 7.3 Dipendenze Esistenti

Non servono nuove dipendenze. Si usano:
- Room (`Flow` dalle query DAO)
- Kotlin Coroutines (`flatMapLatest`, `combine`, `stateIn`)
- Jetpack Compose UI (già nel progetto)
- Material3 (`FilterChip`, `SwipeToDismissBox`, etc.)

---

## 8. Test

### 8.1 Test Unitari

- `AnnotationsViewModel`: test dei filtri (tipo, sort, search), test di `buildAnnotationItems()` con dati mock
- Verifica che `filteredItems` applichi correttamente tutti i filtri combinati

### 8.2 Test Manuali

- Creare annotazioni in più video nella libreria
- Verificare che appaiano nella nuova view
- Testare filtri (tipo, search, sort)
- Testare toggle raggruppamento
- Testare navigazione al Reader
- Testare cancellazione con undo

---

## 9. Checklist Implementazione

- [ ] Aggiungere query `observeAllAccessibleSubtitles()` in `SubtitleDao.kt`
- [ ] Creare `AnnotationsViewModel.kt` con UiState, modelli, e logica reattiva
- [ ] Creare `AnnotationsScreen.kt` con TopBar, SummaryRow, FilterRow, LazyColumn
- [ ] Aggiungere `Screen.Annotations` in `MainScreen.kt`
- [ ] Aggiornare bottom bar items
- [ ] Aggiungere composable in NavHost
- [ ] Aggiungere stringhe in `strings.xml`
- [ ] Testare l'implementazione
