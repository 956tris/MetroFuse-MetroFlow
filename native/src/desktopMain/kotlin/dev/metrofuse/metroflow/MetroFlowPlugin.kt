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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.metrofuse.api.HomeItem
import dev.metrofuse.api.NativePlugin
import dev.metrofuse.api.NativeScreen
import dev.metrofuse.api.PlaybackState
import dev.metrofuse.api.Player
import dev.metrofuse.api.PluginContext
import dev.metrofuse.api.SearchResult
import dev.metrofuse.api.Track
import dev.metrofuse.api.UiArtworkEnvironment
import dev.metrofuse.api.UiContext
import dev.metrofuse.api.UiPlaybackControls
import dev.metrofuse.sdk.PluginSeekBar
import dev.metrofuse.sdk.resources.Res
import dev.metrofuse.sdk.resources.add_queue
import dev.metrofuse.sdk.resources.albums
import dev.metrofuse.sdk.resources.all
import dev.metrofuse.sdk.resources.artists
import dev.metrofuse.sdk.resources.back
import dev.metrofuse.sdk.resources.brand
import dev.metrofuse.sdk.resources.close_player
import dev.metrofuse.sdk.resources.collection
import dev.metrofuse.sdk.resources.empty_home
import dev.metrofuse.sdk.resources.empty_library
import dev.metrofuse.sdk.resources.empty_search
import dev.metrofuse.sdk.resources.home
import dev.metrofuse.sdk.resources.library
import dev.metrofuse.sdk.resources.load_more
import dev.metrofuse.sdk.resources.music
import dev.metrofuse.sdk.resources.next
import dev.metrofuse.sdk.resources.no_results
import dev.metrofuse.sdk.resources.now_playing
import dev.metrofuse.sdk.resources.offline
import dev.metrofuse.sdk.resources.pause
import dev.metrofuse.sdk.resources.play
import dev.metrofuse.sdk.resources.playlists
import dev.metrofuse.sdk.resources.plugins
import dev.metrofuse.sdk.resources.previous
import dev.metrofuse.sdk.resources.queue
import dev.metrofuse.sdk.resources.repeat
import dev.metrofuse.sdk.resources.retry
import dev.metrofuse.sdk.resources.search
import dev.metrofuse.sdk.resources.settings
import dev.metrofuse.sdk.resources.shuffle
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
    }
}

private val canvas = Color(0xFF0E1420)
private val ink = Color(0xFF182232)
private val panel = Color(0xFF273750)
private val accent = Color(0xFFFFCB77)
private val muted = Color(0xFFB5C4D9)

private data class CollectionInfo(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: String,
)

