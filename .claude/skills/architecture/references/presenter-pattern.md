# Presenter Pattern — Canonical Example

Adapted from Doximity's two articles, "Simplifying State Management with Compose" and "Building a
Note-Taking App in Compose". Compose runs both the view and the presentation layer: a presenter is
a class with a `@Composable present(params)` that returns an immutable `UiModel`. State lives in the
Compose runtime, so there is no ViewModel (D-02).

This is a reference sketch, not compiled code: the project has no Compose yet. The
`molecule-presenter-harness` slice turns the contracts below into real code in `:core:ui`, with a
passing Molecule test. After that, the code in the repository is the source of truth; update this
file to match it rather than letting the two drift.

## What Changed From The Articles

| Article | Here | Why |
|---|---|---|
| `EventHandler.equals` compares `key`, `hashCode` uses `handle` | Both derive from `key` | Equal objects must have equal hash codes; the original breaks hash-based collections and Compose's equality checks (bitácora 3.5) |
| View calls `uiModel.events(event)` with no `invoke` declared | `operator fun invoke` is declared | The article's call does not compile (bitácora 3.5) |
| `data class EventHandler` | Plain `class` with an explicit `toString` | A data class would also generate `copy`/`componentN` around a lambda, and its equality is overridden anyway |
| `RecompositionClock.Immediate` | `RecompositionMode.Immediate` | Renamed in current Molecule |
| Use cases with `operator fun invoke` | Repository interfaces in `:core:data` | The project's mutation contract is repository functions (D-13) |
| Mockposable to mock child presenters | Real child presenter with fake repositories | One less compiler plugin; fakes also exercise the child |
| `produceState` per flow | One `combine`d flow with `collectAsState(null)` | One emission per data change keeps Molecule tests deterministic |

## Contracts — `:core:ui`, package `presenter`

```kotlin
@Immutable
interface UiModel

interface UiEvent

interface Presenter<Model : UiModel, Params> {
    @Composable
    fun present(params: Params): Model
}

/**
 * Wraps an event lambda so a UiModel stays comparable. Two handlers are equal when their keys are
 * equal, which lets tests compare a whole UiModel against one built with `EventHandler {}`.
 * Give a key only when a handler is the sole changing property of a model.
 */
@Immutable
class EventHandler<E : UiEvent>(
    private val key: Any? = null,
    val handle: (E) -> Unit,
) {
    operator fun invoke(event: E) = handle(event)

    override fun equals(other: Any?): Boolean = other is EventHandler<*> && key == other.key

    override fun hashCode(): Int = key?.hashCode() ?: 0

    override fun toString(): String = "EventHandler(key=$key)"
}
```

## Example — Next Jam Screen

The admin is not a separate module or screen. It is state inside the feature: the same presenter
reads the admin flag and adds admin events and controls to the same `UiModel`. Mutations go
through repository functions (D-13); the flag only decides what is drawn, and Apps Script still
authorizes every write.

The repository names below are illustrative; the real ones come from their own slices.

### UiModels — `:feature:next-jam`

```kotlin
sealed interface NextJamUiModel : UiModel {
    data object Loading : NextJamUiModel

    /** Musicians see date and venue, and that the list is being assembled — not an empty list. */
    data class Draft(val date: String, val venue: String) : NextJamUiModel

    data class Data(
        val date: String,
        val venue: String,
        val isAdmin: Boolean,
        val isPublished: Boolean,
        val publishError: String?,
        val rows: List<SongRowUiModel>,
        val events: EventHandler<Event>,
    ) : NextJamUiModel {
        sealed interface Event : UiEvent {
            data object Publish : Event
            data object DismissPublishError : Event
        }
    }
}

data class SongRowUiModel(
    val position: Int,
    val title: String,
    val key: String,
    val expanded: Boolean,
    val slots: List<SlotUiModel>,
    val canEdit: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object ToggleExpanded : Event
        data class ClearSlot(val slotIndex: Int) : Event
    }
}

/** `index` is the slot's position in the JamSong lineup, kept so display order never breaks events. */
data class SlotUiModel(
    val index: Int,
    val instrument: Instrument,
    val musicianName: String?,
) : UiModel {
    val isOpen: Boolean get() = musicianName == null
}
```

