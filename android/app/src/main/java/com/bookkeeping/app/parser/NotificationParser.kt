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

    /**
     * 微信只解析服务号通知。好友/群聊通知的标题是联系人或群名，内容由用户输入，不能作为记账依据。
     * 部分 ROM 会在标题后追加「(2条)」之类的计数。
     */
    private val WECHAT_SERVICE_TITLE = Regex(
        "^(微信支付|微信转账|微信红包|服务通知|微信收款助手|微信收款商业版|微信支付助手)\\s*([(（]\\d+条[)）])?$"
    )

    /** 微信多条消息合并时内容前缀，例如「[2条]微信支付: 」 */
    private val WECHAT_CONTENT_PREFIX = Regex("^\\[\\d+条]\\s*")

    /** 明确表示「钱是对方收的」的文案：虽然包含「已收款」等字样，但对自己而言是支出 */
    private val EXPENSE_OVERRIDE_KEYWORDS = listOf(
        "对方已收款", "对方已收钱", "对方已确认收款", "已被接收", "已被领取", "待对方确认收款", "待对方确认收钱",
        "收到你的付款", "收到您的付款"
    )

    /** 收入方向词：先判方向再套金额规则，避免「张三向你转账」被支出规则「向(.+?)转账」吃掉 */
    private val INCOME_DIRECTION = Regex(
        "向你|收到|到账|已收款|已收钱|已存入|退款|退回|转入|收入|(?<!向)收款(?!方|码)"
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
        Regex("一笔([\\d.]+)元的支出"),
        Regex("[￥¥]\\s*([\\d.]+)")
    )

    /** 通用收入金额规则（方向已判定为收入后使用） */
    private val INCOME_AMOUNT_PATTERNS = listOf(
        Regex("[￥¥]\\s*([\\d,]*\\d(?:\\.\\d+)?)"),
        Regex("([\\d,]*\\d(?:\\.\\d+)?)\\s*元")
    )

    /** 收入对象：「张三向你转账」「来自张三的红包」 */
    private val INCOME_COUNTERPARTY_PATTERNS = listOf(
        Regex("([^\\s，,：:]+?)向你(?:转账|付款)"),
        Regex("来自(.+?)的(?:转账|红包)"),
        Regex("收到(.+?)的(?:转账|红包)")
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
            PKG_WECHAT -> parseWeChat(title, content)
            PKG_ALIPAY -> parseAlipay(content, fullText)
            PKG_MEITUAN, PKG_MEITUAN_TAKEOUT -> parseGeneralExpense(fullText, MEITUAN_EXPENSE_PATTERNS, "美团支付")
            PKG_JD, PKG_JD_JR, PKG_JD_LITE -> parseGeneralExpense(fullText, JD_EXPENSE_PATTERNS, "京东支付")
            PKG_DOUYIN, PKG_DOUYIN_LIVE -> parseGeneralExpense(fullText, DOUYIN_EXPENSE_PATTERNS, "抖音支付")
            PKG_UNIONPAY -> parseUnionPay(fullText)
            else -> null
        }
    }

    /** 是否为微信支付相关服务号通知（而不是好友/群聊） */
    fun isWeChatServiceTitle(title: String): Boolean = WECHAT_SERVICE_TITLE.matches(title.trim())

    /** 判断交易方向：先排除「对方已收款」类支出文案，再看收入方向词，其余视为支出 */
    fun detectDirection(text: String): String {
        if (EXPENSE_OVERRIDE_KEYWORDS.any { text.contains(it) }) return "EXPENSE"
        return if (INCOME_DIRECTION.containsMatchIn(text)) "INCOME" else "EXPENSE"
    }

    /**
     * 未解析出金额，但看起来与支付有关的通知（用于排查日志）。
     * 微信只认服务号通知，以及聊天通知里系统生成的「[转账]」「[微信红包]」占位。
     */
    fun isSuspectedPayment(packageName: String, title: String, content: String): Boolean {
        if (packageName == PKG_WECHAT) {
            if (isWeChatServiceTitle(title)) return true
            val c = content.replace(WECHAT_CONTENT_PREFIX, "")
            return c.contains("[转账]") || c.contains("[微信红包]") || c.contains("[红包]")
        }
        val text = "$title $content"
        return SUSPECT_KEYWORDS.any { text.contains(it) } && Regex("\\d").containsMatchIn(text)
    }

    private val SUSPECT_KEYWORDS = listOf("支付", "付款", "扣款", "消费", "到账", "退款", "转账", "收款", "支出", "收入")

    private fun parseWeChat(title: String, rawContent: String): ParsedPayment? {
        // 只解析服务号通知，忽略好友聊天中的「我转账100元给你」之类文本
        if (!isWeChatServiceTitle(title)) return null
        val content = rawContent.replace(WECHAT_CONTENT_PREFIX, "")
        val fullText = "$title $content".trim()

        if (detectDirection(content) == "INCOME") {
            val cents = findAmount(content, INCOME_AMOUNT_PATTERNS) ?: return null
            val counterparty = INCOME_COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { it.find(content)?.groupValues?.get(1) }
            val merchant = cleanMerchant(counterparty) ?: when {
                content.contains("退款") -> "微信退款"
                content.contains("红包") -> "微信红包"
                else -> "微信收款"
            }
            return ParsedPayment(
                amountCents = cents,
                type = "INCOME",
                channel = "微信支付",
                merchant = merchant,
                confidence = ParseConfidence.HIGH,
                rawText = fullText
            )
        }

        for (pattern in WECHAT_EXPENSE_PATTERNS) {
            val match = pattern.find(content) ?: continue
            val (merchant, amountStr) = if (match.groupValues.size >= 3) {
                match.groupValues[1].trim() to match.groupValues[2]
            } else {
                null to match.groupValues[1]
            }
            val amountCents = toCents(amountStr) ?: continue
            if (amountCents <= 0) continue
            return ParsedPayment(
                amountCents = amountCents,
                type = "EXPENSE",
                channel = "微信支付",
                merchant = cleanMerchant(merchant),
                confidence = ParseConfidence.HIGH,
                rawText = fullText
            )
        }
        return null
    }

    private fun parseAlipay(content: String, fullText: String): ParsedPayment? {
        if (detectDirection(fullText) == "INCOME") {
            val cents = findAmount(content, INCOME_AMOUNT_PATTERNS)
                ?: findAmount(fullText, INCOME_AMOUNT_PATTERNS)
                ?: return null
            val counterparty = INCOME_COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { it.find(content)?.groupValues?.get(1) }
            val merchant = cleanMerchant(counterparty) ?: when {
                fullText.contains("退款") -> "支付宝退款"
                fullText.contains("红包") -> "支付宝红包"
                else -> "支付宝收款"
            }
            return ParsedPayment(
                amountCents = cents,
                type = "INCOME",
                channel = "支付宝",
                merchant = merchant,
                confidence = ParseConfidence.HIGH,
                rawText = fullText
            )
        }

        for (pattern in ALIPAY_EXPENSE_PATTERNS) {
            val match = pattern.find(content) ?: pattern.find(fullText) ?: continue
            val (merchant, amountStr) = if (match.groupValues.size >= 3) {
                match.groupValues[1].trim() to match.groupValues[2]
            } else {
                null to match.groupValues[1]
            }
            val amountCents = toCents(amountStr) ?: continue
            if (amountCents <= 0) continue
            return ParsedPayment(
                amountCents = amountCents,
                type = "EXPENSE",
                channel = "支付宝",
                merchant = cleanMerchant(merchant),
                confidence = ParseConfidence.HIGH,
                rawText = fullText
            )
        }
        return null
    }

    private fun parseGeneralExpense(
        fullText: String,
        patterns: List<Regex>,
        channelName: String
    ): ParsedPayment? {
        // 美团/京东/抖音的退款通知按收入处理，不能被「订单...¥」等支出规则吃掉
        if (fullText.contains("退款") && !EXPENSE_OVERRIDE_KEYWORDS.any { fullText.contains(it) }) {
            val cents = findAmount(fullText, INCOME_AMOUNT_PATTERNS) ?: return null
            return ParsedPayment(
                amountCents = cents,
                type = "INCOME",
                channel = channelName,
                merchant = "${channelName}退款",
                confidence = ParseConfidence.HIGH,
                rawText = fullText
            )
        }
        for (pattern in patterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                val amountStr = match.groupValues[1]
                val cents = toCents(amountStr) ?: continue
                if (cents <= 0) continue
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

    private fun findAmount(text: String, patterns: List<Regex>): Long? {
        for (p in patterns) {
            for (m in p.findAll(text)) {
                val cents = toCents(m.groupValues[1]) ?: continue
                if (cents > 0) return cents
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
