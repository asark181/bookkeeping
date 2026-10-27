package com.bookkeeping.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.common.monthRange
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiLargeTitle
import com.bookkeeping.app.ui.components.MiSectionTitle
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import java.util.Calendar

private data class AccountDef(
    val name: String,
    val group: String,
    val icon: ImageVector,
    val color: Color,
    val keywords: List<String>
)

private val ACCOUNTS = listOf(
    AccountDef("微信钱包", "网络支付", Icons.Default.Wallet, Color(0xFF16B47A), listOf("微信")),
    AccountDef("支付宝", "网络支付", Icons.Default.Payment, Color(0xFF1677FF), listOf("支付宝")),
    AccountDef("云闪付", "网络支付", Icons.Default.Payment, Color(0xFFE6312E), listOf("云闪付", "银联")),
    AccountDef("银行卡", "银行账户", Icons.Default.AccountBalance, Color(0xFF0284C7), listOf("银行卡", "储蓄卡")),
    AccountDef("信用卡", "信用与消费信贷", Icons.Default.CreditCard, Color(0xFFDC2626), listOf("信用卡")),
    AccountDef("美团月付", "信用与消费信贷", Icons.Default.Storefront, Color(0xFFF5B400), listOf("美团")),
    AccountDef("京东白条", "信用与消费信贷", Icons.Default.ShoppingBag, Color(0xFFE1251B), listOf("京东")),
    AccountDef("抖音月付", "信用与消费信贷", Icons.Default.ShoppingBag, Color(0xFF111111), listOf("抖音")),
    AccountDef("现金", "现金", Icons.Default.LocalAtm, Color(0xFFFF8A3D), listOf("现金"))
)

@Composable
fun AssetScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).transactionDao() }
    val all by dao.getAllFlow().collectAsState(initial = emptyList())
    val now = remember { Calendar.getInstance() }
    val (mStart, mEnd) = remember { monthRange(now.get(Calendar.YEAR), now.get(Calendar.MONTH)) }

    data class Stat(val def: AccountDef, val monthOut: Long, val monthIn: Long, val count: Int)

    val stats = remember(all) {
        ACCOUNTS.map { def ->
            val related = all.filter { r -> def.keywords.any { r.channel.contains(it) } }
            val month = related.filter { it.timestamp in mStart..mEnd }
            Stat(
                def,
                month.filter { it.type == "EXPENSE" }.sumOf { it.amountCents },
                month.filter { it.type == "INCOME" }.sumOf { it.amountCents },
                related.size
            )
        }
    }
    val monthOut = remember(all) { all.filter { it.type == "EXPENSE" && it.timestamp in mStart..mEnd }.sumOf { it.amountCents } }
    val monthIn = remember(all) { all.filter { it.type == "INCOME" && it.timestamp in mStart..mEnd }.sumOf { it.amountCents } }
    val totalOut = remember(all) { all.filter { it.type == "EXPENSE" }.sumOf { it.amountCents } }
    val totalIn = remember(all) { all.filter { it.type == "INCOME" }.sumOf { it.amountCents } }
    val c = Mi.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item { MiLargeTitle("资产", subtitle = "按支付渠道自动归集的资金流水") }

        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2C3E66), Color(0xFF4A6491))))
                    .padding(20.dp)
            ) {
                Text("累计净流入", fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(4.dp))
                Text(formatCents(totalIn - totalOut), style = AmountStyle, fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("本月流出", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(formatCents(monthOut), style = AmountStyle, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("本月流入", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(formatCents(monthIn), style = AmountStyle, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("活跃账户", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text("${stats.count { it.count > 0 }} 个", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        stats.groupBy { it.def.group }.forEach { (group, list) ->
            item(key = group) {
                MiSectionTitle(group)
                MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    list.forEachIndexed { i, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(s.def.color), contentAlignment = Alignment.Center) {
                                Icon(s.def.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.def.name, fontSize = 16.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(if (s.count > 0) "累计 ${s.count} 笔" else "暂无流水", fontSize = 12.sp, color = c.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (s.monthOut > 0) "-" + formatCents(s.monthOut) else "0.00", style = AmountStyle, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                                if (s.monthIn > 0) {
                                    Text("+" + formatCents(s.monthIn), style = AmountStyle, fontSize = 12.sp, color = c.income)
                                } else {
                                    Text("本月", fontSize = 11.sp, color = c.textTertiary)
                                }
                            }
                        }
                        if (i != list.lastIndex) MiDivider(startIndent = 68.dp)
                    }
                }
            }
        }
    }
}
