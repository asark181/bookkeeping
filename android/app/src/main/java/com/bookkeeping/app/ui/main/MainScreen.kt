package com.bookkeeping.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.attachment.AttachmentStore
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.ui.asset.AssetScreen
import com.bookkeeping.app.ui.category.CategoryManageScreen
import com.bookkeeping.app.ui.category.LocalCategoryMap
import com.bookkeeping.app.ui.category.categoryKey
import com.bookkeeping.app.ui.chart.ChartScreen
import com.bookkeeping.app.ui.common.SharedImport
import com.bookkeeping.app.ui.common.SharedImportPrompt
import com.bookkeeping.app.ui.common.rememberBillImporter
import com.bookkeeping.app.ui.components.LocalOverlayHost
import com.bookkeeping.app.ui.components.OverlayHost
import com.bookkeeping.app.ui.components.OverlayHostView
import com.bookkeeping.app.ui.components.miClickable
import com.bookkeeping.app.ui.detail.DetailScreen
import com.bookkeeping.app.ui.profile.ProfileScreen
import com.bookkeeping.app.ui.profile.UpdateSheet
import com.bookkeeping.app.BookkeepingApp
import com.bookkeeping.app.ui.record.RecordEditorScreen
import com.bookkeeping.app.ui.record.SearchScreen
import com.bookkeeping.app.ui.record.TransactionDetailSheet
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.launch

/** 全屏二级页面 */
private sealed interface Page {
    data class Editor(val record: TransactionRecord?) : Page
    data object Categories : Page
    data object Search : Page
}

/** 页面栈条目：自带进退场状态，退场动画结束后出栈 */
private class PageEntry(val page: Page) {
    val state = MutableTransitionState(false).apply { targetState = true }
}

@Composable
fun MainScreen(sharedImport: SharedImport? = null, onSharedImportHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val db = remember { AppDatabase.getInstance(context) }
    val categories by db.categoryDao().getAllFlow().collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { categories.associateBy { categoryKey(it.type, it.name) } }

    var currentTab by rememberSaveable { mutableStateOf(NavigationItem.DETAIL) }
    val stack = remember { mutableStateListOf<PageEntry>() }
    var openedRecord by remember { mutableStateOf<TransactionRecord?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val updates = remember { (context.applicationContext as BookkeepingApp).updates }
    val updateUi by updates.ui.collectAsState()

    fun push(page: Page) = stack.add(PageEntry(page))
    fun pop() {
        stack.lastOrNull { it.state.targetState }?.state?.targetState = false
    }

    val openRecord: (TransactionRecord) -> Unit = { openedRecord = it }

    val overlayHost = remember { OverlayHost() }
    CompositionLocalProvider(LocalCategoryMap provides categoryMap, LocalOverlayHost provides overlayHost) {
        Box(modifier = Modifier.fillMaxSize().background(Mi.colors.background)) {
            Crossfade(targetState = currentTab, animationSpec = tween(180), label = "tab") { tab ->
                when (tab) {
                    NavigationItem.DETAIL -> DetailScreen(
                        onAdd = { push(Page.Editor(null)) },
                        onSearch = { push(Page.Search) },
                        onManageCategories = { push(Page.Categories) },
                        onOpenRecord = openRecord,
                        onSelectionModeChange = { selectionMode = it }
                    )
                    NavigationItem.CHART -> ChartScreen(onOpenRecord = openRecord)
                    NavigationItem.ASSET -> AssetScreen()
                    NavigationItem.PROFILE -> ProfileScreen(onManageCategories = { push(Page.Categories) })
                }
            }

            // 状态栏遮罩：滚动内容不与时间、电量图标重叠
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Mi.colors.background.copy(alpha = 0.96f))
            )

            AnimatedVisibility(
                visible = !selectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomBar(
                    current = currentTab,
                    profileBadge = updateUi != null,
                    onSelect = { currentTab = it },
                    onAdd = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        push(Page.Editor(null))
                    }
                )
            }

            stack.forEach { entry ->
                key(entry) {
                    val isEditor = entry.page is Page.Editor
                    AnimatedVisibility(
                        visibleState = entry.state,
                        enter = if (isEditor) slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } + fadeIn()
                        else slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it },
                        exit = if (isEditor) slideOutVertically(tween(240)) { it } + fadeOut(tween(240))
                        else slideOutHorizontally(tween(240)) { it }
                    ) {
                        Box(Modifier.fillMaxSize().background(Mi.colors.background)) {
                            when (val page = entry.page) {
                                is Page.Editor -> RecordEditorScreen(
                                    editing = page.record,
                                    onClose = ::pop,
                                    onManageCategories = { push(Page.Categories) }
                                )
                                Page.Categories -> CategoryManageScreen(onBack = ::pop)
                                Page.Search -> SearchScreen(onBack = ::pop, onOpenRecord = openRecord)
                            }
                        }
                    }
                    LaunchedEffect(entry.state.isIdle, entry.state.currentState) {
                        if (entry.state.isIdle && !entry.state.currentState && !entry.state.targetState) stack.remove(entry)
                    }
                }
            }
        }

        openedRecord?.let { record ->
            TransactionDetailSheet(
                record = record,
                onDismiss = { openedRecord = null },
                onEdit = {
                    openedRecord = null
                    push(Page.Editor(record))
                },
                onDelete = {
                    openedRecord = null
                    scope.launch {
                        db.transactionDao().markDeleted(record.id)
                        AttachmentStore.delete(context, record.imageList)
                    }
                }
            )
        }

        updateUi?.takeIf { it.sheet }?.let { UpdateSheet(it, updates) }

        val shareImporter = rememberBillImporter()
        sharedImport?.let { SharedImportPrompt(it, shareImporter, onSharedImportHandled) }

        OverlayHostView(overlayHost)
    }
}

/** 底部导航：中间悬浮「记一笔」按钮 */
@Composable
private fun BottomBar(
    current: NavigationItem,
    profileBadge: Boolean,
    onSelect: (NavigationItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Mi.colors
    val items = NavigationItem.entries
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(c.surface.copy(alpha = 0.97f))
        ) {
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(c.divider))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.take(2).forEach { TabItem(it, it == current, Modifier.weight(1f)) { onSelect(it) } }
                Spacer(Modifier.weight(1f))
                items.drop(2).forEach {
                    TabItem(it, it == current, Modifier.weight(1f), badge = profileBadge && it == NavigationItem.PROFILE) { onSelect(it) }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(58.dp)
                .shadow(12.dp, CircleShape, ambientColor = c.primary, spotColor = c.primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.heroEnd, c.primary)))
                .miClickable(pressedScale = 0.9f, onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "记一笔", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun TabItem(item: NavigationItem, selected: Boolean, modifier: Modifier, badge: Boolean = false, onClick: () -> Unit) {
    val c = Mi.colors
    Column(
        modifier = modifier.miClickable(pressedScale = 0.9f, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = if (selected) c.primary else c.textSecondary,
                modifier = Modifier.size(25.dp)
            )
            if (badge) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(8.dp).clip(CircleShape).background(c.danger))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            fontSize = 11.sp,
            color = if (selected) c.primary else c.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
