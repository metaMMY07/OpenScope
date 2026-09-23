package dev.mediasearch.library

import org.junit.Assert.*
import org.junit.Test

class LibraryMergeTest {
    @Test fun `older backup cannot overwrite edits or restore deliberately cleared note`() {
        assertEquals("新备注", LocalLibraryCodec.MergePolicy.preserveText("新备注", "旧备注"))
        assertEquals("", LocalLibraryCodec.MergePolicy.preserveText("", "旧备注"))
        assertEquals("导入备注", LocalLibraryCodec.MergePolicy.preserveText(null, "导入备注"))
    }
    @Test fun `restoring older history never moves visit backwards`() {
        assertEquals(200L, LocalLibraryCodec.MergePolicy.newestTimestamp(200L, 100L))
        assertEquals(300L, LocalLibraryCodec.MergePolicy.newestTimestamp(200L, 300L))
    }
    @Test(expected = IllegalArgumentException::class) fun `wrapped integer cannot pretend to be supported backup version`() {
        LocalLibraryCodec.decode("""{"version":4294967297,"items":[]}""")
    }
}
