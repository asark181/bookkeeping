package com.bookkeeping.app.parser

object NotificationParser {

    const val PKG_WECHAT = "com.tencent.mm"
    const val PKG_ALIPAY = "com.eg.android.AlipayGphone"
    const val PKG_UNIONPAY = "com.unionpay"

    // 美团生态
    const val PKG_MEITUAN = "com.sankuai.meituan"
    const val PKG_MEITUAN_TAKEOUT = "com.sankuai.meituan.takeoutnew"

    // 京东生态
    const val PKG_JD = "com.jingdong.app.mall"
    const val PKG_JD_JR = "com.jd.jr"
    const val PKG_JD_LITE = "com.jd.jdlite"

    // 抖音生态
    const val PKG_DOUYIN = "com.ss.android.ugc.aweme"
    const val PKG_DOUYIN_LIVE = "com.ss.android.ugc.live"

    val SUPPORTED_PACKAGES = setOf(
        PKG_WECHAT, PKG_ALIPAY, PKG_UNIONPAY,
        PKG_MEITUAN, PKG_MEITUAN_TAKEOUT,
        PKG_JD, PKG_JD_JR, PKG_JD_LITE,
        PKG_DOUYIN, PKG_DOUYIN_LIVE
    )

    // 营销/降噪黑名单关键词
    private val NOISE_KEYWORDS = listOf(
        "干饭", "领积分", "积分到账", "生活缴费提醒", "能量已生成",
        "优惠券", "邀请好友", "还款日提醒", "活动开启", "中奖",
        "省钱金额已达", "未支付订单"
    )

    // 微信支出规则（消费 + 付款码 + 转账 + 发红包）
    private val WECHAT_EXPENSE_PATTERNS = listOf(
        Regex("向(.+?)转账[￥¥]?([\\d.]+)元?"),
        Regex("转账给(.+?)[￥¥]?([\\d.]+)元?"),
        Regex("向(.+?)付款[￥¥]([\\d.]+)"),
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("已支付[￥¥]([\\d.]+)"),
        Regex("微信支付[：:]\\s*已支付[￥¥]([\\d.]+)"),
        Regex("微信支付.*?已支付[￥¥]([\\d.]+)"),
        Regex("付款码.*?已支付[￥¥]([\\d.]+)"),
        Regex("付款码.*?付款[￥¥]?([\\d.]+)"),
        Regex("已付款[￥¥]([\\d.]+)"),
        Regex("付款成功[￥¥]?([\\d.]+)"),
        Regex("微信转账[：:]\\s*已转账[￥¥]([\\d.]+)"),
        Regex("已转账[￥¥]([\\d.]+)"),
        Regex("微信转账.*?([\\d.]+)元"),
        Regex("微信红包.*?([\\d.]+)元"),
        Regex("发出红包.*?([\\d.]+)元"),
        Regex("包入红包.*?([\\d.]+)元"),
        Regex("转账[￥¥]?([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出")
    )

    // 微信收入规则（退款/收款/红包）
    private val WECHAT_INCOME_PATTERNS = listOf(
        Regex("收款[￥¥]([\\d.]+)"),
        Regex("退款[￥¥]([\\d.]+)"),
        Regex("退款.*?[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入([\\d.]+)元"),
        Regex("红包[￥¥]([\\d.]+)")
    )

