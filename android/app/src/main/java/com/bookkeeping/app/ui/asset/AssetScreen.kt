package com.bookkeeping.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.ui.detail.formatCents

data class AccountItem(
    val name: String,
    val group: String,
    val icon: ImageVector,
    val color: Color,
    val channelKeyword: String
)

@Composable
fun AssetScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = db.transactionDao()
    val transactions by dao.getAllFlow().collectAsState(initial = emptyList())

    val accounts = remember {
        listOf(
            AccountItem("微信钱包", "网络支付", Icons.Default.Wallet, Color(0xFF10B981), "微信"),
            AccountItem("支付宝", "网络支付", Icons.Default.Payment, Color(0xFF1677FF), "支付宝"),
            AccountItem("建行储蓄卡(0002)", "储蓄卡", Icons.Default.AccountBalance, Color(0xFF0284C7), "储蓄卡"),
            AccountItem("建行信用卡(0001)", "信用卡与消费信贷", Icons.Default.CreditCard, Color(0xFFDC2626), "信用卡"),
            AccountItem("美团月付", "信用卡与消费信贷", Icons.Default.CreditCard, Color(0xFFF59E0B), "美团"),
            AccountItem("京东白条", "信用卡与消费信贷", Icons.Default.CreditCard, Color(0xFFEF4444), "京东")
        )
    }

    // 按账户统计支出与笔数
    val accountStats = remember(transactions) {
        accounts.map { acc ->
            val related = transactions.filter {
                it.channel.contains(acc.channelKeyword) || (it.rawText?.contains(acc.name) == true)
            }
            val expense = related.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
            val income = related.filter { it.type == "INCOME" }.sumOf { it.amountCents }
            Triple(acc, expense, related.size)
        }
    }

    val totalSpent = remember(transactions) {
        transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("资产管理", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("累计消费: ¥${formatCents(totalSpent)}", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 资产总览卡片
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("关联活跃账户", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${accounts.size} 个支付账户",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "流水记账时自动关联扣款渠道，各账户流水自动沉淀",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // 账户分组列表
            val grouped = accountStats.groupBy { it.first.group }
            grouped.forEach { (groupName, list) ->
                item(key = "hdr_$groupName") {
                    Text(
                        text = groupName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                }

                items(list, key = { it.first.name }) { (acc, spent, count) ->
                    AccountRow(acc, spent, count)
                }
            }
        }
    }
}

@Composable
fun AccountRow(
    account: AccountItem,
    spentCents: Long,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(account.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(account.icon, contentDescription = null, tint = account.color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(account.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text("记录 $count 笔流水", fontSize = 11.sp, color = Color.Gray)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("¥${formatCents(spentCents)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text("累计动账", fontSize = 10.sp, color = Color.LightGray)
        }
    }
}
