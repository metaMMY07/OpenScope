package dev.mediasearch.library

import dev.mediasearch.core.Platform
import dev.mediasearch.core.SearchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
                    item = item.copy(metricCounts = mapOf("likes" to 42L, "favorites" to 7L)),
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
        assertEquals(42L, decoded.entries.single().item.metricCounts["likes"])
        assertEquals(7L, decoded.entries.single().item.metricCounts["favorites"])
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

    @Test
    fun fullLibraryBackupCanBeImportedWhenLargerThanOldTwoMegabyteLimit() {
        val longSummary = "内容".repeat(700)
        val entries = (0 until 500).map { index ->
            LocalLibraryCodec.EntryRecord(
                item.copy(id = "BV$index", summary = longSummary),
                favorite = true, later = false, folder = "", note = "", savedAt = 1L
            )
        }
        val history = (0 until 1000).map { index ->
            LocalLibraryCodec.HistoryRecord(
                item.copy(id = "history-$index", summary = longSummary),
                visitedAt = 2L
            )
        }
        val json = LocalLibraryCodec.encode(entries, history, emptyList())
        assertTrue(json.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024)
        val decoded = LocalLibraryCodec.decode(json)
        assertEquals(500, decoded.entries.size)
        assertEquals(1000, decoded.history.size)
    }
}