    // 支付宝支出规则
    private val ALIPAY_EXPENSE_PATTERNS = listOf(
        Regex("向(.+?)成功付款([\\d.]+)元"),
        Regex("向(.+?)付款([\\d.]+)元"),
        Regex("向(.+?)转账([\\d.]+)元"),
        Regex("成功付款([\\d.]+)元?"),
        Regex("消费([\\d.]+)元"),
        Regex("转账([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("支出([\\d.]+)元"),
        Regex("扣款([\\d.]+)元"),
        Regex("([\\d.]+)元的支出"),
        Regex("[￥¥]([\\d.]+).*支出")
    )

    // 支付宝收入规则
    private val ALIPAY_INCOME_PATTERNS = listOf(
        Regex("退款([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元"),
        Regex("到账([\\d.]+)元"),
        Regex("收款([\\d.]+)元"),
        Regex("转入([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的收入"),
        Regex("红包.*?([\\d.]+)元")
    )

    // 美团支出规则
    private val MEITUAN_EXPENSE_PATTERNS = listOf(
        Regex("使用【.+?】支付([\\d.]+)元?"),
        Regex("已支付[￥¥]?([\\d.]+)元?"),
        Regex("支付成功.*?([\\d.]+)元"),
        Regex("付款成功.*?([\\d.]+)元"),
        Regex("成功支付([\\d.]+)元?"),
        Regex("美团月付.*?([\\d.]+)元"),
        Regex("美团.*?消费([\\d.]+)元"),
        Regex("扣款([\\d.]+)元")
    )

    // 京东支出规则
    private val JD_EXPENSE_PATTERNS = listOf(
        Regex("使用【.+?】支付([\\d.]+)元?"),
        Regex("成功支付([\\d.]+)元?"),
        Regex("付款成功.*?([\\d.]+)元"),
        Regex("已支付[￥¥]?([\\d.]+)元?"),
        Regex("支付成功.*?([\\d.]+)元"),
        Regex("京东白条.*?([\\d.]+)元"),
        Regex("扣款([\\d.]+)元")
    )

    // 抖音支出规则
    private val DOUYIN_EXPENSE_PATTERNS = listOf(
        Regex("抖音支付.*?([\\d.]+)元"),
        Regex("抖音支付.*?[￥¥]([\\d.]+)"),
        Regex("支付[￥¥]([\\d.]+)元?"),
        Regex("已支付[￥¥]([\\d.]+)元?"),
        Regex("付款[￥¥]([\\d.]+)元?"),
        Regex("消费([\\d.]+)元"),
        Regex("订单.*?[￥¥]([\\d.]+)")
    )

    /**
     * 解析通知内容
     */
    fun parse(packageName: String, title: String, content: String): ParsedPayment? {
        val fullText = "$title $content".trim()
        if (fullText.isBlank()) return null

        // 过滤营销与噪声
        if (NOISE_KEYWORDS.any { fullText.contains(it) }) {
            return null
        }

        return when (packageName) {
            PKG_WECHAT -> parseWeChat(title, content, fullText)
            PKG_ALIPAY -> parseAlipay(title, content, fullText)
            PKG_MEITUAN, PKG_MEITUAN_TAKEOUT -> parseGeneralExpense(fullText, MEITUAN_EXPENSE_PATTERNS, "美团支付", "餐饮美食")
            PKG_JD, PKG_JD_JR, PKG_JD_LITE -> parseGeneralExpense(fullText, JD_EXPENSE_PATTERNS, "京东支付", "网络购物")
            PKG_DOUYIN, PKG_DOUYIN_LIVE -> parseGeneralExpense(fullText, DOUYIN_EXPENSE_PATTERNS, "抖音支付", "网络购物")
            PKG_UNIONPAY -> parseUnionPay(fullText)
            else -> null
        }
    }

    private fun parseWeChat(title: String, content: String, fullText: String): ParsedPayment? {
        for (pattern in WECHAT_EXPENSE_PATTERNS) {
            val match = pattern.find(content) ?: pattern.find(fullText)
            if (match != null) {
                val (merchant, amountStr) = if (match.groupValues.size >= 3) {
                    match.groupValues[1].trim() to match.groupValues[2]
                } else {
                    null to match.groupValues[1]
                }
                val amountCents = toCents(amountStr) ?: continue
                return ParsedPayment(
                    amountCents = amountCents,
                    type = "EXPENSE",
                    channel = "微信支付",
                    merchant = cleanMerchant(merchant),
                    confidence = ParseConfidence.HIGH,
                    rawText = fullText
                )
            }
        }

        for (pattern in WECHAT_INCOME_PATTERNS) {
            val match = pattern.find(content) ?: pattern.find(fullText)
            if (match != null) {
                val amountStr = match.groupValues[1]
                val amountCents = toCents(amountStr) ?: continue
                return ParsedPayment(
                    amountCents = amountCents,
                    type = "INCOME",
                    channel = "微信支付",
                    merchant = if (fullText.contains("退款")) "微信退款" else "微信收款",
                    confidence = ParseConfidence.HIGH,
                    rawText = fullText
                )
            }
        }
        return null
    }

    private fun parseAlipay(title: String, content: String, fullText: String): ParsedPayment? {
        for (pattern in ALIPAY_EXPENSE_PATTERNS) {
            val match = pattern.find(content) ?: pattern.find(fullText)
            if (match != null) {
                val (merchant, amountStr) = if (match.groupValues.size >= 3) {
                    match.groupValues[1].trim() to match.groupValues[2]
                } else {
                    null to match.groupValues[1]
                }
                val amountCents = toCents(amountStr) ?: continue
                return ParsedPayment(
                    amountCents = amountCents,
                    type = "EXPENSE",
                    channel = "支付宝",
                    merchant = cleanMerchant(merchant),
                    confidence = ParseConfidence.HIGH,
                    rawText = fullText
                )
            }
        }

        for (pattern in ALIPAY_INCOME_PATTERNS) {
            val match = pattern.find(content) ?: pattern.find(fullText)
            if (match != null) {
                val amountStr = match.groupValues[1]
                val amountCents = toCents(amountStr) ?: continue
                return ParsedPayment(
                    amountCents = amountCents,
                    type = "INCOME",
                    channel = "支付宝",
                    merchant = if (fullText.contains("退款")) "支付宝退款" else "支付宝收款",
                    confidence = ParseConfidence.HIGH,
                    rawText = fullText
                )
            }
        }
        return null
    }

    private fun parseGeneralExpense(
        fullText: String,
        patterns: List<Regex>,
        channelName: String,
        defaultCategory: String
    ): ParsedPayment? {
        for (pattern in patterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                val amountStr = match.groupValues[1]
                val cents = toCents(amountStr) ?: continue
                return ParsedPayment(
                    amountCents = cents,
                    type = "EXPENSE",
                    channel = channelName,
                    merchant = channelName,
                    confidence = ParseConfidence.HIGH,
                    rawText = fullText
                )
            }
        }
        return null
    }

    private fun parseUnionPay(fullText: String): ParsedPayment? {
        val expenseRegex = Regex("消费.*?([\\d.]+)元")
        val match = expenseRegex.find(fullText)
        if (match != null) {
            val cents = toCents(match.groupValues[1]) ?: return null
            return ParsedPayment(
                amountCents = cents,
                type = "EXPENSE",
                channel = "云闪付",
                merchant = "银联消费",
                confidence = ParseConfidence.HIGH,
                rawText = fullText
            )
        }
        return null
    }

    /**
     * 将元转为分（例如 "25.00" -> 2500L, "3.5" -> 350L）
     */
    fun toCents(str: String): Long? {
        val clean = str.replace(",", "").replace("¥", "").replace("￥", "").trim()
        val d = clean.toDoubleOrNull() ?: return null
        return Math.round(d * 100.0)
    }

    private fun cleanMerchant(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        if (s.startsWith("【") && s.endsWith("】")) {
            s = s.substring(1, s.length - 1)
        }
        return s.ifBlank { null }
    }
}
