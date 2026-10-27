package com.bookkeeping.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationItem(val label: String, val icon: ImageVector) {
    DETAIL("明细", Icons.Default.ReceiptLong),
    CHART("图表", Icons.Default.InsertChart),
    ASSET("资产", Icons.Default.AccountBalanceWallet),
    PROFILE("我的", Icons.Default.Person)
}
