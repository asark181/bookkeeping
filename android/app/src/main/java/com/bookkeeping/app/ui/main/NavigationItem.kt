package com.bookkeeping.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DETAIL("明细", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    CHART("统计", Icons.Outlined.DonutLarge, Icons.Filled.DonutLarge),
    ASSET("资产", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    PROFILE("我的", Icons.Outlined.Person, Icons.Filled.Person)
}