private fun HomeItem.info(): CollectionInfo? =
    when {
        track != null -> track!!.let { CollectionInfo("track", it.id, it.title, it.artist, it.artwork) }
        album != null -> album!!.let { CollectionInfo("album", it.id, it.title, it.artist, it.artwork) }
        artist != null -> artist!!.let { CollectionInfo("artist", it.id, it.name, "", it.artwork) }
        playlist != null -> playlist!!.let { CollectionInfo("playlist", it.id, it.title, it.author, it.artwork) }
        else -> null
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
    LaunchedEffect(shelves) {
        try {
            val source = context.callService("ui.source/v1", buildJsonObject {})
            accountName = source["accountName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            accountName = ""
        }
    }
    val scope = rememberCoroutineScope()
    val codec = remember { Json { ignoreUnknownKeys = true } }
    var page by remember { mutableStateOf("home") }
    var filter by remember { mutableStateOf("all") }
    var expanded by remember { mutableStateOf(false) }
    var nowPlayingPanel by remember { mutableStateOf(true) }
    var searchRetry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResult(emptyList())) }
    var offset by remember { mutableIntStateOf(0) }
    val library by remember(offset) { environment.library(offset, 100) }.collectAsState(emptyList())
    var collection by remember { mutableStateOf<CollectionInfo?>(null) }
    var collectionTracks by remember { mutableStateOf(emptyList<Track>()) }
    var searching by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    val loading =
        if (page == "search") {
            searching
        } else if (page == "collection") {
            resolving
        } else {
            false
        }
    var collectionGeneration by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(page, query, searchRetry) {
        if (page != "search") return@LaunchedEffect
        results = SearchResult(emptyList())
        failed = false
        if (query.isBlank()) {
            searching = false
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        try {
            results = context.searchUiCatalog(query)
        } catch (
            cancelled: CancellationException,
        ) {
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
                                title =
                                    item["title"]?.jsonPrimitive?.contentOrNull ?: item["name"]?.jsonPrimitive?.contentOrNull ?: info.title,
                                subtitle =
                                    item["artist"]?.jsonPrimitive?.contentOrNull ?: item["author"]?.jsonPrimitive?.contentOrNull
                                        ?: info.subtitle,
                                artwork = item["artwork"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: info.artwork,
                            )
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
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
    MaterialTheme(
        colorScheme = darkColorScheme(primary = accent, onPrimary = Color.Black, background = ink, surface = ink, onSurface = Color.White),
    ) {
        Surface(Modifier.fillMaxSize(), color = ink, contentColor = Color.White) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val canShowNowPlayingPanel = maxWidth >= 1240.dp
                Column(Modifier.fillMaxSize().background(canvas)) {
                    if (wide) {
                        DesktopHeader(query, accountName, { navigate("home") }, {
                            navigate("search")
                            query = it
                        }, { ui.navigate("settings") }, { ui.navigate("plugins") })
                    }
                    Row(Modifier.weight(1f).padding(if (wide) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (wide) {
                            Column(
                                Modifier
                                    .width(248.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ink)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        navigate("library")
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Glyph("library", stringResource(Res.string.library), tint = muted)
                                    Text(
                                        stringResource(Res.string.library),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { navigate("search") },
                                    ) { Glyph("search", stringResource(Res.string.search), Modifier.size(20.dp), muted) }
                                }
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(library.take(30)) { track ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable(enabled = "playTracks" in ui.player.capabilities) {
                                                ui.player.playTracks(listOf(track))
                                            },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement =
                                                Arrangement.spacedBy(
                                                    12.dp,
                                                ),
                                        ) {
                                            environment.Artwork(
                                                track.artwork,
                                                track.title,
                                                Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                                            )
                                            TrackMetadata(track, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        LazyColumn(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(if (wide) 8.dp else 0.dp))
                                .background(ink),
                            contentPadding = PaddingValues(horizontal = if (wide) 28.dp else 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(end = if (wide) 0.dp else 40.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (!wide) {
                                        Surface(onClick = { ui.navigate("settings") }, shape = CircleShape, color = accent) {
                                            if (accountName.isNotBlank()) {
                                                Text(
                                                    accountName.take(1),
                                                    Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            } else {
                                                Glyph(
                                                    "profile",
                                                    stringResource(Res.string.settings),
                                                    Modifier.padding(9.dp).size(24.dp),
                                                    Color.Black,
                                                )
                                            }
                                        }
                                    }
                                    if (page == "home") {
                                        HomeFilters(filter, { filter = it }, Modifier.weight(1f))
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
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            when (page) {
                                "home" -> {
                                    val filtered =
                                        shelves
                                            .map {
                                                it.copy(
                                                    items =
                                                        it.items.filter { item ->
                                                            filter == "all" ||
                                                                item.info()?.kind == filter
                                                        },
                                                )
                                            }.filter { it.items.isNotEmpty() }
                                    val quick = filtered.flatMap { it.items }.distinctBy { it.info()?.id }.take(8)
                                    if (quick.isNotEmpty()) {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                quick.chunked(if (wide) 4 else 2).forEach { row ->
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        row.forEach { entry ->
                                                            val info = entry.info()!!
                                                            Row(
                                                                Modifier
                                                                    .weight(
                                                                        1f,
                                                                    ).height(56.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(panel)
                                                                    .clickable {
                                                                        openItem(entry)
                                                                    },
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                environment.Artwork(info.artwork, info.title, Modifier.size(56.dp))
                                                                Text(
                                                                    info.title,
                                                                    Modifier.padding(8.dp),
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                )
                                                            }
                                                        }
                                                        repeat((if (wide) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (shelves.isEmpty()) {
                                        item {
                                            Text(stringResource(Res.string.empty_home), color = muted)
                                            TextButton(onClick = { ui.navigate("plugins") }) { Text(stringResource(Res.string.plugins)) }
                                        }
                                    }
                                    filtered.forEach { shelf ->
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(
                                                    shelf.title,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    items(shelf.items) { entry ->
                                                        entry.info()?.let { info ->
                                                            Column(
                                                                Modifier
                                                                    .width(if (wide) 168.dp else 148.dp)
                                                                    .clickable { openItem(entry) },
                                                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                                            ) {
                                                                environment.Artwork(
                                                                    info.artwork,
                                                                    info.title,
                                                                    Modifier
                                                                        .size(if (wide) 168.dp else 148.dp)
                                                                        .clip(
                                                                            if (info.kind ==
                                                                                "artist"
                                                                            ) {
                                                                                CircleShape
                                                                            } else {
                                                                                RoundedCornerShape(4.dp)
                                                                            },
                                                                        ).background(panel),
                                                                )
                                                                Text(
                                                                    info.title,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                )
                                                                if (info.subtitle.isNotBlank()) {
                                                                    Text(
                                                                        info.subtitle,
                                                                        color = muted,
                                                                        style = MaterialTheme.typography.bodySmall,
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
                                            SearchInput(
                                                query,
                                                { query = it },
                                                Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                    if (page == "collection") {
                                        item {
                                            collection?.let { info ->
                                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    IconButton(
                                                        onClick = { navigate("home") },
                                                    ) { Glyph("back", stringResource(Res.string.back)) }
                                                    environment.Artwork(
                                                        info.artwork,
                                                        info.title,
                                                        Modifier
                                                            .size(
                                                                if (wide) 240.dp else 220.dp,
                                                            ).align(Alignment.CenterHorizontally)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(panel),
                                                    )
                                                    Text(
                                                        info.title,
                                                        style = MaterialTheme.typography.headlineLarge,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    if (info.subtitle.isNotBlank()) Text(info.subtitle, color = muted)
                                                    if (collectionTracks.isNotEmpty()) {
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Text(
                                                                formatTime(collectionTracks.sumOf { it.duration }),
                                                                color = muted,
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                            PlayButton(
                                                                false,
                                                                "playTracks" in ui.player.capabilities,
                                                                { ui.player.playTracks(collectionTracks) },
                                                                56,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                    if (failed) {
                                        item {
                                            TextButton(onClick = {
                                                if (page ==
                                                    "collection"
                                                ) {
                                                    collection?.let(::open)
                                                } else {
                                                    searchRetry++
                                                }
                                            }) { Text(stringResource(Res.string.retry)) }
                                        }
                                    }
                                    if (page == "search") {
                                        items(
                                            results.albums,
                                        ) { album ->
                                            CatalogRow(
                                                environment,
                                                CollectionInfo("album", album.id, album.title, album.artist, album.artwork),
                                                ::open,
                                            )
                                        }
                                        items(
                                            results.artists,
                                        ) { artist ->
                                            CatalogRow(
                                                environment,
                                                CollectionInfo("artist", artist.id, artist.name, "", artist.artwork),
                                                ::open,
                                            )
                                        }
                                        items(
                                            results.playlists,
                                        ) { playlist ->
                                            CatalogRow(
                                                environment,
                                                CollectionInfo("playlist", playlist.id, playlist.title, playlist.author, playlist.artwork),
                                                ::open,
                                            )
                                        }
                                    }
                                    val tracks =
                                        when (page) {
                                            "queue" -> playback.queue
                                            "search" -> results.tracks
                                            "collection" -> collectionTracks
                                            else -> library
                                        }
                                    if (tracks.isEmpty() && !loading &&
                                        !failed
                                    ) {
                                        item {
                                            Text(
                                                stringResource(
                                                    if (page ==
                                                        "search"
                                                    ) {
                                                        if (query.isBlank()) Res.string.empty_search else Res.string.no_results
                                                    } else {
                                                        Res.string.empty_library
                                                    },
                                                ),
                                                color = muted,
                                            )
                                        }
                                    }
                                    items(tracks.indices.toList()) { index ->
                                        val track = tracks[index]
                                        Row(
                                            Modifier.fillMaxWidth().clickable(
                                                enabled =
                                                    if (page ==
                                                        "queue"
                                                    ) {
                                                        "queueIndex" in ui.player.capabilities
                                                    } else {
                                                        "playTracks" in ui.player.capabilities
                                                    },
                                            ) {
                                                if (page ==
                                                    "queue"
                                                ) {
                                                    (ui.player as? UiPlaybackControls)?.selectQueueIndex(index)
                                                } else {
                                                    ui.player.playTracks(tracks, index)
                                                }
                                            },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            environment.Artwork(
                                                track.artwork,
                                                track.title,
                                                Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(panel),
                                            )
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                    track.title,
                                                    color =
                                                        if (track.id ==
                                                            playback.track.id
                                                        ) {
                                                            accent
                                                        } else {
                                                            Color.White
                                                        },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    listOf(track.artist, track.album)
                                                        .filter {
                                                            it.isNotBlank()
                                                        }.joinToString(
                                                            " · ",
                                                        ),
                                                    color = muted,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (track.duration >
                                                0
                                            ) {
                                                Text(formatTime(track.duration), color = muted, style = MaterialTheme.typography.labelSmall)
                                            }
                                            if ("addToQueue" in
                                                ui.player.capabilities
                                            ) {
                                                IconButton(
                                                    onClick = { ui.player.addToQueue(listOf(track)) },
                                                ) { Glyph("add", stringResource(Res.string.add_queue)) }
                                            }
                                        }
                                    }
                                    if (page == "library") {
                                        item {
                                            Row {
                                                if (offset >
                                                    0
                                                ) {
                                                    TextButton(
                                                        onClick = { offset = (offset - 100).coerceAtLeast(0) },
                                                    ) { Text(stringResource(Res.string.previous)) }
                                                }
                                                if (library.size ==
                                                    100
                                                ) {
                                                    TextButton(onClick = { offset += 100 }) { Text(stringResource(Res.string.load_more)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (wide && canShowNowPlayingPanel && nowPlayingPanel && current.id.isNotBlank()) {
                            NowPlayingSidebar(
                                current,
                                playback,
                                environment,
                                { nowPlayingPanel = false },
                                { expanded = true },
                                { navigate("queue") },
                            )
                        }
                    }
                    if (current.id.isNotBlank() && wide) {
                        DesktopPlayer(ui.player, playback, current, environment, {
                            if (canShowNowPlayingPanel) {
                                nowPlayingPanel = !nowPlayingPanel
                            } else {
                                expanded = true
                            }
                        }, { navigate("queue") })
                    } else if (current.id.isNotBlank()) {
                        Surface(
                            modifier =
                                Modifier
                                    .padding(
                                        horizontal = 8.dp,
                                    ).fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF304866),
                            onClick = {
                                expanded =
                                    true
                            },
                        ) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                environment.Artwork(current.artwork, current.title, Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                                TrackMetadata(current, Modifier.weight(1f))
                                IconButton(onClick = ui.player::toggle, enabled = "toggle" in ui.player.capabilities) {
                                    Glyph(
                                        if (playback.playing) "pause" else "play",
                                        stringResource(if (playback.playing) Res.string.pause else Res.string.play),
                                    )
                                }
                                if (wide) {
                                    IconButton(onClick = ui.player::next, enabled = "next" in ui.player.capabilities) {
                                        Glyph("next", stringResource(Res.string.next))
                                    }
                                }
                            }
                        }
                    }
                    if (!wide) Navigation(page, ::navigate, false)
                }
                if (expanded) {
                    Surface(Modifier.fillMaxSize(), color = Color(0xFF202F47)) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = if (wide) 120.dp else 24.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { expanded = false }) { Glyph("down", stringResource(Res.string.close_player)) }
                                Text(
                                    stringResource(Res.string.now_playing),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                                IconButton(onClick = {
                                    expanded = false
                                    navigate("queue")
                                }) { Glyph("queue", stringResource(Res.string.queue)) }
                            }
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val artSize = minOf(maxWidth, maxHeight, 440.dp)
                                environment.Artwork(
                                    current.artwork,
                                    current.title,
                                    Modifier.size(artSize).clip(RoundedCornerShape(8.dp)).background(panel),
                                )
                            }
                            TrackMetadata(current, Modifier.fillMaxWidth(), true)
                            if (current.album.isNotBlank()) {
                                Text(
                                    current.album,
                                    Modifier.fillMaxWidth(),
                                    color = muted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            PluginSeekBar(ui.player)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(playback.position), color = muted, style = MaterialTheme.typography.labelSmall)
                                Text(formatTime(current.duration), color = muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                val controls = ui.player as? UiPlaybackControls
                                IconButton(
                                    onClick = { controls?.toggleShuffle() },
                                    enabled =
                                        controls != null && "shuffle" in ui.player.capabilities,
                                ) { Glyph("shuffle", stringResource(Res.string.shuffle)) }
                                IconButton(onClick = ui.player::previous, enabled = "previous" in ui.player.capabilities) {
                                    Glyph("previous", stringResource(Res.string.previous), Modifier.size(32.dp))
                                }
                                PlayButton(playback.playing, "toggle" in ui.player.capabilities, ui.player::toggle, 64, Color.White)
                                IconButton(onClick = ui.player::next, enabled = "next" in ui.player.capabilities) {
                                    Glyph("next", stringResource(Res.string.next), Modifier.size(32.dp))
                                }
                                IconButton(onClick = {
                                    controls?.cycleRepeat()
                                }, enabled = controls != null && "repeat" in ui.player.capabilities) {
                                    Glyph(
                                        "repeat",
                                        stringResource(Res.string.repeat),
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingSidebar(
    current: Track,
    playback: PlaybackState,
    environment: UiArtworkEnvironment,
    close: () -> Unit,
    expand: () -> Unit,
    queue: () -> Unit,
) {
    LazyColumn(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(ink),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.now_playing),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = close) { Glyph("close", stringResource(Res.string.close_player), Modifier.size(20.dp), muted) }
            }
        }
        item {
            environment.Artwork(
                current.artwork,
                current.title,
                Modifier.size(248.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = expand),
            )
        }
        item { TrackMetadata(current, Modifier.fillMaxWidth(), true) }
        if (current.album.isNotBlank()) item { Text(current.album, color = muted, style = MaterialTheme.typography.bodyMedium) }
        val index = playback.currentIndex ?: playback.queue.indexOfFirst { it.id == playback.track.id }
        val next = playback.queue.getOrNull(index + 1).takeIf { index >= 0 }
        if (next != null) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(panel)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.queue), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = queue) { Glyph("queue", stringResource(Res.string.queue), Modifier.size(20.dp), muted) }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = queue),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        environment.Artwork(next.artwork, next.title, Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
                        TrackMetadata(next, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopHeader(
    query: String,
    accountName: String,
    home: () -> Unit,
    search: (String) -> Unit,
    settings: () -> Unit,
    plugins: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = home) { Glyph("brand", stringResource(Res.string.brand), Modifier.size(28.dp), accent) }
        Text(stringResource(Res.string.brand), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Surface(onClick = home, shape = RoundedCornerShape(12.dp), color = panel, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Glyph("home", stringResource(Res.string.home), Modifier.size(24.dp)) }
        }
        SearchInput(query, search, Modifier.width(340.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = plugins) { Glyph("settings", stringResource(Res.string.plugins), Modifier.size(20.dp), muted) }
        Surface(onClick = settings, shape = CircleShape, color = accent, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (accountName.isNotBlank()) {
                    Text(accountName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Glyph("profile", stringResource(Res.string.settings), Modifier.size(18.dp), Color.Black)
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    search: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(panel)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph("search", stringResource(Res.string.search), tint = muted)
        BasicTextField(
            query,
            search,
            Modifier.weight(
                1f,
            ),
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                ),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { input ->
                Box {
                    if (query.isEmpty()) Text(stringResource(Res.string.search), color = muted)
                    input()
                }
            },
        )
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
                shape = RoundedCornerShape(10.dp),
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = panel,
                        labelColor = Color.White,
                        selectedContainerColor = accent,
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
                    )
                },
            )
        }
    }
}

@Composable
private fun DesktopPlayer(
    player: Player,
    playback: PlaybackState,
    current: Track,
    environment: UiArtworkEnvironment,
    expand: () -> Unit,
    queue: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = expand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            environment.Artwork(current.artwork, current.title, Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
            TrackMetadata(current, Modifier.weight(1f))
        }
        Column(Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val controls = player as? UiPlaybackControls
                IconButton(onClick = {
                    controls?.toggleShuffle()
                }, enabled = "shuffle" in player.capabilities) {
                    Glyph(
                        "shuffle",
                        stringResource(Res.string.shuffle),
                        Modifier.size(18.dp),
                        muted,
                    )
                }
                IconButton(onClick = player::previous, enabled = "previous" in player.capabilities) {
                    Glyph("previous", stringResource(Res.string.previous), Modifier.size(20.dp), muted)
                }
                PlayButton(playback.playing, "toggle" in player.capabilities, player::toggle, 32, Color.White)
                IconButton(onClick = player::next, enabled = "next" in player.capabilities) {
                    Glyph("next", stringResource(Res.string.next), Modifier.size(20.dp), muted)
                }
                IconButton(onClick = {
                    controls?.cycleRepeat()
                }, enabled = "repeat" in player.capabilities) {
                    Glyph(
                        "repeat",
                        stringResource(Res.string.repeat),
                        Modifier.size(18.dp),
                        muted,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(playback.position), color = muted, style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f)) { PluginSeekBar(player) }
                Text(formatTime(current.duration), color = muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = queue) { Glyph("queue", stringResource(Res.string.queue), Modifier.size(20.dp), muted) }
            IconButton(onClick = expand) { Glyph("expand", stringResource(Res.string.now_playing), Modifier.size(20.dp), muted) }
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
        Modifier.fillMaxWidth().clickable {
            open(info)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        environment.Artwork(
            info.artwork,
            info.title,
            Modifier
                .size(56.dp)
                .clip(
                    if (info.kind ==
                        "artist"
                    ) {
                        CircleShape
                    } else {
                        RoundedCornerShape(4.dp)
                    },
                ).background(panel),
        )
        Column {
            Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (info.subtitle.isNotBlank()) Text(info.subtitle, color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrackMetadata(
    track: Track,
    modifier: Modifier,
    large: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            track.title,
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            color = muted,
            style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayButton(
    playing: Boolean,
    enabled: Boolean,
    click: () -> Unit,
    size: Int,
    color: Color = accent,
) {
    Surface(onClick = click, enabled = enabled, shape = RoundedCornerShape(14.dp), color = color, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Glyph(
                if (playing) "pause" else "play",
                stringResource(if (playing) Res.string.pause else Res.string.play),
                Modifier.size((size / 2).dp),
                Color.Black,
            )
        }
    }
}

@Composable
private fun Navigation(
    page: String,
    select: (String) -> Unit,
    wide: Boolean,
) {
    val entries = listOf("home", "search", "library")

    @Composable fun entry(
        target: String,
        modifier: Modifier,
    ) {
        val label =
            stringResource(
                when (target) {
                    "search" -> Res.string.search
                    "library" -> Res.string.library
                    else -> Res.string.home
                },
            )
        Column(
            modifier
                .clickable {
                    select(target)
                }.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Glyph(target, label, tint = if (page == target) Color.White else muted)
            Text(label, color = if (page == target) Color.White else muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
    if (wide) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { target ->
                val label =
                    stringResource(
                        when (target) {
                            "search" -> Res.string.search
                            "library" -> Res.string.library
                            else -> Res.string.home
                        },
                    )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (page ==
                                target
                            ) {
                                panel
                            } else {
                                Color.Transparent
                            },
                        ).clickable { select(target) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Glyph(target, label, tint = if (page == target) Color.White else muted)
                    Text(label, fontWeight = FontWeight.Bold, color = if (page == target) Color.White else muted)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().background(canvas)) { entries.forEach { entry(it, Modifier.weight(1f)) } }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun Glyph(
    name: String,
    description: String,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.White,
) {
    val path =
        when (name) {
            "home" -> {
                "M12 3 2 11h3v10h5v-6h4v6h5V11h3Z"
            }

            "search" -> {
                "M10 2a8 8 0 1 0 4.9 14.3L21 22l1-1-5.7-6.1A8 8 0 0 0 10 2Zm0 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12Z"
            }

            "library" -> {
                "M3 3h2v18H3Zm5 0h2v18H8Zm5 1 2-.5 5 17-2 .5Z"
            }

            "play" -> {
                "M7 3v18l15-9Z"
            }

            "pause" -> {
                "M6 4h4v16H6Zm8 0h4v16h-4Z"
            }

            "next" -> {
                "M5 4v16l12-8ZM18 4h3v16h-3Z"
            }

            "previous" -> {
                "M19 4v16L7 12ZM3 4h3v16H3Z"
            }

            "profile" -> {
                "M12 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10ZM3 22v-3c0-7 18-7 18 0v3Z"
            }

            "down" -> {
                "m3 8 9 9 9-9-2-2-7 7-7-7Z"
            }

            "close" -> {
                "m5 4 7 7 7-7 1 1-7 7 7 7-1 1-7-7-7 7-1-1 7-7-7-7Z"
            }

            "back" -> {
                "m12 3 2 2-6 6h14v2H8l6 6-2 2-9-9Z"
            }

            "queue" -> {
                "M3 4h18v2H3Zm0 6h18v2H3Zm0 6h12v2H3Zm14-2 6 4-6 4Z"
            }

            "add" -> {
                "M11 3h2v8h8v2h-8v8h-2v-8H3v-2h8Z"
            }

            "shuffle" -> {
                "M3 5h4l10 12h2v-3l5 4-5 4v-3h-3L6 7H3Zm0 12h4l3-4 2 2-4 4H3Zm10-8 3-4h3V2l5 4-5 4V7h-2l-3 4Z"
            }

            "repeat" -> {
                "M5 4h13V1l5 4-5 4V6H5v5H3V6Zm14 16H6v3l-5-4 5-4v3h13v-5h2v5Z"
            }

            "expand" -> {
                "M3 3h7v2H5v5H3Zm11 0h7v7h-2V5h-5ZM3 14h2v5h5v2H3Zm16 0h2v7h-7v-2h5Z"
            }

            "brand" -> {
                "M3 17V7h3l6 7 6-7h3v10h-3v-6l-6 7-6-7v6Z"
            }

            "settings" -> {
                "M10 2h4l.7 3 2 .9 2.8-.9 2 3.5-2.2 2v2.9l2.2 2-2 3.5-2.8-.9-2 .9-.7 3" +
                    "h-4l-.7-3-2-.9-2.8.9-2-3.5 2.2-2v-2.9l-2.2-2 2-3.5 2.8.9 2-.9Zm2 6a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z"
            }

            else -> {
                "M4 3h16v18H4Zm2 2v14h12V5Zm2 3h8v2H8Zm0 5h8v2H8Z"
            }
        }
    val vector =
        remember(name) {
            ImageVector
                .Builder(
                    name,
                    24.dp,
                    24.dp,
                    24f,
                    24f,
                ).addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White))
                .build()
        }
    Icon(vector, description, modifier, tint = tint)
}
