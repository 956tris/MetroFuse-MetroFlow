package dev.metrofuse.metroflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.metrofuse.api.HomeItem
import dev.metrofuse.api.NativePlugin
import dev.metrofuse.api.NativeScreen
import dev.metrofuse.api.PluginContext
import dev.metrofuse.api.SearchResult
import dev.metrofuse.api.Track
import dev.metrofuse.api.UiArtworkEnvironment
import dev.metrofuse.api.UiContext
import dev.metrofuse.api.UiPlaybackControls
import dev.metrofuse.sdk.PlaybackLabels
import dev.metrofuse.sdk.PluginPlaybackControls
import dev.metrofuse.sdk.PluginSeekBar
import dev.metrofuse.sdk.resources.Res
import dev.metrofuse.sdk.resources.add_queue
import dev.metrofuse.sdk.resources.back
import dev.metrofuse.sdk.resources.brand
import dev.metrofuse.sdk.resources.empty_home
import dev.metrofuse.sdk.resources.empty_library
import dev.metrofuse.sdk.resources.empty_playback
import dev.metrofuse.sdk.resources.empty_search
import dev.metrofuse.sdk.resources.home
import dev.metrofuse.sdk.resources.library
import dev.metrofuse.sdk.resources.load_more
import dev.metrofuse.sdk.resources.loading
import dev.metrofuse.sdk.resources.next
import dev.metrofuse.sdk.resources.no_results
import dev.metrofuse.sdk.resources.pause
import dev.metrofuse.sdk.resources.play
import dev.metrofuse.sdk.resources.plugins
import dev.metrofuse.sdk.resources.previous
import dev.metrofuse.sdk.resources.queue
import dev.metrofuse.sdk.resources.retry
import dev.metrofuse.sdk.resources.search
import dev.metrofuse.sdk.resources.settings
import dev.metrofuse.sdk.resources.tagline
import dev.metrofuse.sdk.searchUiCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

class MetroFlowPlugin : NativePlugin {
    override suspend fun start(context: PluginContext) {
        val plugin = context
        context.registerUi(
            "ui.app",
            object : NativeScreen {
                @Composable
                override fun Content(context: UiContext) = MetroFlow(context, plugin)
            },
        )
    }
}

private val ink = Color(0xFF121212)
private val panel = Color(0xFF1C1C1C)
private val green = Color(0xFF1ED760)

