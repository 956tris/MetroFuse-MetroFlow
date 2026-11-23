package dev.metrofuse.metroflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metrofuse.api.HomeItem
import dev.metrofuse.api.NativePlugin
import dev.metrofuse.api.NativeScreen
import dev.metrofuse.api.PluginContext
import dev.metrofuse.api.SearchResult
import dev.metrofuse.api.Track
import dev.metrofuse.api.UiArtworkEnvironment
import dev.metrofuse.api.UiContext
import dev.metrofuse.api.UiPlaybackControls
import dev.metrofuse.sdk.resources.Res
import dev.metrofuse.sdk.resources.add_queue
import dev.metrofuse.sdk.resources.albums
import dev.metrofuse.sdk.resources.all
import dev.metrofuse.sdk.resources.artists
import dev.metrofuse.sdk.resources.back
import dev.metrofuse.sdk.resources.collection
import dev.metrofuse.sdk.resources.empty_home
import dev.metrofuse.sdk.resources.empty_library
import dev.metrofuse.sdk.resources.home
import dev.metrofuse.sdk.resources.library
import dev.metrofuse.sdk.resources.load_more
import dev.metrofuse.sdk.resources.music
import dev.metrofuse.sdk.resources.no_results
import dev.metrofuse.sdk.resources.playlists
import dev.metrofuse.sdk.resources.plugins
import dev.metrofuse.sdk.resources.previous
import dev.metrofuse.sdk.resources.queue
import dev.metrofuse.sdk.resources.retry
import dev.metrofuse.sdk.resources.search
import dev.metrofuse.sdk.resources.settings
import dev.metrofuse.sdk.searchUiCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

class MetroFlowPlugin : NativePlugin {
    override suspend fun start(context: PluginContext) {
        val pluginContext = context
        context.registerUi(
            "ui.app",
            object : NativeScreen {
                @Composable
                override fun Content(context: UiContext) = MetroFlow(context, context = pluginContext)
            },
        )
        context.registerUi(
            "ui.settings",
            object : NativeScreen {
                @Composable
                override fun Content(context: UiContext) = SpotifySettingsScreen(context, pluginContext)
            },
        )
        context.registerUi(
            "ui.plugins",
            object : NativeScreen {
                @Composable
                override fun Content(context: UiContext) = SpotifyPluginsScreen(context, pluginContext)
            },
        )
    }
}

