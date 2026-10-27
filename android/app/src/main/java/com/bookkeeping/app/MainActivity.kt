package com.bookkeeping.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.parser.CsvStatementImporter
import com.bookkeeping.app.ui.main.MainScreen
import com.bookkeeping.app.ui.theme.BookkeepingTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            BookkeepingTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri: Uri? = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val text: String? = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (uri != null && (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND)) {
            lifecycleScope.launch {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val db = AppDatabase.getInstance(applicationContext)
                        val summary = CsvStatementImporter.importFromStream(stream, db.transactionDao())
                        if (summary.errorMessage == null) {
                            Toast.makeText(
                                applicationContext,
                                "成功导入 ${summary.totalRows} 笔流水 (新增 ${summary.newInserted} 笔，合并去重 ${summary.mergedExisting} 笔)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(applicationContext, summary.errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (!text.isNullOrBlank() && action == Intent.ACTION_SEND) {
            lifecycleScope.launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val summary = CsvStatementImporter.importFromText(text, db.transactionDao())
                    if (summary.errorMessage == null) {
                        Toast.makeText(
                            applicationContext,
                            "成功导入 ${summary.totalRows} 笔流水 (新增 ${summary.newInserted} 笔，合并去重 ${summary.mergedExisting} 笔)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
