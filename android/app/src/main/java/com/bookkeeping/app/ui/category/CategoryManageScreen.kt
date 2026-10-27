package com.bookkeeping.app.ui.category

import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.category.DefaultCategories
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.CategoryEntity
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiDialog
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiButtonStyle
import com.bookkeeping.app.ui.components.MiIconButton
import com.bookkeeping.app.ui.components.MiSegmented
import com.bookkeeping.app.ui.components.MiSheet
import com.bookkeeping.app.ui.components.MiTextField
import com.bookkeeping.app.ui.components.MiTopBar
import com.bookkeeping.app.ui.components.miClickable
import com.bookkeeping.app.ui.components.miRowClickable
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.launch

/** 分类管理：新建、编辑（改名/图标/颜色）、删除 */
@Composable
fun CategoryManageScreen(onBack: () -> Unit, initialType: String = "EXPENSE") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).categoryDao() }
    val all by dao.getAllFlow().collectAsState(initial = emptyList())
    var typeIndex by remember { mutableIntStateOf(if (initialType == "INCOME") 1 else 0) }
    val type = if (typeIndex == 1) "INCOME" else "EXPENSE"
    val list = remember(all, type) { all.filter { it.type == type } }

    var editorTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<CategoryEntity, Int>?>(null) }

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().background(Mi.colors.background)) {
        MiTopBar(title = "分类管理", onBack = onBack) {
            MiIconButton(Icons.Default.Add, onClick = { creating = true }, tint = Mi.colors.primary, contentDescription = "新建分类")
        }
        MiSegmented(
            options = listOf("支出分类", "收入分类"),
            selectedIndex = typeIndex,
            onSelect = { typeIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                MiCard(modifier = Modifier.animateContentSize()) {
                    list.forEachIndexed { index, cat ->
                        CategoryRow(
                            category = cat,
                            onEdit = { editorTarget = cat },
                            onDelete = {
                                scope.launch {
                                    deleteTarget = cat to dao.countTransactions(cat.type, cat.name)
                                }
                            }
                        )
                        if (index != list.lastIndex) MiDivider(startIndent = 70.dp)
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                MiButton(
                    text = "新建${if (type == "INCOME") "收入" else "支出"}分类",
                    onClick = { creating = true },
                    icon = Icons.Default.Add,
                    style = MiButtonStyle.Soft,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "删除分类后，该分类下的历史账单会归入「${DefaultCategories.fallbackFor(type)}」，不会丢失。",
                    fontSize = 12.sp,
                    color = Mi.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    if (creating || editorTarget != null) {
        CategoryEditorSheet(
            existing = editorTarget,
            type = editorTarget?.type ?: type,
            onDismiss = { creating = false; editorTarget = null },
            onSave = { draft ->
                scope.launch {
                    try {
                        val old = editorTarget
                        if (old != null) {
                            dao.updateWithRename(old, draft)
                        } else {
                            val order = (dao.maxSortOrder(draft.type) ?: 0) + 1
                            dao.insert(draft.copy(sortOrder = order))
                        }
                        Toast.makeText(context, if (old != null) "已保存" else "已新建分类「${draft.name}」", Toast.LENGTH_SHORT).show()
                    } catch (_: SQLiteConstraintException) {
                        Toast.makeText(context, "已存在同名分类", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    deleteTarget?.let { (cat, count) ->
        MiDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除「${cat.name}」？",
            message = if (count > 0) "该分类下有 $count 笔账单，删除后将归入「${DefaultCategories.fallbackFor(cat.type)}」。" else "删除后可随时重新创建。",
            confirmText = "删除",
            confirmStyle = MiButtonStyle.Danger,
            onConfirm = {
                scope.launch { dao.deleteAndReassign(cat, DefaultCategories.fallbackFor(cat.type)) }
            }
        )
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val v = category.visual()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miRowClickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(v.color), contentAlignment = Alignment.Center) {
            Icon(v.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(category.name, fontSize = 16.sp, color = Mi.colors.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (category.isProtected) {
            Icon(Icons.Default.Lock, contentDescription = "默认分类", tint = Mi.colors.textTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Mi.colors.danger)
                    .miClickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 新建/编辑分类面板：名称 + 图标宫格 + 颜色 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorSheet(
    existing: CategoryEntity?,
    type: String,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "category") }
    var color by remember { mutableStateOf(existing?.colorArgb ?: CategoryIcons.palette[7]) }
    val renameLocked = existing?.isProtected == true

    MiSheet(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新建${if (type == "INCOME") "收入" else "支出"}分类" else "编辑分类"
    ) { dismiss ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
                    Icon(CategoryIcons.icon(iconKey), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                if (renameLocked) {
                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Mi.colors.textPrimary)
                } else {
                    MiTextField(
                        value = name,
                        onValueChange = { if (it.length <= 8) name = it },
                        placeholder = "分类名称（最多 8 字）",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("颜色", fontSize = 13.sp, color = Mi.colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryIcons.palette.forEach { argb ->
                    val selected = argb == color
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(if (selected) Modifier.border(3.dp, Mi.colors.surface, CircleShape) else Modifier)
                            .miClickable { color = argb },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("图标", fontSize = 13.sp, color = Mi.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CategoryIcons.all, key = { it.first }) { (key, icon) ->
                    val selected = key == iconKey
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(color) else Mi.colors.fill)
                            .miClickable { iconKey = key },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = if (selected) Color.White else Mi.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            MiButton(
                text = "保存",
                enabled = name.isNotBlank(),
                onClick = {
                    val draft = (existing ?: CategoryEntity(name = "", type = type, iconKey = iconKey, colorArgb = color))
                        .copy(name = name.trim(), iconKey = iconKey, colorArgb = color)
                    dismiss { onSave(draft) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}
