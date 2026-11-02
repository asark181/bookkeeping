package com.bookkeeping.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bookkeeping.app.data.category.DefaultCategories
import com.bookkeeping.app.data.db.dao.CategoryDao
import com.bookkeeping.app.data.db.dao.TransactionDao
import com.bookkeeping.app.data.db.entity.CapturedNotification
import com.bookkeeping.app.data.db.entity.CategoryEntity
import com.bookkeeping.app.data.db.entity.TransactionRecord

@Database(
    entities = [
        TransactionRecord::class,
        CapturedNotification::class,
        CategoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    /** 分类表为空时写入默认分类（新装、升级均适用，INSERT OR IGNORE 幂等） */
    suspend fun ensureDefaultCategories() {
        val dao = categoryDao()
        if (dao.count() == 0) {
            dao.insertAll(DefaultCategories.all())
        }
    }

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v3 → v4：交易新增备注与图片字段，新增分类表。保留全部历史账单。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN images TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `isProtected` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_type_name` ON `categories` (`type`, `name`)")
            }
        }

        /** v4 → v5：交易新增跨设备唯一 uid（云同步用），为已有账单生成随机 uid */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE transactions SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_uid` ON `transactions` (`uid`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bookkeeping.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
