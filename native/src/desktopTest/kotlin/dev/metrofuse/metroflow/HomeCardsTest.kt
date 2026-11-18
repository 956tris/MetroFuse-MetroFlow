package dev.metrofuse.metroflow

import dev.metrofuse.api.CatalogAlbum
import dev.metrofuse.api.HomeItem
import dev.metrofuse.api.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCardsTest {
    @Test
    fun quickCardsSkipEmptyItemsAndKeepDifferentKindsWithTheSameId() {
        val track = HomeItem(track = Track("42", "Track", "Artist", "Album"))
        val album = HomeItem(album = CatalogAlbum("42", "Album", "Artist"))
        assertEquals(listOf(track, album), quickHomeItems(listOf(HomeItem(), track, album, track)))
    }
}
