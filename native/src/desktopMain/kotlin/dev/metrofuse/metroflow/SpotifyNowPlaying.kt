package dev.metrofuse.metroflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metrofuse.api.PlaybackState
import dev.metrofuse.api.Player
import dev.metrofuse.api.PluginContext
import dev.metrofuse.api.Track
import dev.metrofuse.api.UiArtworkEnvironment
import dev.metrofuse.api.UiPlaybackControls
import dev.metrofuse.sdk.PluginSeekBar
import dev.metrofuse.sdk.playbackFraction
import dev.metrofuse.sdk.resources.Res
import dev.metrofuse.sdk.resources.close_player
import dev.metrofuse.sdk.resources.next
import dev.metrofuse.sdk.resources.now_playing
import dev.metrofuse.sdk.resources.pause
import dev.metrofuse.sdk.resources.play
import dev.metrofuse.sdk.resources.previous
import dev.metrofuse.sdk.resources.queue
import dev.metrofuse.sdk.resources.repeat
import dev.metrofuse.sdk.resources.shuffle
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayButton(
    playing: Boolean,
    enabled: Boolean,
    click: () -> Unit,
    size: Int,
    color: Color = SpotifyColors.Green,
) {
    Surface(
        onClick = click,
        enabled = enabled,
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Glyph(
                if (playing) "pause" else "play",
                stringResource(if (playing) Res.string.pause else Res.string.play),
                Modifier.size((size * 0.52f).dp),
                Color.Black,
            )
        }
    }
}

