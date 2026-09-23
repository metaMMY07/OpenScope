package dev.mediasearch.core

import org.junit.Assert.*
import org.junit.Test

class DisplayCountTest {
    @Test fun `displayed numbers and units`() {
        assertEquals(12000L, displayCount("1.2万"))
        assertEquals(1234L, displayCount("1,234"))
        assertEquals(2000L, displayCount("2k"))
    }
    @Test fun `unknown labels are not zero`() {
        assertNull(displayCount("点赞"))
        assertNull(displayCount(""))
        assertNull(displayCount("NaN"))
    }
}