@Composable
private fun MetroFlow(
    ui: UiContext,
    context: PluginContext,
) {
    val environment = checkNotNull(ui.extensions as? UiArtworkEnvironment) { "MetroFlow requires the host UI artwork environment" }
    val playback by ui.player.state.collectAsState()
    val metadata by ui.metadata.collectAsState()
    val current = if (metadata.id == playback.track.id) metadata else playback.track
    val shelves by environment.homeShelves.collectAsState(emptyList())
    var accountName by remember { mutableStateOf("") }
    var frontendId by remember { mutableStateOf("") }
    var frontendTitle by remember { mutableStateOf("") }
    var showFrontends by remember { mutableStateOf(false) }
    val pluginStates by environment.pluginStates.collectAsState(emptyList())

    LaunchedEffect(shelves, pluginStates, frontendId) {
        try {
            val source = context.callService("ui.source/v1", buildJsonObject {})
            accountName = source["accountName"]?.jsonPrimitive?.contentOrNull.orEmpty()
            frontendId = source["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            frontendTitle = source["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            accountName = ""
            frontendTitle = ""
        }
    }

    val scope = rememberCoroutineScope()
    val codec = remember { Json { ignoreUnknownKeys = true } }
    var page by remember { mutableStateOf("home") }
    var filter by remember { mutableStateOf("all") }
    var libraryFilter by remember { mutableStateOf("songs") }
    var expanded by remember { mutableStateOf(false) }
    var nowPlayingPanel by remember { mutableStateOf(true) }
    var searchRetry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResult(emptyList())) }
    var offset by remember { mutableIntStateOf(0) }
    val library by remember(offset) { environment.library(offset, 100) }.collectAsState(emptyList())
    var collection by remember { mutableStateOf<CollectionInfo?>(null) }
    var collectionOrigin by remember { mutableStateOf("home") }
    var collectionTracks by remember { mutableStateOf(emptyList<Track>()) }
    var searching by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    val loading =
        when (page) {
            "search" -> searching
            "collection" -> resolving
            else -> false
        }
    var collectionGeneration by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(page, query, searchRetry, frontendId) {
        if (page != "search") return@LaunchedEffect
        results = SearchResult(emptyList())
        failed = false
        if (query.isBlank()) {
            searching = false
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        try {
            results = context.searchUiCatalog(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            searching = false
        }
    }

    fun navigate(target: String) {
        job?.cancel()
        searching = false
        resolving = false
        collectionGeneration++
        failed = false
        page = target
    }

    fun open(info: CollectionInfo) {
        if (page != "collection") collectionOrigin = page
        val generation = ++collectionGeneration
        job?.cancel()
        collection = info
        collectionTracks = emptyList()
        page = "collection"
        resolving = true
        failed = false
        job =
            scope.launch {
                try {
                    val item =
                        context
                            .callService(
                                "ui.collection/v1",
                                buildJsonObject {
                                    put("kind", info.kind)
                                    put("id", info.id)
                                },
                            )["item"]
                            ?.jsonObject
                    if (generation != collectionGeneration) return@launch
                    collectionTracks = item?.get("tracks")?.let { codec.decodeFromJsonElement<List<Track>>(it) }.orEmpty()
                    failed = item == null
                    if (item != null) {
                        collection =
                            info.copy(
                                title = item["title"]?.jsonPrimitive?.contentOrNull ?: item["name"]?.jsonPrimitive?.contentOrNull ?: info.title,
                                subtitle = item["artist"]?.jsonPrimitive?.contentOrNull ?: item["author"]?.jsonPrimitive?.contentOrNull ?: info.subtitle,
                                artwork = item["artwork"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: info.artwork,
                            )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == collectionGeneration) failed = true
                } finally {
                    if (generation == collectionGeneration) resolving = false
                }
            }
    }

    fun openItem(item: HomeItem) {
        if (item.track != null) {
            if ("playTracks" in ui.player.capabilities) ui.player.playTracks(listOf(item.track!!))
        } else {
            item.info()?.let(::open)
        }
    }

    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    MaterialTheme(colorScheme = SpotifyDarkColorScheme) {
        Surface(Modifier.fillMaxSize(), color = SpotifyColors.OuterBlack, contentColor = SpotifyColors.TextPrimary) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val canShowNowPlayingPanel = maxWidth >= 1200.dp
                val sidebarWidth = if (maxWidth < 1100.dp) 236.dp else 280.dp

                if (showFrontends) {
                    FrontendPicker(context, { showFrontends = false }) { selection ->
                        frontendId = selection.selected
                        frontendTitle = selection.choices.firstOrNull { it.id == selection.selected }?.title.orEmpty()
                        navigate("home")
                    }
                }

                Column(Modifier.fillMaxSize().background(SpotifyColors.OuterBlack)) {
                    if (wide) {
                        DesktopTopBar(
                            query = query,
                            accountName = accountName,
                            sourceTitle = frontendTitle,
                            onHome = { navigate("home") },
                            onSearch = {
                                navigate("search")
                                query = it
                            },
                            onFocusSearch = { navigate("search") },
                            onSources = { showFrontends = true },
                            onPlugins = { ui.navigate("plugins") },
                            onSettings = { ui.navigate("settings") },
                        )
                    }

                    Row(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = if (wide) 8.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (wide) {
                            DesktopLibrarySidebar(
                                width = sidebarWidth,
                                page = page,
                                libraryTracks = library,
                                player = ui.player,
                                environment = environment,
                                onNavigate = ::navigate,
                            )
                        }

                        val headerTint =
                            remember(page, collection?.artwork, current.id) {
                                when (page) {
                                    "collection" -> artworkAccentTint(collection?.artwork.orEmpty())
                                    "home" -> Color(0xFF1F3B2C)
                                    else -> SpotifyColors.Elevated
                                }
                            }

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(if (wide) 8.dp else 0.dp))
                                .background(SpotifyColors.BaseSurface),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(headerTint.copy(alpha = 0.72f), SpotifyColors.BaseSurface),
                                        ),
                                    ),
                            )

                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                item {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(end = if (wide) 0.dp else 44.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (!wide) {
                                            Surface(
                                                onClick = { ui.navigate("settings") },
                                                shape = CircleShape,
                                                color = SpotifyColors.Green,
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (accountName.isNotBlank()) {
                                                        Text(accountName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    } else {
                                                        Glyph("profile", stringResource(Res.string.settings), Modifier.size(17.dp), Color.Black)
                                                    }
                                                }
                                            }
                                        }

                                        if (page == "home") {
                                            HomeFilters(filter, { filter = it }, Modifier.weight(1f))
                                            if (!wide) {
                                                Surface(
                                                    onClick = { showFrontends = true },
                                                    shape = CircleShape,
                                                    color = SpotifyColors.Card,
                                                ) {
                                                    Row(
                                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Glyph("source", "Source", Modifier.size(14.dp), SpotifyColors.Green)
                                                        Text(
                                                            frontendTitle.ifBlank { "Offline" },
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                stringResource(
                                                    when (page) {
                                                        "search" -> Res.string.search
                                                        "library" -> Res.string.library
                                                        "queue" -> Res.string.queue
                                                        "collection" -> Res.string.collection
                                                        else -> Res.string.home
                                                    },
                                                ),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!wide && page == "library") {
                                                IconButton(onClick = { ui.navigate("plugins") }, modifier = Modifier.size(34.dp)) {
                                                    Glyph("settings", stringResource(Res.string.plugins), Modifier.size(19.dp), SpotifyColors.TextMuted)
                                                }
                                            }
                                        }
                                    }
                                }

                                when (page) {
                                    "home" -> {
                                        val filtered =
                                            shelves
                                                .map { shelf ->
                                                    shelf.copy(
                                                        items = shelf.items.filter { item -> filter == "all" || item.info()?.kind == filter },
                                                    )
                                                }.filter { it.items.isNotEmpty() }

                                        val quick = quickHomeItems(filtered.flatMap { it.items })
                                        if (quick.isNotEmpty()) {
                                            item {
                                                val cols = if (wide) 4 else 2
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    quick.chunked(cols).forEach { row ->
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            row.forEach { entry ->
                                                                val info = entry.info()!!
                                                                val isPlayingThis = info.kind == "track" && info.id == current.id
                                                                Row(
                                                                    Modifier
                                                                        .weight(1f)
                                                                        .height(56.dp)
                                                                        .clip(RoundedCornerShape(6.dp))
                                                                        .background(if (isPlayingThis) SpotifyColors.Highlight else SpotifyColors.Card)
                                                                        .clickable { openItem(entry) },
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                ) {
                                                                    environment.Artwork(info.artwork, info.title, Modifier.size(56.dp))
                                                                    Text(
                                                                        info.title,
                                                                        Modifier.weight(1f).padding(horizontal = 10.dp),
                                                                        maxLines = 2,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isPlayingThis) SpotifyColors.Green else Color.White,
                                                                    )
                                                                }
                                                            }
                                                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (filtered.isEmpty()) {
                                            item {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(stringResource(Res.string.empty_home), color = SpotifyColors.TextMuted)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        TextButton(onClick = { showFrontends = true }) {
                                                            Text("Choose Source", color = SpotifyColors.Green, fontWeight = FontWeight.Bold)
                                                        }
                                                        TextButton(onClick = { ui.navigate("plugins") }) {
                                                            Text(stringResource(Res.string.plugins), color = Color.White)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        filtered.forEach { shelf ->
                                            item {
                                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Text(
                                                        shelf.title,
                                                        fontSize = 21.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = (-0.3).sp,
                                                    )
                                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                        items(shelf.items) { entry ->
                                                            entry.info()?.let { info ->
                                                                val cardSize = if (wide) 172.dp else 150.dp
                                                                Column(
                                                                    Modifier
                                                                        .width(cardSize)
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .clickable { openItem(entry) },
                                                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                                                ) {
                                                                    environment.Artwork(
                                                                        info.artwork,
                                                                        info.title,
                                                                        Modifier
                                                                            .size(cardSize)
                                                                            .clip(if (info.kind == "artist") CircleShape else RoundedCornerShape(6.dp))
                                                                            .background(SpotifyColors.Card),
                                                                    )
                                                                    Text(
                                                                        info.title,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        fontSize = 14.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                    )
                                                                    if (info.subtitle.isNotBlank()) {
                                                                        Text(
                                                                            info.subtitle,
                                                                            color = SpotifyColors.TextMuted,
                                                                            fontSize = 12.sp,
                                                                            maxLines = 2,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        if (page == "search" && !wide) {
                                            item {
                                                SearchPillInput(query, { query = it }, Modifier.fillMaxWidth())
                                            }
                                        }

                                        if (page == "search" && query.isBlank()) {
                                            item {
                                                Text("Browse all", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                            val cols = if (wide) 4 else 2
                                            items(spotifyBrowseCategories.chunked(cols)) { row ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    row.forEach { cat ->
                                                        Box(
                                                            Modifier
                                                                .weight(1f)
                                                                .height(94.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(cat.color)
                                                                .clickable { query = cat.query }
                                                                .padding(14.dp),
                                                        ) {
                                                            Text(
                                                                cat.title,
                                                                color = Color.White,
                                                                fontSize = 17.sp,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                        }
                                                    }
                                                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                                }
                                            }
                                        }

                                        if (page == "library") {
                                            item {
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    items(listOf("songs" to "Songs", "albums" to "Albums", "artists" to "Artists")) { (key, label) ->
                                                        FilterChip(
                                                            selected = libraryFilter == key,
                                                            onClick = { libraryFilter = key },
                                                            shape = CircleShape,
                                                            border = null,
                                                            colors =
                                                                FilterChipDefaults.filterChipColors(
                                                                    containerColor = SpotifyColors.Card,
                                                                    labelColor = Color.White,
                                                                    selectedContainerColor = SpotifyColors.Green,
                                                                    selectedLabelColor = Color.Black,
                                                                ),
                                                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (page == "collection") {
                                            item {
                                                collection?.let { info ->
                                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                        IconButton(onClick = { navigate(collectionOrigin) }) {
                                                            Glyph("back", stringResource(Res.string.back))
                                                        }
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.Bottom,
                                                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                                                        ) {
                                                            environment.Artwork(
                                                                info.artwork,
                                                                info.title,
                                                                Modifier
                                                                    .size(if (wide) 196.dp else 156.dp)
                                                                    .clip(if (info.kind == "artist") CircleShape else RoundedCornerShape(6.dp))
                                                                    .background(SpotifyColors.Card),
                                                            )
                                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Text(
                                                                    info.kind.uppercase(),
                                                                    color = SpotifyColors.TextMuted,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    letterSpacing = 1.sp,
                                                                )
                                                                Text(
                                                                    info.title,
                                                                    fontSize = if (wide) 34.sp else 24.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                                if (info.subtitle.isNotBlank()) {
                                                                    Text(info.subtitle, color = SpotifyColors.TextMuted, fontSize = 14.sp)
                                                                }
                                                            }
                                                        }
                                                        if (collectionTracks.isNotEmpty()) {
                                                            Row(
                                                                Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                            ) {
                                                                Text(
                                                                    "${collectionTracks.size} songs · ${formatTime(collectionTracks.sumOf { it.duration })}",
                                                                    color = SpotifyColors.TextMuted,
                                                                    fontSize = 13.sp,
                                                                )
                                                                PlayButton(
                                                                    playing = false,
                                                                    enabled = "playTracks" in ui.player.capabilities,
                                                                    click = { ui.player.playTracks(collectionTracks) },
                                                                    size = 52,
                                                                    color = SpotifyColors.Green,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpotifyColors.Green) }
                                        if (failed) {
                                            item {
                                                TextButton(
                                                    onClick = {
                                                        if (page == "collection") collection?.let(::open) else searchRetry++
                                                    },
                                                ) { Text(stringResource(Res.string.retry), color = SpotifyColors.Green) }
                                            }
                                        }

                                        if (page == "search" && query.isNotBlank()) {
                                            items(results.albums.take(6)) { album ->
                                                CatalogRow(environment, CollectionInfo("album", album.id, album.title, album.artist, album.artwork), ::open)
                                            }
                                            items(results.artists.take(6)) { artist ->
                                                CatalogRow(environment, CollectionInfo("artist", artist.id, artist.name, "Artist", artist.artwork), ::open)
                                            }
                                            items(results.playlists.take(6)) { playlist ->
                                                CatalogRow(environment, CollectionInfo("playlist", playlist.id, playlist.title, playlist.author, playlist.artwork), ::open)
                                            }
                                        }

                                        val tracks =
                                            when (page) {
                                                "queue" -> playback.queue
                                                "search" -> if (query.isBlank()) emptyList() else results.tracks
                                                "collection" -> collectionTracks
                                                "library" ->
                                                    when (libraryFilter) {
                                                        "albums" -> library.filter { it.album.isNotBlank() }.distinctBy { it.album }
                                                        "artists" -> library.distinctBy { it.artist }
                                                        else -> library
                                                    }
                                                else -> library
                                            }

                                        if (tracks.isEmpty() && !loading && !failed && !(page == "search" && query.isBlank())) {
                                            item {
                                                Text(
                                                    stringResource(
                                                        if (page == "search") Res.string.no_results else Res.string.empty_library,
                                                    ),
                                                    color = SpotifyColors.TextMuted,
                                                )
                                            }
                                        }

                                        items(tracks.indices.toList()) { index ->
                                            val track = tracks[index]
                                            val isCurrent = track.id == playback.track.id
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isCurrent) SpotifyColors.Elevated else Color.Transparent)
                                                    .clickable(
                                                        enabled = if (page == "queue") "queueIndex" in ui.player.capabilities else "playTracks" in ui.player.capabilities,
                                                    ) {
                                                        if (page == "queue") {
                                                            (ui.player as? UiPlaybackControls)?.selectQueueIndex(index)
                                                        } else {
                                                            ui.player.playTracks(tracks, index)
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                if (page == "collection") {
                                                    Text(
                                                        "${index + 1}",
                                                        Modifier.width(24.dp),
                                                        color = if (isCurrent) SpotifyColors.Green else SpotifyColors.TextMuted,
                                                        fontSize = 13.sp,
                                                    )
                                                }
                                                environment.Artwork(
                                                    track.artwork,
                                                    track.title,
                                                    Modifier
                                                        .size(48.dp)
                                                        .clip(if (page == "library" && libraryFilter == "artists") CircleShape else RoundedCornerShape(4.dp))
                                                        .background(SpotifyColors.Card),
                                                )
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        if (page == "library" && libraryFilter == "artists") track.artist.ifBlank { track.title }
                                                        else if (page == "library" && libraryFilter == "albums") track.album.ifBlank { track.title }
                                                        else track.title,
                                                        color = if (isCurrent) SpotifyColors.Green else Color.White,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                                                        color = SpotifyColors.TextMuted,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (track.duration > 0) {
                                                    Text(formatTime(track.duration), color = SpotifyColors.TextMuted, fontSize = 12.sp)
                                                }
                                                if ("addToQueue" in ui.player.capabilities) {
                                                    IconButton(onClick = { ui.player.addToQueue(listOf(track)) }) {
                                                        Glyph("add", stringResource(Res.string.add_queue), Modifier.size(18.dp), SpotifyColors.TextMuted)
                                                    }
                                                }
                                            }
                                        }

                                        if (page == "library") {
                                            item {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (offset > 0) {
                                                        TextButton(onClick = { offset = (offset - 100).coerceAtLeast(0) }) {
                                                            Text(stringResource(Res.string.previous), color = SpotifyColors.Green)
                                                        }
                                                    }
                                                    if (library.size == 100) {
                                                        TextButton(onClick = { offset += 100 }) {
                                                            Text(stringResource(Res.string.load_more), color = SpotifyColors.Green)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (wide && canShowNowPlayingPanel && nowPlayingPanel && current.id.isNotBlank()) {
                            NowPlayingSidebar(
                                current = current,
                                playback = playback,
                                environment = environment,
                                context = context,
                                close = { nowPlayingPanel = false },
                                expand = { expanded = true },
                                openQueue = { navigate("queue") },
                            )
                        }
                    }

                    if (current.id.isNotBlank() && wide) {
                        DesktopPlayerBar(
                            player = ui.player,
                            playback = playback,
                            current = current,
                            environment = environment,
                            nowPlayingVisible = canShowNowPlayingPanel && nowPlayingPanel,
                            toggleNowPlaying = {
                                if (canShowNowPlayingPanel) nowPlayingPanel = !nowPlayingPanel else expanded = true
                            },
                            expand = { expanded = true },
                            openQueue = { navigate("queue") },
                        )
                    } else if (current.id.isNotBlank()) {
                        CompactMiniPlayer(ui.player, playback, current, environment) { expanded = true }
                    }

                    if (!wide) {
                        CompactBottomNavigation(page, ::navigate)
                    }
                }

                if (expanded) {
                    ExpandedSpotifyPlayer(
                        player = ui.player,
                        playback = playback,
                        current = current,
                        environment = environment,
                        context = context,
                        wide = wide,
                        close = { expanded = false },
                        openQueue = {
                            expanded = false
                            navigate("queue")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFilters(
    selected: String,
    select: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(listOf("all", "track", "playlist", "album", "artist")) { kind ->
            FilterChip(
                selected = selected == kind,
                onClick = { select(kind) },
                shape = CircleShape,
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = SpotifyColors.Card,
                        labelColor = Color.White,
                        selectedContainerColor = SpotifyColors.Green,
                        selectedLabelColor = Color.Black,
                    ),
                label = {
                    Text(
                        stringResource(
                            when (kind) {
                                "track" -> Res.string.music
                                "playlist" -> Res.string.playlists
                                "album" -> Res.string.albums
                                "artist" -> Res.string.artists
                                else -> Res.string.all
                            },
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
    }
}

@Composable
private fun CatalogRow(
    environment: UiArtworkEnvironment,
    info: CollectionInfo,
    open: (CollectionInfo) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { open(info) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        environment.Artwork(
            info.artwork,
            info.title,
            Modifier
                .size(54.dp)
                .clip(if (info.kind == "artist") CircleShape else RoundedCornerShape(4.dp))
                .background(SpotifyColors.Card),
        )
        Column(Modifier.weight(1f)) {
            Text(info.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (info.subtitle.isNotBlank()) Text(info.subtitle, color = SpotifyColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CompactBottomNavigation(
    page: String,
    select: (String) -> Unit,
) {
    val entries = listOf("home", "search", "library")
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE6121212), SpotifyColors.OuterBlack),
                ),
            ),
    ) {
        entries.forEach { target ->
            val label =
                stringResource(
                    when (target) {
                        "search" -> Res.string.search
                        "library" -> Res.string.library
                        else -> Res.string.home
                    },
                )
            val isSelected = page == target
            Column(
                Modifier
                    .weight(1f)
                    .semantics { selected = isSelected }
                    .clickable(role = Role.Tab) { select(target) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Glyph(
                    if (target == "home" && !isSelected) "home_outline" else target,
                    label,
                    Modifier.size(24.dp),
                    tint = if (isSelected) Color.White else SpotifyColors.TextMuted,
                )
                Text(
                    label,
                    color = if (isSelected) Color.White else SpotifyColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
