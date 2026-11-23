package dev.metrofuse.metroflow

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import dev.metrofuse.api.HomeItem
import dev.metrofuse.api.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object SpotifyColors {
    val OuterBlack = Color(0xFF000000)
    val BaseSurface = Color(0xFF121212)
    val Elevated = Color(0xFF1F1F1F)
    val Card = Color(0xFF282828)
    val CardHover = Color(0xFF2E2E2E)
    val Highlight = Color(0xFF333333)
    val Green = Color(0xFF1ED760)
    val GreenPressed = Color(0xFF1DB954)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFFB3B3B3)
    val TextSubtle = Color(0xFF727272)
    val Hairline = Color(0x1FFFFFFF)
    val Danger = Color(0xFFE91429)
}

internal val SpotifyDarkColorScheme =
    darkColorScheme(
        primary = SpotifyColors.Green,
        onPrimary = Color.Black,
        background = SpotifyColors.BaseSurface,
        surface = SpotifyColors.BaseSurface,
        surfaceVariant = SpotifyColors.Elevated,
        onSurface = SpotifyColors.TextPrimary,
        onSurfaceVariant = SpotifyColors.TextMuted,
        outline = SpotifyColors.Hairline,
    )

internal data class CollectionInfo(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: String,
)

internal data class BrowseCategory(
    val title: String,
    val query: String,
    val color: Color,
)

internal val spotifyBrowseCategories =
    listOf(
        BrowseCategory("Pop", "Pop hits", Color(0xFF148A08)),
        BrowseCategory("Hip-Hop", "Hip Hop", Color(0xFFBC5900)),
        BrowseCategory("Electronic", "Electronic dance", Color(0xFF006450)),
        BrowseCategory("Rock", "Rock", Color(0xFFE91429)),
        BrowseCategory("R&B", "R&B soul", Color(0xFFDC148C)),
        BrowseCategory("Chill", "Chill lofi", Color(0xFF477D95)),
        BrowseCategory("Workout", "Workout energy", Color(0xFF777777)),
        BrowseCategory("Indie", "Indie", Color(0xFF8D67AB)),
    )

internal fun HomeItem.info(): CollectionInfo? =
    when {
        track != null -> track!!.let { CollectionInfo("track", it.id, it.title, it.artist, it.artwork) }
        album != null -> album!!.let { CollectionInfo("album", it.id, it.title, it.artist, it.artwork) }
        artist != null -> artist!!.let { CollectionInfo("artist", it.id, it.name, "", it.artwork) }
        playlist != null -> playlist!!.let { CollectionInfo("playlist", it.id, it.title, it.author, it.artwork) }
        else -> null
    }

internal fun quickHomeItems(items: List<HomeItem>): List<HomeItem> =
    items
        .filter { it.info() != null }
        .distinctBy { it.info()?.let { info -> info.kind to info.id } }
        .take(8)

internal fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal fun artworkAccentTint(seed: String): Color {
    if (seed.isBlank()) return Color(0xFF203A2E)
    val palette =
        listOf(
            Color(0xFF1F4E3D),
            Color(0xFF3E2A56),
            Color(0xFF4D232A),
            Color(0xFF1E3A5F),
            Color(0xFF4B3B1E),
            Color(0xFF28424A),
        )
    return palette[(seed.hashCode() and 0x7FFFFFFF) % palette.size]
}

internal data class LyricLineUi(
    val text: String,
    val timeMs: Long?,
)

internal fun parseLyricsLines(response: JsonObject): List<LyricLineUi> {
    val lyricsObj = response["lyrics"] as? JsonObject ?: return emptyList()
    val lines = lyricsObj["lines"] as? JsonArray ?: return emptyList()
    return lines.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (text.isBlank()) return@mapNotNull null
        val timeMs = obj["timeMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        LyricLineUi(text = text, timeMs = timeMs)
    }
}

