package dev.metrofuse.metroflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FrontendSelectionTest {
    @Test
    fun pickerRejectsMalformedSourcesAndStaleSelection() {
        val response = Json.parseToJsonElement("""{"selected":"disabled","sources":[{"id":"active","title":"Music"},{"id":"active","title":"Duplicate"},{"id":"","title":"Invalid"},{"id":false,"title":"Invalid"}]}""").jsonObject
        val parsed = parseFrontendSelection(response)
        assertEquals("", parsed.selected)
        assertEquals(listOf(FrontendChoice("active", "Music")), parsed.choices)
    }

    @Test
    fun pickerPreservesAvailableSourceSelection() {
        val response = Json.parseToJsonElement("""{"selected":"active","sources":[{"id":"active","title":"Music"}]}""").jsonObject
        assertEquals("active", parseFrontendSelection(response).selected)
    }
}
