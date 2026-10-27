package com.bookkeeping.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_notifications")
data class CapturedNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val title: String,
    val content: String,
    val parsedStatus: String, // "PARSED_EXPENSE", "PARSED_INCOME", "UNMATCHED", "NOISE"
    val parsedAmountCents: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
