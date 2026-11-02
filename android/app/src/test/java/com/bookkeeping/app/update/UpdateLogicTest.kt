package com.bookkeeping.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    @Test
    fun parsesPublishedVersionJson() {
        val offer = parseOffer(
            """{"versionCode": 9, "versionName": "1.4.0", "apkUrl": "https://x/download/bookkeeping.apk", "sha256": "ABC", "notes": "修复"}""",
            "https://x"
        )
        assertEquals(9, offer.versionCode)
        assertEquals("1.4.0", offer.versionName)
        assertEquals("abc", offer.sha256)
        assertEquals("修复", offer.notes)
    }

    @Test
    fun fallsBackToFixedApkPath() {
        val offer = parseOffer("""{"versionCode": 3}""", "https://x/")
        assertEquals("https://x/download/bookkeeping.apk", offer.apkUrl)
        assertEquals("3", offer.versionName)
    }

    @Test
    fun checksHashAndApkHeader() {
        assertTrue(sha256Matches("ABCDEF", "abcdef"))
        assertTrue(sha256Matches("whatever", ""))
        assertFalse(sha256Matches("abc", "abd"))
        assertTrue(looksLikeApk(byteArrayOf(0x50, 0x4B, 3, 4)))
        assertFalse(looksLikeApk("<htm".toByteArray()))
    }
}