@Composable
private fun MetroFlow(
    ui: UiContext,
    plugin: PluginContext,
) {
    // The host supplies artwork, reactive library and navigation without a second player.
    val environment = ui.extensions as? UiArtworkEnvironment ?: return
    val playback by ui.player.state.collectAsState()
    var page by remember { mutableStateOf("home") }
    var offset by remember { mutableIntStateOf(0) }
    val libraryPage by remember(offset) { environment.library(offset, 100) }.collectAsState(emptyList())
    val library = libraryPage
    val shelves by environment.homeShelves.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResult(emptyList())) }
    var searching by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var collectionFailed by remember { mutableStateOf(false) }
    var collectionTitle by remember { mutableStateOf("") }
    var collectionTracks by remember { mutableStateOf(emptyList<Track>()) }
    var collectionGeneration by remember { mutableIntStateOf(0) }
    var collectionJob by remember { mutableStateOf<Job?>(null) }
    val loading =
        if (page == "search") {
            searching
        } else if (page == "collection") {
            resolving
        } else {
            false
        }
    val failed =
        if (page == "search") {
            searchFailed
        } else if (page == "collection") {
            collectionFailed
        } else {
            false
        }
    LaunchedEffect(page) {
        if (page != "collection") collectionJob?.cancel()
    }
    val codec = remember { Json { ignoreUnknownKeys = true } }
    LaunchedEffect(query, page) {
        if (page != "search") return@LaunchedEffect
        results = SearchResult(emptyList())
        searchFailed = false
        if (query.isBlank()) return@LaunchedEffect
        delay(300)
        searching = true
        try {
            results = plugin.searchUiCatalog(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            searchFailed = true
        } finally {
            searching = false
        }
    }

    fun openCollection(
        kind: String,
        id: String,
        title: String,
    ) {
        val generation = ++collectionGeneration
        collectionJob?.cancel()
        page = "collection"
        collectionTitle = title
        collectionTracks = emptyList()
        resolving = true
        collectionFailed = false
        collectionJob =
            scope.launch {
                try {
                    val response =
                        plugin.callService(
                            "ui.collection/v1",
                            buildJsonObject {
                                put("kind", kind)
                                put("id", id)
                            },
                        )
                    val item = response["item"]?.jsonObject
                    if (generation != collectionGeneration) return@launch
                    collectionTracks = item?.get("tracks")?.let { codec.decodeFromJsonElement<List<Track>>(it) }.orEmpty()
                    collectionFailed = item == null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == collectionGeneration) collectionFailed = true
                } finally {
                    if (generation == collectionGeneration) resolving = false
                }
            }
    }

    fun openItem(item: HomeItem) {
        item.track?.let { if ("playTracks" in ui.player.capabilities) ui.player.playTracks(listOf(it)) }
        item.album?.let { openCollection("album", it.id, it.title) }
        item.artist?.let { openCollection("artist", it.id, it.name) }
        item.playlist?.let { openCollection("playlist", it.id, it.title) }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = green, onPrimary = Color.Black, background = ink, surface = panel)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(ink)) {
            val wide = maxWidth >= 840.dp
            Column(
                Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (wide) {
                        Surface(Modifier.width(220.dp).fillMaxHeight(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(Res.string.brand),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(stringResource(Res.string.tagline), color = Color.LightGray)
                                Navigation({ page = it }, ui)
                            }
                        }
                    }
                    Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(if (wide) 28.dp else 16.dp)) {
                            Text(
                                if (page ==
                                    "collection"
                                ) {
                                    collectionTitle
                                } else {
                                    stringResource(
                                        when (page) {
                                            "search" -> Res.string.search
                                            "library" -> Res.string.library
                                            "queue" -> Res.string.queue
                                            else -> Res.string.home
                                        },
                                    )
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (page == "collection") TextButton(onClick = { page = "home" }) { Text(stringResource(Res.string.back)) }
                            Spacer(Modifier.height(20.dp))
                            when (page) {
                                "home" -> {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                        if (shelves.isEmpty()) {
                                            item {
                                                Text(stringResource(Res.string.empty_home))
                                                Button(onClick = { ui.navigate("plugins") }) { Text(stringResource(Res.string.plugins)) }
                                            }
                                        }
                                        shelves.forEach { shelf ->
                                            item {
                                                Text(shelf.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(12.dp))
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    items(shelf.items) { item ->
                                                        val title =
                                                            item.track?.title ?: item.album?.title ?: item.artist?.name
                                                                ?: item.playlist?.title.orEmpty()
                                                        val artwork =
                                                            item.track?.artwork ?: item.album?.artwork ?: item.artist?.artwork
                                                                ?: item.playlist?.artwork.orEmpty()
                                                        Column(
                                                            Modifier
                                                                .width(160.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    openItem(item)
                                                                }.padding(8.dp),
                                                        ) {
                                                            environment.Artwork(
                                                                artwork,
                                                                title,
                                                                Modifier.size(144.dp).clip(RoundedCornerShape(8.dp)).background(ink),
                                                            )
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(
                                                                title,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                fontWeight = FontWeight.SemiBold,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    if (page ==
                                        "search"
                                    ) {
                                        OutlinedTextField(
                                            query,
                                            { query = it },
                                            Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(Res.string.search)) },
                                            singleLine = true,
                                        )
                                    }
                                    if (loading) Text(stringResource(Res.string.loading))
                                    if (failed) Text(stringResource(Res.string.retry))
                                    val tracks =
                                        when (page) {
                                            "queue" -> playback.queue
                                            "search" -> results.tracks
                                            "collection" -> collectionTracks
                                            else -> library
                                        }
                                    LazyColumn {
                                        if (page == "search") {
                                            items(
                                                results.albums,
                                            ) { album ->
                                                TextButton(
                                                    onClick = { openCollection("album", album.id, album.title) },
                                                ) { Text(album.title) }
                                            }
                                            items(
                                                results.artists,
                                            ) { artist ->
                                                TextButton(
                                                    onClick = { openCollection("artist", artist.id, artist.name) },
                                                ) { Text(artist.name) }
                                            }
                                            items(results.playlists) { playlist ->
                                                TextButton(onClick = {
                                                    openCollection("playlist", playlist.id, playlist.title)
                                                }) { Text(playlist.title) }
                                            }
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
                                                )
                                            }
                                        }
                                        items(tracks.indices.toList()) { index ->
                                            val track = tracks[index]
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = "playTracks" in ui.player.capabilities) {
                                                        if (page == "queue" && "queueIndex" in ui.player.capabilities) {
                                                            (ui.player as? UiPlaybackControls)?.selectQueueIndex(index)
                                                        } else {
                                                            ui.player.playTracks(tracks, index)
                                                        }
                                                    }.padding(
                                                        vertical = 8.dp,
                                                    ),
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
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        track.title,
                                                        color =
                                                            if (track.id ==
                                                                playback.track.id
                                                            ) {
                                                                green
                                                            } else {
                                                                Color.White
                                                            },
                                                        maxLines = 1,
                                                    )
                                                    Text(track.artist, color = Color.LightGray, maxLines = 1)
                                                }
                                                if ("addToQueue" in
                                                    ui.player.capabilities
                                                ) {
                                                    TextButton(
                                                        onClick = { ui.player.addToQueue(listOf(track)) },
                                                    ) { Text(stringResource(Res.string.add_queue)) }
                                                }
                                            }
                                        }
                                        if (page == "library" && offset > 0) {
                                            item {
                                                TextButton(
                                                    onClick = { offset = (offset - 100).coerceAtLeast(0) },
                                                ) { Text(stringResource(Res.string.previous)) }
                                            }
                                        }
                                        if (page == "library" &&
                                            libraryPage.size == 100
                                        ) {
                                            item {
                                                TextButton(
                                                    onClick = { offset += 100 },
                                                ) { Text(stringResource(Res.string.load_more)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            environment.Artwork(
                                playback.track.artwork,
                                playback.track.title,
                                Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playback.track.title.ifBlank { stringResource(Res.string.empty_playback) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(playback.track.artist, color = Color.LightGray, maxLines = 1)
                            }
                            if (wide) {
                                Controls(ui)
                            } else {
                                Button(onClick = ui.player::toggle, enabled = playback.track.id.isNotBlank()) {
                                    Text(stringResource(if (playback.playing) Res.string.pause else Res.string.play))
                                }
                            }
                        }
                        PluginSeekBar(ui.player)
                        if (!wide) Controls(ui)
                    }
                }
                if (!wide) {
                    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Row {
                                Navigation({
                                    page =
                                        it
                                }, ui)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(ui: UiContext) =
    PluginPlaybackControls(
        ui.player,
        PlaybackLabels(
            stringResource(Res.string.previous),
            stringResource(Res.string.play),
            stringResource(Res.string.pause),
            stringResource(Res.string.next),
        ),
    )

@Composable
private fun Navigation(
    select: (String) -> Unit,
    ui: UiContext,
) {
    TextButton(onClick = { select("home") }) { Text(stringResource(Res.string.home)) }
    TextButton(onClick = { select("search") }) { Text(stringResource(Res.string.search)) }
    TextButton(onClick = { select("library") }) { Text(stringResource(Res.string.library)) }
    TextButton(onClick = { select("queue") }) { Text(stringResource(Res.string.queue)) }
    TextButton(onClick = { ui.navigate("settings") }) { Text(stringResource(Res.string.settings)) }
    TextButton(onClick = { ui.navigate("plugins") }) { Text(stringResource(Res.string.plugins)) }
}
