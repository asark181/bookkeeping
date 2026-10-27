package com.bookkeeping.app.parser

import com.bookkeeping.app.data.db.dao.TransactionDao
import com.bookkeeping.app.data.db.entity.TransactionRecord
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class ImportSummary(
    val totalRows: Int = 0,
    val newInserted: Int = 0,
    val mergedExisting: Int = 0,
    val skipped: Int = 0,
    val errorMessage: String? = null
)

data class StatementRow(
    val timestamp: Long,
    val type: String, // EXPENSE / INCOME
    val amountCents: Long,
    val channel: String,
    val merchant: String?,
    val category: String,
    val externalId: String?,
    val rawText: String
)

object CsvStatementImporter {

    /**
     * 将 CSV 这一行拆分成字段，处理双引号包含逗号以及首尾空格、制表符
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    // 转义双引号 "" -> "
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(cleanCell(sb.toString()))
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(cleanCell(sb.toString()))
        return result
    }

    private fun cleanCell(cell: String): String {
        var s = cell.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1).trim()
        }
        // 去除 Excel 防科学计数法的先导制表符与斜杠占位符
        s = s.removePrefix("\t").trim()
        if (s == "/" || s == "-") {
            return ""
        }
        return s
    }

    /**
     * 自动检测是否为 ZIP 压缩包（微信常导出 ZIP 压缩包）
     */
    fun isZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && // 'P'
                bytes[1] == 0x4B.toByte() && // 'K'
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
    }

    /**
     * 判断字节流是否为 OpenXML (.xlsx) 电子表格
     */
    fun isOpenXmlSpreadsheet(bytes: ByteArray): Boolean {
        if (!isZipArchive(bytes)) return false
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var hasWorkbookOrWorksheet = false
            while (entry != null) {
                val name = entry.name
                if (name.equals("xl/workbook.xml", ignoreCase = true) ||
                    name.startsWith("xl/worksheets/", ignoreCase = true) ||
                    name.equals("[Content_Types].xml", ignoreCase = true)
                ) {
                    hasWorkbookOrWorksheet = true
                    break
                }
                entry = zis.nextEntry
            }
            hasWorkbookOrWorksheet
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 将 Excel 列名（如 A, B, K, AA）转换为 0-based 列索引
     */
    fun columnLetterToIndex(col: String): Int {
        var result = 0
        for (ch in col.uppercase(Locale.ROOT)) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return if (result > 0) result - 1 else 0
    }

    /**
     * 将 Excel 序列日历浮点数（如 46322.63872685185）转换为标准日期时间字符串
     */
    fun formatExcelSerialDate(serial: Double): String {
        val days = serial.toLong()
        val fraction = serial - days
        val totalSeconds = Math.round(fraction * 86400.0).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(1899, Calendar.DECEMBER, 30, hours, minutes, seconds)
        cal.add(Calendar.DAY_OF_YEAR, days.toInt())

        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.time)
    }

    /**
     * 从 XLSX 字节流中解压并提取所有行内容为 CSV 文本行
     */
    fun parseXlsxBytesToLines(bytes: ByteArray): List<String> {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var sharedStringsBytes: ByteArray? = null
            var sheetBytes: ByteArray? = null

            while (entry != null) {
                val name = entry.name
                if (name.equals("xl/sharedStrings.xml", ignoreCase = true)) {
                    sharedStringsBytes = zis.readBytes()
                } else if (sheetBytes == null && (name.equals("xl/worksheets/sheet1.xml", ignoreCase = true) || name.matches(Regex("xl/worksheets/sheet\\d+\\.xml", RegexOption.IGNORE_CASE)))) {
                    sheetBytes = zis.readBytes()
                }
                entry = zis.nextEntry
            }

            if (sheetBytes == null) return emptyList()

            // 1. 解析 sharedStrings.xml
            val sharedStrings = ArrayList<String>()
            if (sharedStringsBytes != null) {
                val dbf = DocumentBuilderFactory.newInstance()
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(sharedStringsBytes))
                val siList = doc.getElementsByTagName("si")
                for (i in 0 until siList.length) {
                    val siElement = siList.item(i) as? Element ?: continue
                    val tList = siElement.getElementsByTagName("t")
                    val sb = StringBuilder()
                    for (j in 0 until tList.length) {
                        sb.append(tList.item(j).textContent ?: "")
                    }
                    sharedStrings.add(sb.toString())
                }
            }

            // 2. 解析 worksheet
            val dbf = DocumentBuilderFactory.newInstance()
            val sheetDoc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(sheetBytes))
            val rowList = sheetDoc.getElementsByTagName("row")
            val lines = mutableListOf<String>()
            var dateColIndex = -1

            for (i in 0 until rowList.length) {
                val rowElement = rowList.item(i) as? Element ?: continue
                val cList = rowElement.getElementsByTagName("c")
                if (cList.length == 0) continue

                val cellMap = mutableMapOf<Int, String>()
                for (j in 0 until cList.length) {
                    val cElement = cList.item(j) as? Element ?: continue
                    val rAttr = cElement.getAttribute("r") ?: ""
                    val colLetters = rAttr.filter { it.isLetter() }
                    val colIdx = if (colLetters.isNotEmpty()) columnLetterToIndex(colLetters) else j
                    val type = cElement.getAttribute("t") ?: ""

                    var cellValue = ""
                    val vList = cElement.getElementsByTagName("v")
                    if (vList.length > 0) {
                        cellValue = vList.item(0).textContent?.trim() ?: ""
                    } else {
                        val tList = cElement.getElementsByTagName("t")
                        if (tList.length > 0) {
                            cellValue = tList.item(0).textContent?.trim() ?: ""
                        }
                    }

                    if (type == "s") {
                        val strIdx = cellValue.toIntOrNull()
                        if (strIdx != null && strIdx in sharedStrings.indices) {
                            cellValue = sharedStrings[strIdx]
                        }
                    }

                    cellMap[colIdx] = cellValue
                }

                if (cellMap.isEmpty()) continue
                val maxCol = cellMap.keys.maxOrNull() ?: 0
                val rowValues = MutableList(maxCol + 1) { cellMap[it] ?: "" }

                // 检查是否为标题行，定位时间列
                for (idx in rowValues.indices) {
                    val v = rowValues[idx]
                    if (v.contains("交易时间") || v.contains("付款时间") || v.contains("创建时间")) {
                        dateColIndex = idx
                        break
                    }
                }

                // 若当前行在时间列且值为 Excel 浮点数字，转换为 yyyy-MM-dd HH:mm:ss
                if (dateColIndex in rowValues.indices) {
                    val dVal = rowValues[dateColIndex]
                    val serial = dVal.toDoubleOrNull()
                    if (serial != null && serial > 30000.0 && serial < 80000.0) {
                        rowValues[dateColIndex] = formatExcelSerialDate(serial)
                    }
                }

                // 拼装为标准 CSV 格式行
                val csvLine = rowValues.joinToString(",") { cell ->
                    if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
                        "\"" + cell.replace("\"", "\"\"") + "\""
                    } else {
                        cell
                    }
                }
                lines.add(csvLine)
            }

            lines
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 从 ZIP 字节流中解压出匹配条件的文件
     */
    fun extractEntryFromZip(bytes: ByteArray, filter: (String) -> Boolean): ByteArray? {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && filter(entry.name)) {
                    return zis.readBytes()
                }
                entry = zis.nextEntry
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从 ZIP 字节流中解压出第一个 CSV 或文本文件
     */
    fun extractFirstCsvFromZip(bytes: ByteArray): ByteArray? {
        return extractEntryFromZip(bytes) {
            it.endsWith(".csv", ignoreCase = true) || it.endsWith(".txt", ignoreCase = true)
        }
    }

    /**
     * 自动检测 XLSX、ZIP（内部含 XLSX 或 CSV）及 UTF-8/GBK CSV 并解码为文本行列表
     */
    fun decodeBytesToLines(rawBytes: ByteArray): List<String> {
        // 1. 如果本身是 XLSX 电子表格，直接提取文本行
        if (isOpenXmlSpreadsheet(rawBytes)) {
            val xlsxLines = parseXlsxBytesToLines(rawBytes)
            if (xlsxLines.isNotEmpty()) {
                return xlsxLines
            }
        }

        // 2. 如果是 ZIP 压缩包，检查是否包含 XLSX 或 CSV/TXT
        val bytes = if (isZipArchive(rawBytes)) {
            val innerXlsx = extractEntryFromZip(rawBytes) { it.endsWith(".xlsx", ignoreCase = true) }
            if (innerXlsx != null) {
                val xlsxLines = parseXlsxBytesToLines(innerXlsx)
                if (xlsxLines.isNotEmpty()) {
                    return xlsxLines
                }
            }
            extractFirstCsvFromZip(rawBytes) ?: rawBytes
        } else {
            rawBytes
        }

        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0

        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val text = try {
            val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
            utf8Decoder.decode(buffer).toString()
        } catch (_: CharacterCodingException) {
            // 支付宝导出的 CSV 普遍为 GBK / GB18030
            String(bytes, Charset.forName("GB18030"))
        }

        return text.lines().map { it.trimEnd('\r', '\n') }
    }

    /**
     * 解析账单行列表（微信/支付宝官方导出或用户直接粘贴的文本）
     */
    fun parseStatementRows(lines: List<String>): List<StatementRow> {
        if (lines.isEmpty()) return emptyList()

        var headerIndex = -1
        var isWeChat = false
        var isAlipay = false

        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("交易时间") && (line.contains("交易单号") || line.contains("商户单号") || line.contains("交易类型") || line.contains("收/支"))) {
                headerIndex = i
                isWeChat = true
                break
            } else if (line.contains("交易号") && (line.contains("商家订单号") || line.contains("交易创建时间") || line.contains("付款时间") || line.contains("金额"))) {
                headerIndex = i
                isAlipay = true
                break
            }
        }

        val dataRows = mutableListOf<StatementRow>()

        if (headerIndex != -1 && headerIndex < lines.size) {
            val headerCells = parseCsvLine(lines[headerIndex])
            val colMap = headerCells.mapIndexed { idx, name -> name.trim() to idx }.toMap()

            for (i in (headerIndex + 1) until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank() || line.startsWith("----")) continue

                val cells = parseCsvLine(line)
                if (cells.size < 3) continue

                try {
                    val row = if (isWeChat) {
                        parseWeChatRow(cells, colMap)
                    } else if (isAlipay) {
                        parseAlipayRow(cells, colMap)
                    } else null

                    if (row != null) {
                        dataRows.add(row)
                    }
                } catch (_: Exception) {}
            }
        } else {
            // 降级容错：如果用户粘贴的是纯数据行（不包含标题行），按标准列序进行智能提取
            val dateRegex = Regex("^(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{1,2}(?::\\d{1,2})?)")
            for (line in lines) {
                val clean = line.trim()
                if (clean.isBlank() || !dateRegex.containsMatchIn(clean)) continue

                val cells = parseCsvLine(clean)
                if (cells.size >= 6) {
                    val fallbackRow = parseFallbackWechatRow(cells) ?: parseFallbackAlipayRow(cells)
                    if (fallbackRow != null) {
                        dataRows.add(fallbackRow)
                    }
                }
            }
        }

        return dataRows
    }

    private fun parseWeChatRow(cells: List<String>, colMap: Map<String, Int>): StatementRow? {
        val timeStr = getCell(cells, colMap, "交易时间") ?: return null
        val typeStr = getCell(cells, colMap, "交易类型") ?: ""
        val counterparty = getCell(cells, colMap, "交易对方") ?: ""
        val product = getCell(cells, colMap, "商品") ?: ""
        val flowDirection = getCell(cells, colMap, "收/支") ?: ""
        val amountStr = getCell(cells, colMap, "金额(元)", "金额（元）", "金额") ?: return null
        val payMethod = getCell(cells, colMap, "支付方式") ?: "微信支付"
        val status = getCell(cells, colMap, "当前状态") ?: ""
        val externalId = getCell(cells, colMap, "交易单号")?.ifBlank { null }

        val type = when {
            flowDirection.contains("支") -> "EXPENSE"
            flowDirection.contains("收") -> "INCOME"
            else -> return null // 中性交易（如充值提现）通常不计入日常收支
        }

        val amountCents = NotificationParser.toCents(amountStr) ?: return null
        val timestamp = parseDateTime(timeStr) ?: return null

        val merchant = when {
            counterparty.isNotBlank() && product.isNotBlank() && product != "/" && !product.startsWith("商户单号") -> "$counterparty - $product"
            counterparty.isNotBlank() -> counterparty
            product.isNotBlank() && !product.startsWith("商户单号") -> product
            else -> typeStr.ifBlank { "微信支付" }
        }

        val category = inferCategory(merchant, "$product $typeStr", "微信支付", type)

        return StatementRow(
            timestamp = timestamp,
            type = type,
            amountCents = amountCents,
            channel = if (payMethod.isNotBlank()) "微信支付 ($payMethod)" else "微信支付",
            merchant = merchant,
            category = category,
            externalId = externalId,
            rawText = "微信账单: $timeStr | $merchant | $amountStr | $status"
        )
    }

    private fun parseFallbackWechatRow(cells: List<String>): StatementRow? {
        // 标准列位：0交易时间, 1交易类型, 2交易对方, 3商品, 4收/支, 5金额, 6支付方式, 7当前状态, 8交易单号
        if (cells.size < 6) return null
        val timeStr = cells[0]
        val typeStr = cells[1]
        val counterparty = cells[2]
        val product = cells[3]
        val flow = cells[4]
        val amountStr = cells[5]

        val type = when {
            flow.contains("支") -> "EXPENSE"
            flow.contains("收") -> "INCOME"
            else -> return null
        }

        val amountCents = NotificationParser.toCents(amountStr) ?: return null
        val timestamp = parseDateTime(timeStr) ?: return null
        val payMethod = if (cells.size > 6) cells[6] else "微信支付"
        val status = if (cells.size > 7) cells[7] else ""
        val externalId = if (cells.size > 8) cells[8].ifBlank { null } else null

        val merchant = when {
            counterparty.isNotBlank() && product.isNotBlank() && product != "/" && !product.startsWith("商户单号") -> "$counterparty - $product"
            counterparty.isNotBlank() -> counterparty
            product.isNotBlank() && !product.startsWith("商户单号") -> product
            else -> typeStr.ifBlank { "微信支付" }
        }

        return StatementRow(
            timestamp = timestamp,
            type = type,
            amountCents = amountCents,
            channel = if (payMethod.isNotBlank()) "微信支付 ($payMethod)" else "微信支付",
            merchant = merchant,
            category = inferCategory(merchant, "$product $typeStr", "微信支付", type),
            externalId = externalId,
            rawText = "微信账单: $timeStr | $merchant | $amountStr | $status"
        )
    }

    private fun parseAlipayRow(cells: List<String>, colMap: Map<String, Int>): StatementRow? {
        val externalId = getCell(cells, colMap, "交易号")?.ifBlank { null }
        val timeStr = getCell(cells, colMap, "付款时间", "交易创建时间", "最近修改时间") ?: return null
        val counterparty = getCell(cells, colMap, "交易对方") ?: ""
        val product = getCell(cells, colMap, "商品名称") ?: ""
        val amountStr = getCell(cells, colMap, "金额（元）", "金额(元)", "金额") ?: return null
        val flowDirection = getCell(cells, colMap, "收/支") ?: ""
        val status = getCell(cells, colMap, "交易状态") ?: ""

        if (status.contains("关闭") || status.contains("失败") || status.contains("等待")) {
            return null
        }

        val type = when {
            flowDirection.contains("支") -> "EXPENSE"
            flowDirection.contains("收") -> "INCOME"
            else -> return null
        }

        val amountCents = NotificationParser.toCents(amountStr) ?: return null
        val timestamp = parseDateTime(timeStr) ?: return null

        val merchant = when {
            counterparty.isNotBlank() && product.isNotBlank() && product != "/" -> "$counterparty - $product"
            counterparty.isNotBlank() -> counterparty
            product.isNotBlank() -> product
            else -> "支付宝交易"
        }

        val category = inferCategory(merchant, product, "支付宝", type)

        return StatementRow(
            timestamp = timestamp,
            type = type,
            amountCents = amountCents,
            channel = "支付宝",
            merchant = merchant,
            category = category,
            externalId = externalId,
            rawText = "支付宝账单: $timeStr | $merchant | $amountStr | $status"
        )
    }

    private fun parseFallbackAlipayRow(cells: List<String>): StatementRow? {
        return null
    }

    private fun getCell(cells: List<String>, colMap: Map<String, Int>, vararg candidates: String): String? {
        for (c in candidates) {
            val idx = colMap[c]
            if (idx != null && idx < cells.size) {
                return cells[idx]
            }
        }
        return null
    }

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    )

    private fun parseDateTime(str: String): Long? {
        val clean = str.trim()
        val num = clean.toDoubleOrNull()
        if (num != null && num > 30000.0 && num < 80000.0) {
            return try {
                val cal = Calendar.getInstance()
                cal.clear()
                val days = num.toLong()
                val fraction = num - days
                val totalSeconds = Math.round(fraction * 86400.0).toInt()
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                cal.set(1899, Calendar.DECEMBER, 30, hours, minutes, seconds)
                cal.add(Calendar.DAY_OF_YEAR, days.toInt())
                cal.timeInMillis
            } catch (_: Exception) {
                null
            }
        }

        for (format in DATE_FORMATS) {
            try {
                val d = format.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    fun inferCategory(merchant: String, title: String, channel: String, type: String): String {
        val combined = "$merchant $title $channel"

        return when {
            containsAny(combined, "外卖", "美团", "饿了么", "餐饮", "咖啡", "麦当劳", "肯德基", "星巴克", "便利店", "全家", "罗森", "超市", "菜场", "食品", "蛋糕", "茶", "酒", "饭", "面", "餐", "小吃", "老乡鸡", "沙县", "蜜雪冰城", "霸王茶姬") -> "餐饮美食"
            containsAny(combined, "出行", "滴滴", "打车", "公交", "地铁", "铁路", "12306", "高德", "加油", "停车", "机票", "航空", "车", "青桔", "骑行", "巴士", "同程") -> "交通出行"
            containsAny(combined, "京东", "淘宝", "天猫", "拼多多", "抖音", "快手", "唯品会", "网购", "商城", "店铺", "购物") -> "网络购物"
            containsAny(combined, "转账", "红包", "人情", "请收款") -> if (type == "INCOME") "其他收入" else "转账红包"
            containsAny(combined, "理财", "基金", "余额宝", "零钱通", "定投", "证券") -> "金融理财"
            containsAny(combined, "水费", "电费", "燃气", "物业", "话费", "宽带", "电信", "移动", "联通", "充值", "共享充电") -> "日常缴费"
            containsAny(combined, "医院", "药", "诊所", "体检", "健康") -> "医疗健康"
            type == "INCOME" -> "其他收入"
            else -> "日常支出"
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        for (k in keywords) {
            if (text.contains(k, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * 直接从文本字符串解析导入（支持用户直接粘贴或从剪贴板读取）
     */
    suspend fun importFromText(text: String, dao: TransactionDao): ImportSummary {
        val lines = text.lines().map { it.trimEnd('\r', '\n') }
        val rows = parseStatementRows(lines)
        return processStatementRows(rows, dao)
    }

    /**
     * 从输入流执行完整账单导入（支持 CSV 或 ZIP 压缩包）
     */
    suspend fun importFromStream(inputStream: InputStream, dao: TransactionDao): ImportSummary {
        return try {
            val bytes = inputStream.readBytes()
            val lines = decodeBytesToLines(bytes)
            val rows = parseStatementRows(lines)
            processStatementRows(rows, dao)
        } catch (e: Exception) {
            ImportSummary(errorMessage = "导入失败: ${e.message}")
        }
    }

    /**
     * 核心去重与入账处理逻辑
     */
    suspend fun processStatementRows(rows: List<StatementRow>, dao: TransactionDao): ImportSummary {
        if (rows.isEmpty()) {
            return ImportSummary(totalRows = 0, errorMessage = "未能识别到有效的微信或支付宝账单记录，请核对是否包含交易时间与金额")
        }

        var newInserted = 0
        var mergedExisting = 0
        var skipped = 0

        for (row in rows) {
            // 1. 精确外键匹配去重
            if (row.externalId != null) {
                val existing = dao.getByExternalId(row.externalId)
                if (existing != null) {
                    skipped++
                    continue
                }
            }

            // 2. 近邻时间差与金额智能合并去重（处理先前被通知栏捕获但缺乏订单号的记录）
            // 设定前后 90 秒窗口
            val nearby = dao.findNearbyByAmount(row.amountCents, row.timestamp - 90_000L, row.timestamp + 90_000L)
            val unlinkedMatch = nearby.firstOrNull { it.externalId == null && it.type == row.type }

            if (unlinkedMatch != null) {
                // 合并补充外部订单号与更详尽的商家信息
                val updated = unlinkedMatch.copy(
                    externalId = row.externalId,
                    merchant = if (unlinkedMatch.merchant.isNullOrBlank() || unlinkedMatch.merchant == unlinkedMatch.channel) {
                        row.merchant ?: unlinkedMatch.merchant
                    } else {
                        unlinkedMatch.merchant
                    },
                    category = if (unlinkedMatch.category == "日常支出" || unlinkedMatch.category == "未分类" || unlinkedMatch.category == "日常消费") {
                        row.category
                    } else {
                        unlinkedMatch.category
                    },
                    updatedAt = System.currentTimeMillis()
                )
                dao.update(updated)
                mergedExisting++
            } else {
                // 全新入账
                val newRecord = TransactionRecord(
                    amountCents = row.amountCents,
                    type = row.type,
                    channel = row.channel,
                    merchant = row.merchant,
                    category = row.category,
                    timestamp = row.timestamp,
                    rawText = row.rawText,
                    externalId = row.externalId,
                    confidence = "HIGH"
                )
                dao.insert(newRecord)
                newInserted++
            }
        }

        return ImportSummary(
            totalRows = rows.size,
            newInserted = newInserted,
            mergedExisting = mergedExisting,
            skipped = skipped
        )
    }
}