### Child presenter — one row

```kotlin
class SongRowPresenter(
    private val setlistRepository: SetlistRepository,
) : Presenter<SongRowUiModel, SongRowPresenter.Params> {

    data class Params(val jamSong: JamSong, val title: String, val isAdmin: Boolean)

    @Composable
    override fun present(params: Params): SongRowUiModel {
        val scope = rememberCoroutineScope()
        val jamSong = params.jamSong
        // Keyed by position so expansion survives list updates and follows the row.
        var expanded by remember(jamSong.position) { mutableStateOf(false) }

        return SongRowUiModel(
            position = jamSong.position,
            title = params.title,
            key = jamSong.key,
            expanded = expanded,
            // Open slots first (DESIGN.md); sortedBy is stable, and each slot keeps its lineup index.
            slots = jamSong.lineup
                .mapIndexed { index, slot -> SlotUiModel(index, slot.instrument, slot.musicianName) }
                .sortedBy { !it.isOpen },
            canEdit = params.isAdmin,
            events = EventHandler { event ->
                when (event) {
                    SongRowUiModel.Event.ToggleExpanded -> expanded = !expanded
                    is SongRowUiModel.Event.ClearSlot -> scope.launch {
                        setlistRepository.clearSlot(jamSong.position, event.slotIndex)
                    }
                }
            },
        )
    }
}
```

### Parent presenter — the screen

```kotlin
class NextJamPresenter(
    private val setlistRepository: SetlistRepository,
    private val catalogRepository: CatalogRepository,
    private val adminSession: AdminSession,
    private val songRowPresenter: SongRowPresenter,
) : Presenter<NextJamUiModel, Unit> {

    @Composable
    override fun present(params: Unit): NextJamUiModel {
        val scope = rememberCoroutineScope()
        val sources by remember {
            combine(
                setlistRepository.observeUpcomingJam(),
                catalogRepository.observeSongsById(),
                adminSession.observeIsAdmin(),
                ::Triple,
            )
        }.collectAsState(initial = null)
        var publishError by remember { mutableStateOf<String?>(null) }

        val (jam, songsById, isAdmin) = sources ?: return NextJamUiModel.Loading
        if (jam.status == JamStatus.DRAFT && !isAdmin) {
            return NextJamUiModel.Draft(jam.date.toString(), jam.venue)
        }

        return NextJamUiModel.Data(
            date = jam.date.toString(),
            venue = jam.venue,
            isAdmin = isAdmin,
            isPublished = jam.status == JamStatus.PUBLISHED,
            publishError = publishError,
            rows = jam.songs.map { jamSong ->
                songRowPresenter.present(
                    SongRowPresenter.Params(
                        jamSong = jamSong,
                        title = songsById[jamSong.songId]?.title.orEmpty(),
                        isAdmin = isAdmin,
                    ),
                )
            },
            events = EventHandler { event ->
                when (event) {
                    NextJamUiModel.Data.Event.Publish -> scope.launch {
                        // A publish that fails silently is the worst outcome in the product:
                        // surface it, never swallow it.
                        setlistRepository.publish()
                            .onFailure { publishError = "No se pudo publicar. Probá de nuevo." }
                    }
                    NextJamUiModel.Data.Event.DismissPublishError -> publishError = null
                }
            },
        )
    }
}
```

### Screen — renders only

```kotlin
@Composable
fun NextJamScreen(presenter: NextJamPresenter = koinInject()) {
    when (val model = presenter.present(Unit)) {
        NextJamUiModel.Loading -> SetlistSkeleton()
        is NextJamUiModel.Draft -> DraftMessage(model)
        is NextJamUiModel.Data -> Setlist(model)
    }
}

@Composable
private fun SongRow(model: SongRowUiModel) {
    Column(Modifier.clickable { model.events(SongRowUiModel.Event.ToggleExpanded) }) {
        // position, title, key, instrument strip…
        if (model.expanded) {
            model.slots.forEach { slot ->
                SlotChip(
                    slot = slot,
                    onClear = if (model.canEdit && !slot.isOpen) {
                        { model.events(SongRowUiModel.Event.ClearSlot(slot.index)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
```

