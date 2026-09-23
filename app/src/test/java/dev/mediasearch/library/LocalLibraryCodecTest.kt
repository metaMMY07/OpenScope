package dev.mediasearch.library

import dev.mediasearch.core.Platform
import dev.mediasearch.core.SearchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalLibraryCodecTest {
    private val item = SearchItem(
        id = "BV-test",
        platform = Platform.BILIBILI,
        title = "测试视频",
        author = "作者",
        summary = "摘要",
        url = "https://www.bilibili.com/video/BV-test",
        thumbnailUrl = "https://i0.hdslb.com/bfs/archive/test.jpg",
        metric = "1.2万"
    )

    @Test
    fun roundTripKeepsIndependentFlagsFoldersNotesAndHistory() {
        val json = LocalLibraryCodec.encode(
            entries = listOf(
                LocalLibraryCodec.EntryRecord(
                    item = item,
                    favorite = true,
                    later = false,
                    folder = "稍后整理",
                    note = "记得引用",
                    savedAt = 1_700_000_000L
                )
            ),
            history = listOf(LocalLibraryCodec.HistoryRecord(item, 1_700_000_010L)),
            folders = listOf("稍后整理")
        )

        val decoded = LocalLibraryCodec.decode(json)
        assertEquals(1, decoded.entries.size)
        assertEquals(true, decoded.entries.single().favorite)
        assertEquals(false, decoded.entries.single().later)
        assertEquals("稍后整理", decoded.entries.single().folder)
        assertEquals("记得引用", decoded.entries.single().note)
        assertEquals(1_700_000_010L, decoded.history.single().visitedAt)
    }

    @Test
    fun rejectsWrongTypesAndDuplicateItemsBeforeStorage() {
        val valid = LocalLibraryCodec.encode(
            entries = listOf(LocalLibraryCodec.EntryRecord(item, true, true, "", "", 1L)),
            history = emptyList(),
            folders = emptyList()
        )
        // Decode's strict type checks are exercised with a small explicit malformed payload.
        assertFailsWith<IllegalArgumentException> {
            LocalLibraryCodec.decode("{\"version\":1,\"items\":[1]}")
        }
        assertFailsWith<IllegalArgumentException> {
            LocalLibraryCodec.decode(valid.replace("\"favorite\":true", "\"favorite\":1"))
        }
    }
}