@Composable
internal fun Glyph(
    name: String,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val finalModifier = if (modifier == Modifier) Modifier.run { this } else modifier
    val path =
        when (name) {
            "home" -> "M12 3 2 11h3v10h5v-6h4v6h5V11h3Z"
            "home_outline" -> "M12 3 2 11h3v10h6v-6h2v6h6V11h3Zm5 16h-2v-6H9v6H7v-8.8L12 6l5 4.2Z"
            "search" -> "M10.5 3a7.5 7.5 0 1 0 4.7 13.3l4.7 4.8 1.4-1.4-4.7-4.8A7.5 7.5 0 0 0 10.5 3Zm0 2a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11Z"
            "library" -> "M3 3h2v18H3Zm5 0h2v18H8Zm5 1 2-.5 5 17-2 .5Z"
            "play" -> "M7 4v16l13-8Z"
            "pause" -> "M6 4h4v16H6Zm8 0h4v16h-4Z"
            "next" -> "M5 4v16l12-8ZM18 4h3v16h-3Z"
            "previous" -> "M19 4v16L7 12ZM3 4h3v16H3Z"
            "profile" -> "M12 3a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9ZM4 20.5c0-3.8 3.6-6 8-6s8 2.2 8 6V22H4Z"
            "down" -> "m4 8 8 8 8-8-1.5-1.5L12 13 5.5 6.5Z"
            "close" -> "m6 5 6 6 6-6 1 1-6 6 6 6-1 1-6-6-6 6-1-1 6-6-6-6Z"
            "back" -> "m12 4 1.5 1.5L7.8 11H20v2H7.8l5.7 5.5L12 20l-8-8Z"
            "queue" -> "M3 5h18v2H3Zm0 5h18v2H3Zm0 5h12v2H3Zm14-1 5 3.5-5 3.5Z"
            "lyrics" -> "M4 4h16v12H7l-3 3V4Zm3 4v2h10V8Zm0 4v2h7v-2Z"
            "add" -> "M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7Z"
            "check" -> "m9.5 16.2-4.2-4.2-1.4 1.4 5.6 5.6 10.6-10.6-1.4-1.4Z"
            "shuffle" -> "M3 5h4l10 12h2v-3l5 4-5 4v-3h-3L6 7H3Zm0 12h4l3-4 2 2-4 4H3Zm10-8 3-4h3V2l5 4-5 4V7h-2l-3 4Z"
            "repeat" -> "M5 4h13V1l5 4-5 4V6H5v5H3V6Zm14 16H6v3l-5-4 5-4v3h13v-5h2v5Z"
            "expand" -> "M4 4h6v2H6v4H4Zm10 0h6v6h-2V6h-4ZM4 14h2v4h4v2H4Zm14 0h2v6h-6v-2h4Z"
            "brand" -> "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2Zm4.6 14.3a.8.8 0 0 1-1.1.3c-3-1.8-6.7-2.2-11.1-1.2a.8.8 0 1 1-.4-1.6c4.8-1.1 8.9-.6 12.3 1.4.4.2.5.7.3 1.1Zm1.2-2.8a1 1 0 0 1-1.4.3c-3.4-2.1-8.6-2.7-12.6-1.5a1 1 0 1 1-.6-1.9c4.6-1.4 10.3-.7 14.2 1.7.5.3.6.9.4 1.4Zm.1-3c-4.1-2.4-10.8-2.6-14.7-1.5a1.2 1.2 0 1 1-.7-2.3c4.5-1.3 11.9-1.1 16.6 1.7a1.2 1.2 0 0 1-1.2 2.1Z"
            "metroflow_mark" -> "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2ZM6 14.5v-5h2v5Zm3.5 2v-9h2v9Zm3.5-3v-3h2v3Zm3.5 1.5v-6h2v6Z"
            "source" -> "M4 6h16v2H4Zm0 5h16v2H4Zm0 5h10v2H4Z"
            "settings" ->
                "M10 2h4l.7 3 2 .9 2.8-.9 2 3.5-2.2 2v2.9l2.2 2-2 3.5-2.8-.9-2 .9-.7 3" +
                    "h-4l-.7-3-2-.9-2.8.9-2-3.5 2.2-2v-2.9l-2.2-2 2-3.5 2.8.9 2-.9Zm2 6a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z"
            "panel" -> "M3 4h18v16H3Zm2 2v12h9V6Zm11 0v12h3V6Z"
            else -> "M4 3h16v18H4Zm2 2v14h12V5Zm2 3h8v2H8Zm0 5h8v2H8Z"
        }
    val vector =
        remember(name) {
            ImageVector
                .Builder(name, 24.dp, 24.dp, 24f, 24f)
                .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White))
                .build()
        }
    Icon(vector, description, finalModifier, tint = tint)
}