@Composable
internal fun TrackMetadata(
    track: Track,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            track.title,
            color = SpotifyColors.TextPrimary,
            fontSize = if (large) 22.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            color = SpotifyColors.TextMuted,
            fontSize = if (large) 15.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NowPlayingSidebar(
    current: Track,
    playback: PlaybackState,
    environment: UiArtworkEnvironment,
    context: PluginContext,
    close: () -> Unit,
    expand: () -> Unit,
    openQueue: () -> Unit,
) {
    var showLyrics by remember { mutableStateOf(false) }
    var lyricsLines by remember(current.id) { mutableStateOf<List<LyricLineUi>>(emptyList()) }
    var loadingLyrics by remember(current.id) { mutableStateOf(false) }

    LaunchedEffect(showLyrics, current.id) {
        if (!showLyrics || current.id.isBlank()) return@LaunchedEffect
        loadingLyrics = true
        try {
            val res = context.callService("ui.lyrics/v1", buildJsonObject {})
            lyricsLines = parseLyricsLines(res)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            lyricsLines = emptyList()
        } finally {
            loadingLyrics = false
        }
    }

    LazyColumn(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyColors.BaseSurface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (showLyrics) "Lyrics" else stringResource(Res.string.now_playing),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                IconButton(onClick = { showLyrics = !showLyrics }, modifier = Modifier.size(32.dp)) {
                    Glyph(
                        "lyrics",
                        "Lyrics",
                        Modifier.size(18.dp),
                        if (showLyrics) SpotifyColors.Green else SpotifyColors.TextMuted,
                    )
                }
                IconButton(onClick = close, modifier = Modifier.size(32.dp)) {
                    Glyph("close", stringResource(Res.string.close_player), Modifier.size(18.dp), SpotifyColors.TextMuted)
                }
            }
        }

        if (showLyrics) {
            if (loadingLyrics) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpotifyColors.Green) }
            } else if (lyricsLines.isEmpty()) {
                item {
                    Text("No synced lyrics available for this track.", color = SpotifyColors.TextMuted, fontSize = 14.sp)
                }
            } else {
                itemsIndexed(lyricsLines) { idx, line ->
                    val nextTime = lyricsLines.getOrNull(idx + 1)?.timeMs
                    val isActive =
                        line.timeMs != null &&
                            playback.position >= line.timeMs &&
                            (nextTime == null || playback.position < nextTime)
                    Text(
                        line.text,
                        color = if (isActive) SpotifyColors.TextPrimary else SpotifyColors.TextMuted,
                        fontSize = if (isActive) 17.sp else 15.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = 23.sp,
                    )
                }
            }
        } else {
            item {
                environment.Artwork(
                    current.artwork,
                    current.title,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyColors.Card)
                        .clickable(onClick = expand),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        current.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.artist,
                        color = SpotifyColors.TextMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current.album.isNotBlank()) {
                        Text(
                            current.album,
                            color = SpotifyColors.TextSubtle,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val index = playback.currentIndex ?: playback.queue.indexOfFirst { it.id == playback.track.id }
            val next = playback.queue.getOrNull(index + 1).takeIf { index >= 0 }
            if (next != null) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyColors.Elevated)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Next in queue", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Open queue",
                                color = SpotifyColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(onClick = openQueue),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = openQueue),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            environment.Artwork(next.artwork, next.title, Modifier.size(46.dp).clip(RoundedCornerShape(4.dp)))
                            TrackMetadata(next, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopPlayerBar(
    player: Player,
    playback: PlaybackState,
    current: Track,
    environment: UiArtworkEnvironment,
    nowPlayingVisible: Boolean,
    toggleNowPlaying: () -> Unit,
    expand: () -> Unit,
    openQueue: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(SpotifyColors.OuterBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = expand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            environment.Artwork(current.artwork, current.title, Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(SpotifyColors.Card))
            TrackMetadata(current, Modifier.weight(1f))
        }

        Column(Modifier.weight(1.45f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val controls = player as? UiPlaybackControls
                IconButton(
                    onClick = { controls?.toggleShuffle() },
                    enabled = controls != null && "shuffle" in player.capabilities,
                    modifier = Modifier.size(32.dp),
                ) {
                    Glyph("shuffle", stringResource(Res.string.shuffle), Modifier.size(17.dp), SpotifyColors.TextMuted)
                }
                IconButton(onClick = player::previous, enabled = "previous" in player.capabilities, modifier = Modifier.size(32.dp)) {
                    Glyph("previous", stringResource(Res.string.previous), Modifier.size(18.dp), SpotifyColors.TextMuted)
                }
                PlayButton(playback.playing, "toggle" in player.capabilities, player::toggle, 34, Color.White)
                IconButton(onClick = player::next, enabled = "next" in player.capabilities, modifier = Modifier.size(32.dp)) {
                    Glyph("next", stringResource(Res.string.next), Modifier.size(18.dp), SpotifyColors.TextMuted)
                }
                IconButton(
                    onClick = { controls?.cycleRepeat() },
                    enabled = controls != null && "repeat" in player.capabilities,
                    modifier = Modifier.size(32.dp),
                ) {
                    Glyph("repeat", stringResource(Res.string.repeat), Modifier.size(17.dp), SpotifyColors.TextMuted)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(playback.position), color = SpotifyColors.TextMuted, fontSize = 11.sp)
                Box(Modifier.weight(1f)) { PluginSeekBar(player) }
                Text(formatTime(current.duration), color = SpotifyColors.TextMuted, fontSize = 11.sp)
            }
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = toggleNowPlaying) {
                Glyph(
                    "panel",
                    "Now playing panel",
                    Modifier.size(19.dp),
                    if (nowPlayingVisible) SpotifyColors.Green else SpotifyColors.TextMuted,
                )
            }
            IconButton(onClick = openQueue) {
                Glyph("queue", stringResource(Res.string.queue), Modifier.size(19.dp), SpotifyColors.TextMuted)
            }
            IconButton(onClick = expand) {
                Glyph("expand", stringResource(Res.string.now_playing), Modifier.size(19.dp), SpotifyColors.TextMuted)
            }
        }
    }
}

@Composable
internal fun CompactMiniPlayer(
    player: Player,
    playback: PlaybackState,
    current: Track,
    environment: UiArtworkEnvironment,
    open: () -> Unit,
) {
    val fraction = playbackFraction(playback.position, current.duration)
    Column(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyColors.Card)
            .clickable(onClick = open),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            environment.Artwork(current.artwork, current.title, Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(SpotifyColors.Elevated))
            TrackMetadata(current, Modifier.weight(1f))
            IconButton(onClick = player::toggle, enabled = "toggle" in player.capabilities, modifier = Modifier.size(38.dp)) {
                Glyph(
                    if (playback.playing) "pause" else "play",
                    stringResource(if (playback.playing) Res.string.pause else Res.string.play),
                    Modifier.size(22.dp),
                    Color.White,
                )
            }
            if ("next" in player.capabilities) {
                IconButton(onClick = player::next, modifier = Modifier.size(38.dp)) {
                    Glyph("next", stringResource(Res.string.next), Modifier.size(20.dp), Color.White)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
        }
    }
}

