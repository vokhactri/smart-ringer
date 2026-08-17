package dev.trivk.smartringer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorTest {
    @Test
    fun `hex color accepts hash and stores opaque argb`() {
        assertEquals(0xFF4F5E92.toInt(), parseThemeColor("#4F5E92"))
        assertEquals(0xFFAABBCC.toInt(), parseThemeColor("aabbcc"))
    }

    @Test
    fun `hex color rejects incomplete or invalid input`() {
        assertNull(parseThemeColor("#12345"))
        assertNull(parseThemeColor("#GG0000"))
        assertNull(parseThemeColor("#80112233"))
    }
}
