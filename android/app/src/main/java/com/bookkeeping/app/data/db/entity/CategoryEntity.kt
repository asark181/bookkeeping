package com.bookkeeping.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户可自定义的收支分类。交易表以分类名称关联（兼容自动记账与账单导入写入的名称）。
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["type", "name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** EXPENSE / INCOME */
    val type: String,
    /** 图标键，见 ui/category/CategoryIcons */
    val iconKey: String,
    /** ARGB 颜色值 */
    val colorArgb: Long,
    val sortOrder: Int = 0,
    /** 兜底分类（其他支出/其他收入）不可删除 */
    val isProtected: Boolean = false
)