No logic in the screen: it maps the model to composables and forwards events. Admin controls are
drawn from `canEdit` / `isAdmin`; nothing in the screen decides who is an admin.

### Koin wiring

Presenters are `factory`: they hold no state, the composition does. Each feature module exposes one
Koin module, and `:app` starts Koin with all of them — features never reference each other's modules.

```kotlin
// :feature:next-jam
val nextJamModule = module {
    factory { SongRowPresenter(get()) }
    factory { NextJamPresenter(get(), get(), get(), get()) }
}

// :core:data
val dataModule = module {
    single<SetlistRepository> { AppsScriptSetlistRepository(get(), get()) }
    single<CatalogRepository> { CachedCatalogRepository(get(), get()) }
    single<AdminSession> { DataStoreAdminSession(get()) }
}

// :app
class BluesJamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BluesJamApp)
            modules(dataModule, nextJamModule, pastJamsModule, infoModule)
        }
    }
}
```

## Tests — Molecule on the JVM

Fakes backed by `MutableStateFlow`, no emulator, no mocking library required. Each test asserts a
state transition, not just the first emission.

```kotlin
class NextJamPresenterTest {

    private val setlist = FakeSetlistRepository(upcoming = publishedJam)
    private val catalog = FakeCatalogRepository(songsById = mapOf("s1" to sweetHomeChicago))

    private fun presenter(isAdmin: Boolean) = NextJamPresenter(
        setlistRepository = setlist,
        catalogRepository = catalog,
        adminSession = FakeAdminSession(isAdmin),
        songRowPresenter = SongRowPresenter(setlist),
    )

    @Test
    fun `emits loading, then the published setlist`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter(isAdmin = false).present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            val data = awaitItem() as NextJamUiModel.Data
            assertEquals(listOf("Sweet Home Chicago"), data.rows.map { it.title })
            assertFalse(data.rows.first().canEdit)
        }
    }

    @Test
    fun `musicians see the draft message, not an empty list`() = runTest {
        setlist.upcoming.value = publishedJam.copy(status = JamStatus.DRAFT)
        moleculeFlow(RecompositionMode.Immediate) { presenter(isAdmin = false).present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            assertIs<NextJamUiModel.Draft>(awaitItem())
        }
    }

    @Test
    fun `admin clears a slot through the repository`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter(isAdmin = true).present(Unit) }.test {
            awaitItem() // Loading
            val row = (awaitItem() as NextJamUiModel.Data).rows.first()
            row.events(SongRowUiModel.Event.ClearSlot(slotIndex = 0))

            val updated = (awaitItem() as NextJamUiModel.Data).rows.first()
            assertTrue(updated.slots.single { it.index == 0 }.isOpen)
            assertEquals(listOf(1 to 0), setlist.clearSlotCalls) // (position, slotIndex)
        }
    }
}
```

Because every `EventHandler` without a key is equal to any other, a whole model can be compared with
one built from `EventHandler {}` when that is clearer than asserting field by field.

## Rules To Keep

- The presenter is the only place with presentation logic; the screen renders and forwards events.
- A `UiModel` holds display values and `EventHandler`s only — no repositories, no Android types, no
  raw lambdas. In phase 2 it is handed to the assistant as context unchanged.
- Local, instant UI state (`expanded`, text being typed) lives in `remember`, keyed by a stable id.
- Anything that changes data calls a repository function. Never write from an event handler
  directly to a data source (D-13).
- Short-lived work uses `rememberCoroutineScope()`. Work that must outlive the screen (save on
  pause) uses an app-level scope, as in the second article's `AppScope`, when a slice needs it.
