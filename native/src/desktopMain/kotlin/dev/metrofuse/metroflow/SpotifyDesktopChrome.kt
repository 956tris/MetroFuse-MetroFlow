package dev.metrofuse.metroflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metrofuse.api.Player
import dev.metrofuse.api.Track
import dev.metrofuse.api.UiArtworkEnvironment
import dev.metrofuse.sdk.resources.Res
import dev.metrofuse.sdk.resources.home
import dev.metrofuse.sdk.resources.library
import dev.metrofuse.sdk.resources.plugins
import dev.metrofuse.sdk.resources.search
import dev.metrofuse.sdk.resources.settings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopTopBar(
    query: String,
    accountName: String,
    sourceTitle: String,
    onHome: () -> Unit,
    onSearch: (String) -> Unit,
    onFocusSearch: () -> Unit,
    onSources: () -> Unit,
    onPlugins: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SpotifyColors.OuterBlack)
            .padding(start = 16.dp, end = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onHome) {
            Glyph("metroflow_mark", "MetroFlow", Modifier.size(30.dp), SpotifyColors.Green)
        }
        Surface(onClick = onHome, shape = CircleShape, color = SpotifyColors.Elevated, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Glyph("home", stringResource(Res.string.home), Modifier.size(22.dp), Color.White)
            }
        }
        SearchPillInput(
            query = query,
            onQuery = onSearch,
            modifier = Modifier.weight(1f).widthIn(max = 460.dp),
            onFocus = onFocusSearch,
        )
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onSources,
            shape = CircleShape,
            color = SpotifyColors.Elevated,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Glyph("source", "Source", Modifier.size(16.dp), SpotifyColors.Green)
                Text(
                    sourceTitle.ifBlank { "Offline" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onPlugins) {
            Glyph("settings", stringResource(Res.string.plugins), Modifier.size(20.dp), SpotifyColors.TextMuted)
        }
        Surface(onClick = onSettings, shape = CircleShape, color = SpotifyColors.Green, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (accountName.isNotBlank()) {
                    Text(accountName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Glyph("profile", stringResource(Res.string.settings), Modifier.size(17.dp), Color.Black)
                }
            }
        }
    }
}

@Composable
internal fun SearchPillInput(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    Row(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(SpotifyColors.Elevated)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph("search", stringResource(Res.string.search), Modifier.size(19.dp), SpotifyColors.TextMuted)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocus() },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { input ->
                Box {
                    if (query.isEmpty()) {
                        Text("What do you want to play?", color = SpotifyColors.TextMuted, fontSize = 14.sp, maxLines = 1)
                    }
                    input()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(28.dp)) {
                Glyph("close", "Clear", Modifier.size(16.dp), SpotifyColors.TextMuted)
            }
        }
    }
}

@Composable
internal fun DesktopLibrarySidebar(
    width: Dp,
    page: String,
    libraryTracks: List<Track>,
    player: Player,
    environment: UiArtworkEnvironment,
    onNavigate: (String) -> Unit,
) {
    var filter by remember { mutableStateOf("All") }
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyColors.BaseSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onNavigate("library") },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph("library", stringResource(Res.string.library), Modifier.size(22.dp), if (page == "library") Color.White else SpotifyColors.TextMuted)
            Text(
                "Your Library",
                color = if (page == "library") Color.White else SpotifyColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onNavigate("search") }, modifier = Modifier.size(30.dp)) {
                Glyph("search", stringResource(Res.string.search), Modifier.size(18.dp), SpotifyColors.TextMuted)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("All", "Songs", "Artists", "Albums")) { label ->
                FilterChip(
                    selected = filter == label,
                    onClick = { filter = label },
                    shape = CircleShape,
                    border = null,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = SpotifyColors.Elevated,
                            labelColor = Color.White,
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                        ),
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                )
            }
        }

        val shownTracks =
            remember(libraryTracks, filter) {
                when (filter) {
                    "Artists" -> libraryTracks.distinctBy { it.artist }.take(30)
                    "Albums" -> libraryTracks.filter { it.album.isNotBlank() }.distinctBy { it.album }.take(30)
                    else -> libraryTracks.take(40)
                }
            }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shownTracks) { track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = "playTracks" in player.capabilities) {
                            player.playTracks(libraryTracks, libraryTracks.indexOf(track).coerceAtLeast(0))
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    environment.Artwork(
                        track.artwork,
                        track.title,
                        Modifier
                            .size(46.dp)
                            .clip(if (filter == "Artists") CircleShape else RoundedCornerShape(4.dp))
                            .background(SpotifyColors.Card),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when (filter) {
                                "Artists" -> track.artist.ifBlank { track.title }
                                "Albums" -> track.album.ifBlank { track.title }
                                else -> track.title
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (filter == "Artists") "Artist" else track.artist,
                            color = SpotifyColors.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
