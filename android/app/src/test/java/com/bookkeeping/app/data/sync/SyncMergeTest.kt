package com.bookkeeping.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun tx(uid: String, updatedAt: Long, amount: Long = 100, deleted: Boolean = false, ext: String? = null) =
        SyncTx(uid = uid, amountCents = amount, type = "EXPENSE", channel = "微信支付", category = "餐饮美食",
            timestamp = 1, externalId = ext, updatedAt = updatedAt, isDeleted = deleted)

    @Test
    fun newerSideWinsAndLocalOnlyIsUploaded() {
        val local = listOf(tx("a", 10, amount = 1), tx("b", 30, amount = 2), tx("local-only", 5))
        val remote = listOf(tx("a", 20, amount = 9), tx("b", 25, amount = 8), tx("remote-only", 7))
        val r = mergeTransactions(local, remote)

        assertEquals(setOf("a", "remote-only"), r.toApply.map { it.uid }.toSet())
        val merged = r.merged.associateBy { it.uid }
        assertEquals(9L, merged["a"]!!.amountCents)
        assertEquals(2L, merged["b"]!!.amountCents)
        assertTrue("local-only" in merged)
        assertTrue(r.needsUpload)
    }

    @Test
    fun deletionsPropagateAsTombstones() {
        val r = mergeTransactions(listOf(tx("a", 10)), listOf(tx("a", 20, deleted = true)))
        assertTrue(r.toApply.single().isDeleted)
        assertFalse(r.needsUpload)
    }

    @Test
    fun identicalDataNeedsNoUpload() {
        val same = listOf(tx("a", 10), tx("b", 11))
        val r = mergeTransactions(same, same)
        assertTrue(r.toApply.isEmpty())
        assertFalse(r.needsUpload)
    }

    @Test
    fun sameOfficialBillImportedOnTwoDevicesIsNotDuplicated() {
        // 本机已导入单号 X；云端是另一台设备导入的同一笔（不同 uid）
        val r = mergeTransactions(listOf(tx("mine", 10, ext = "X")), listOf(tx("theirs", 12, ext = "X")))
        assertTrue(r.toApply.isEmpty())
        // 第三台设备：云端两条孪生记录只写入一条
        val third = mergeTransactions(emptyList(), listOf(tx("mine", 10, ext = "X"), tx("theirs", 12, ext = "X")))
        assertEquals(1, third.toApply.size)
    }
}