@Composable
internal fun ExpandedSpotifyPlayer(
    player: Player,
    playback: PlaybackState,
    current: Track,
    environment: UiArtworkEnvironment,
    context: PluginContext,
    wide: Boolean,
    close: () -> Unit,
    openQueue: () -> Unit,
) {
    val tint = remember(current.id, current.artwork) { artworkAccentTint(current.id + current.artwork) }
    var showLyrics by remember { mutableStateOf(false) }
    var lyricsLines by remember(current.id) { mutableStateOf<List<LyricLineUi>>(emptyList()) }

    LaunchedEffect(showLyrics, current.id) {
        if (!showLyrics || current.id.isBlank()) return@LaunchedEffect
        try {
            lyricsLines = parseLyricsLines(context.callService("ui.lyrics/v1", buildJsonObject {}))
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            lyricsLines = emptyList()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(tint, Color(0xFF161616), SpotifyColors.BaseSurface),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 96.dp else 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(end = if (wide) 40.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) {
                    Glyph("down", stringResource(Res.string.close_player), Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = SpotifyColors.TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        current.album.ifBlank { current.artist.ifBlank { stringResource(Res.string.now_playing) } },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Glyph("lyrics", "Lyrics", Modifier.size(20.dp), if (showLyrics) SpotifyColors.Green else Color.White)
                }
                IconButton(onClick = openQueue) {
                    Glyph("queue", stringResource(Res.string.queue), Modifier.size(20.dp))
                }
            }

            AnimatedContent(targetState = showLyrics, label = "expanded content") { lyricsMode ->
                if (lyricsMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(tint.copy(alpha = 0.65f))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Lyrics", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (lyricsLines.isEmpty()) {
                            Text("No lyrics available for this track.", color = SpotifyColors.TextMuted)
                        } else {
                            lyricsLines.forEachIndexed { idx, line ->
                                val nextTime = lyricsLines.getOrNull(idx + 1)?.timeMs
                                val active = line.timeMs != null && playback.position >= line.timeMs && (nextTime == null || playback.position < nextTime)
                                Text(
                                    line.text,
                                    color = if (active) Color.White else Color(0x99FFFFFF),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 26.sp,
                                )
                            }
                        }
                    }
                } else {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val artSize = minOf(maxWidth, maxHeight, 420.dp)
                        environment.Artwork(
                            current.artwork,
                            current.title,
                            Modifier.size(artSize).clip(RoundedCornerShape(10.dp)).background(SpotifyColors.Card),
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TrackMetadata(current, Modifier.fillMaxWidth(), large = true)
                PluginSeekBar(player)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(playback.position), color = SpotifyColors.TextMuted, fontSize = 12.sp)
                    Text(formatTime(current.duration), color = SpotifyColors.TextMuted, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val controls = player as? UiPlaybackControls
                    IconButton(onClick = { controls?.toggleShuffle() }, enabled = controls != null && "shuffle" in player.capabilities) {
                        Glyph("shuffle", stringResource(Res.string.shuffle), Modifier.size(22.dp), SpotifyColors.TextMuted)
                    }
                    IconButton(onClick = player::previous, enabled = "previous" in player.capabilities) {
                        Glyph("previous", stringResource(Res.string.previous), Modifier.size(34.dp))
                    }
                    PlayButton(playback.playing, "toggle" in player.capabilities, player::toggle, 64, Color.White)
                    IconButton(onClick = player::next, enabled = "next" in player.capabilities) {
                        Glyph("next", stringResource(Res.string.next), Modifier.size(34.dp))
                    }
                    IconButton(onClick = { controls?.cycleRepeat() }, enabled = controls != null && "repeat" in player.capabilities) {
                        Glyph("repeat", stringResource(Res.string.repeat), Modifier.size(22.dp), SpotifyColors.TextMuted)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
