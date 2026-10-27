package com.bookkeeping.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bookkeeping.app.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM categories WHERE type = :type")
    suspend fun maxSortOrder(type: String): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isProtected = 0")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE isDeleted = 0 AND type = :type AND category = :name")
    suspend fun countTransactions(type: String, name: String): Int

    @Query("UPDATE transactions SET category = :newName, updatedAt = :now WHERE type = :type AND category = :oldName")
    suspend fun renameInTransactions(type: String, oldName: String, newName: String, now: Long = System.currentTimeMillis())

    /** 删除分类，并把该分类下的账单归入兜底分类 */
    @Transaction
    suspend fun deleteAndReassign(category: CategoryEntity, fallbackName: String) {
        renameInTransactions(category.type, category.name, fallbackName)
        deleteById(category.id)
    }

    /** 重命名分类，同步更新历史账单 */
    @Transaction
    suspend fun updateWithRename(old: CategoryEntity, updated: CategoryEntity) {
        if (old.name != updated.name) {
            renameInTransactions(old.type, old.name, updated.name)
        }
        update(updated)
    }
}
