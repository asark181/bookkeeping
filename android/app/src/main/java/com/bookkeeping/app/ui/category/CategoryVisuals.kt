package com.bookkeeping.app.ui.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bookkeeping.app.data.db.entity.CategoryEntity

/** 分类图标库：键值持久化到数据库，顺序即图标选择器的展示顺序 */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "restaurant" to Icons.Filled.Restaurant,
        "coffee" to Icons.Filled.LocalCafe,
        "bakery" to Icons.Filled.BakeryDining,
        "drink" to Icons.Filled.LocalBar,
        "icecream" to Icons.Filled.Icecream,
        "fastfood" to Icons.Filled.Fastfood,
        "cart" to Icons.Filled.ShoppingCart,
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "store" to Icons.Filled.Storefront,
        "car" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "subway" to Icons.Filled.Subway,
        "taxi" to Icons.Filled.LocalTaxi,
        "gas" to Icons.Filled.LocalGasStation,
        "parking" to Icons.Filled.LocalParking,
        "flight" to Icons.Filled.Flight,
        "train" to Icons.Filled.Train,
        "bike" to Icons.Filled.PedalBike,
        "home" to Icons.Filled.Home,
        "bolt" to Icons.Filled.Bolt,
        "water" to Icons.Filled.WaterDrop,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.PhoneAndroid,
        "devices" to Icons.Filled.Devices,
        "movie" to Icons.Filled.Movie,
        "game" to Icons.Filled.SportsEsports,
        "music" to Icons.Filled.MusicNote,
        "fitness" to Icons.Filled.FitnessCenter,
        "sports" to Icons.Filled.SportsBasketball,
        "book" to Icons.Filled.MenuBook,
        "school" to Icons.Filled.School,
        "medical" to Icons.Filled.MedicalServices,
        "pharmacy" to Icons.Filled.LocalPharmacy,
        "spa" to Icons.Filled.Spa,
        "checkroom" to Icons.Filled.Checkroom,
        "face" to Icons.Filled.Face,
        "child" to Icons.Filled.ChildCare,
        "pets" to Icons.Filled.Pets,
        "gift" to Icons.Filled.CardGiftcard,
        "red_packet" to Icons.Filled.Redeem,
        "celebration" to Icons.Filled.Celebration,
        "favorite" to Icons.Filled.Favorite,
        "people" to Icons.Filled.People,
        "credit_card" to Icons.Filled.CreditCard,
        "payments" to Icons.Filled.Payments,
        "savings" to Icons.Filled.Savings,
        "trending_up" to Icons.Filled.TrendingUp,
        "trophy" to Icons.Filled.EmojiEvents,
        "work" to Icons.Filled.Work,
        "undo" to Icons.Filled.Undo,
        "swap" to Icons.Filled.SwapHoriz,
        "build" to Icons.Filled.Build,
        "beach" to Icons.Filled.BeachAccess,
        "hotel" to Icons.Filled.Hotel,
        "smoke" to Icons.Filled.SmokingRooms,
        "local_florist" to Icons.Filled.LocalFlorist,
        "category" to Icons.Filled.Category
    )

    private val map = all.toMap()

    fun icon(key: String): ImageVector = map[key] ?: Icons.Filled.Category

    /** 分类配色：HyperOS 明快色板 */
    val palette: List<Long> = listOf(
        0xFFFF8A3D, 0xFFF5B400, 0xFF2EC27E, 0xFF16B47A, 0xFF22B5A4, 0xFF00B8D9,
        0xFF26A9E0, 0xFF3482FF, 0xFF5B6CFF, 0xFFA66BFF, 0xFFE86FD6, 0xFFFF6FA8,
        0xFFFF5A5F, 0xFF8D6E63, 0xFF607D8B, 0xFF8E99A8
    )
}

@Immutable
data class CategoryVisual(val icon: ImageVector, val color: Color)

/** 当前全部分类，键为 "TYPE|名称" */
val LocalCategoryMap = staticCompositionLocalOf<Map<String, CategoryEntity>> { emptyMap() }

fun categoryKey(type: String, name: String) = "$type|$name"

fun CategoryEntity.visual() = CategoryVisual(CategoryIcons.icon(iconKey), Color(colorArgb))

/**
 * 查找分类外观：优先用户分类，其次同名的另一收支类型分类，最后按名称关键词推断。
 * 已被删除的分类或自动记账写入的新名称也能有合理图标。
 */
@Composable
fun categoryVisual(name: String, type: String): CategoryVisual {
    val map = LocalCategoryMap.current
    val normalizedType = if (type == "INCOME") "INCOME" else "EXPENSE"
    map[categoryKey(normalizedType, name)]?.let { return it.visual() }
    map.values.firstOrNull { it.name == name }?.let { return it.visual() }
    return guessVisual(name, normalizedType)
}

private fun guessVisual(name: String, type: String): CategoryVisual {
    fun v(key: String, color: Long) = CategoryVisual(CategoryIcons.icon(key), Color(color))
    return when {
        type == "INCOME" -> v("savings", 0xFF16B47A)
        name.contains("餐") || name.contains("食") -> v("restaurant", 0xFFFF8A3D)
        name.contains("交通") || name.contains("出行") -> v("car", 0xFF3482FF)
        name.contains("购物") -> v("shopping_bag", 0xFFA66BFF)
        name.contains("红包") || name.contains("转账") -> v("red_packet", 0xFFFF5A5F)
        name.contains("缴费") -> v("bolt", 0xFFF5B400)
        name.contains("娱乐") -> v("movie", 0xFF00B8D9)
        name.contains("医") -> v("medical", 0xFF2EC27E)
        else -> v("category", 0xFF8E99A8)
    }
}
