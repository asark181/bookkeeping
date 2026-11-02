package com.bookkeeping.app.parser

import com.bookkeeping.app.data.db.entity.TransactionRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class CsvStatementImporterTest {

    @Test
    fun testRealUserWeChatCsv() {
        val csv = """
微信支付账单明细,,,,,,,,,,
微信昵称：[用户A],,,,,,,,,,
起始时间：[2026-09-27 00:00:00] 终止时间：[2026-10-27 16:07:03],,,,,,,,,,
导出类型：[全部账单],,,,,,,,,,
导出时间：[2026-10-27 16:07:03],,,,,,,,,,
,,,,,,,,,,
共65笔记录,,,,,,,,,,
收入：12笔 1144.71元,,,,,,,,,,
支出：53笔 2772.52元,,,,,,,,,,
中性交易：0笔 0.00元,,,,,,,,,,
注：,,,,,,,,,,
1. 充值/提现/理财通购买/零钱通存取/信用卡还款等交易，将计入中性交易,,,,,,,,,,
2. 若交易记录明细无有效内容，则代表该时间段内此微信号无交易,,,,,,,,,,
3. 本明细仅供个人对账使用,,,,,,,,,,
4. 本账单中所有时间均为UTC+08:00时间,,,,,,,,,,
,,,,,,,,,,
----------------------微信支付账单明细列表--------------------,,,,,,,,,,
交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
2026-10-27 15:19:46,微信红包（单发）,发给a,/,支出,0.01,零钱,支付成功,145524795938735547867909404750439930,8115172330892815761231121901522,/
2026-10-27 15:19:27,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,44745448015810043974420105042705,9619404035251431690410156141253,/
2026-10-27 15:08:30,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,08725802375409634472788214987203,3252040212235323007714494217813,/
2026-10-27 15:06:17,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,93694368219017064305464344024845,0474213046054443322006905365005,/
2026-10-27 14:56:45,微信红包（单发）,发给a,/,支出,0.01,零钱,支付成功,346329313589642441944042219712936003,2647326325404655672543479516522,/
2026-10-27 14:56:16,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,67049460639365356248601147286803,2187593343705970114012701251333,/
2026-10-27 14:55:48,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,38000149791450539098014341333183,0141692621961448233242706495096,/
2026-10-26 13:54:02,商户消费,星巴克,星巴克啡快,支出,6,零钱,支付成功,1504185059852569342896200643,2610261353W00446261,/
2026-10-24 00:07:46,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,685053310452250406457172650726022854221172534,9510685310305361247046639411227161936039,打开拼多多，领更多现金红包
2026-10-23 00:08:12,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,831098045961491247032619140535458446332816003,4023217011324506850845072711303382241857,打开拼多多，领更多现金红包
2026-10-22 19:42:05,商户消费,老乡鸡,微信充值,支出,400,示例银行信用卡(0001),支付成功,0418820657106045038056276461,90670144147453588523149,/
2026-10-22 13:59:41,转账-退款,/,转账备注:微信转账,收入,300,示例银行储蓄卡(0002),已全额退款,535223430106692549287795090457032246431,,/
2026-10-22 13:58:38,转账,亲友,转账备注:微信转账,支出,300,示例银行储蓄卡(0002),对方已退还,13240163032633324820415143510086,0330736200511548774582201134610,/
2026-10-22 09:11:49,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,9.5,零钱,已转账,53929459398311525038692610051120,41329135152035050954064226145758,/
2026-10-22 08:41:19,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,240244252016916319880021461982521117491460092,2162848659519200425915071015002832230320,打开拼多多，领更多现金红包
2026-10-21 18:05:25,转账,好友A,转账备注:微信转账,支出,20.63,零钱,对方已收钱,65056908957356357812009641507862,3158848821231936923543334790572,/
2026-10-21 09:07:30,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,零钱,已转账,22381973023052213332571468054915,51140101563590310312198355485800,/
2026-10-20 14:25:29,微信红包,好友A,/,收入,168,/,已存入零钱,5540644274811225561461529100691,9545111812243579153220560314548,/
2026-10-20 14:11:30,转账,好友B,转账备注:微信转账,收入,72.2,/,已存入零钱,3355240935302380510669278103255,/,/
2026-10-19 00:05:58,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,503509214402935532155631587412405206019352225,5204334131871041356577493432491954457119,打开拼多多，领更多现金红包
2026-10-17 00:07:51,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,129141429564067512474744473323315030478300901,6058603585644863513961125436585071123422,打开拼多多，领更多现金红包
2026-10-16 18:32:30,商户消费,沙县小吃,快餐,支出,16,示例银行信用卡(0001),支付成功,0180838215180268126062654358,82473421211948855773027215521684,/
2026-10-16 08:59:37,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,示例银行储蓄卡(0002),已转账,06041732118092103034150362638414,01430010159041583224660349620241,/
2026-10-15 20:51:03,商户消费,淘宝平台商户,商户单号32294501444720259264522560860,支出,21.2,示例银行信用卡(0001),支付成功,6728241410430111770047238035,32294501444720259264522560860,/
2026-10-15 20:49:53,商户消费,淘宝平台商户,商户单号01552810531559442418897047532,支出,21.2,示例银行信用卡(0001),支付成功,0604014382773822962442236652,01552810531559442418897047532,/
2026-10-15 20:11:43,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,312698395115362037484966585337661635186391091,5225348216391074021510030846060352900237,打开拼多多，领更多现金红包
2026-10-15 19:23:07,商户消费,老乡鸡,老乡鸡,支出,78.8,示例银行信用卡(0001),支付成功,1993152815011700310373417210,kj911256710240360404448550,/
2026-10-15 12:20:23,商户消费,腾讯游戏,一元福利周 * 1(为1**000购买),支出,1,零钱,支付成功,5907150950227549444076812138,product_SZ_34111305610325100,/
2026-10-15 06:13:14,商户消费,中国电信,充值 20.0 元,支出,19.96,示例银行储蓄卡(0002),支付成功,0803333519110200916516235194,ZY4042531423449604160238,/
2026-10-14 09:01:24,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,示例银行储蓄卡(0002),已转账,19993415726422139304102522733127,50311777530022210339725211219307,/
2026-10-13 08:55:25,商户消费,共享充电,普通订单,支出,10,示例银行储蓄卡(0002),支付成功,1046257593129021276527149478,298533232283732674572521973849,/
2026-10-12 22:49:24,商户消费,起点读书,充值600起点币,支出,6,示例银行信用卡(0001),支付成功,6826011473434843507226002290,5802416600910957424,/
2026-10-09 22:50:29,其他,淘宝签到提现,/,收入,0.15,/,已到账,584195040460001305052343434140241573637442701,7043432754310200412302233110601881,淘宝签到提现
2026-10-09 22:47:30,其他,淘宝签到提现,/,收入,0.4,/,已到账,754045711232056395122230156148484225332755039,0366364801582029145225590580355622,淘宝签到提现
2026-10-09 22:22:58,商户消费,腾讯,支付订单(为1**000购买),支出,24,示例银行信用卡(0001),支付成功,9541533018138982106305544545,product_SZ_10413611251151440,/
2026-10-09 12:00:22,商户消费,腾讯游戏,王者荣耀王者荣耀点券(为1**000购买),支出,6,示例银行信用卡(0001),支付成功,5321210053433010005521531323,product_SH_75113485288402471,/
2026-10-08 19:59:14,商户消费,示例彩票站,示例彩票站,支出,40,示例银行储蓄卡(0002),支付成功,5098414228007292829655321042,34113955155422299306725053415133,/
2026-10-08 19:42:51,商户消费,老乡鸡,老乡鸡,支出,52,示例银行信用卡(0001),支付成功,9724542197565045012464983215,kj411033520122414322420218,/
2026-10-07 09:25:38,商户消费,腾讯游戏,王者荣耀王者荣耀点券(为1**000购买),支出,18,示例银行信用卡(0001),支付成功,4044254885781619237220250010,product_SH_349411251054922254,/
2026-10-05 16:23:58,商户消费,霸王茶姬,CHAGEE霸王茶姬（示例门店）,支出,38,示例银行信用卡(0001),支付成功,1389562730365931286024352333,077355240720011942208416509101,/
2026-10-05 15:55:25,商户消费,滴滴出行平台商户,先乘车后付款,支出,26.39,示例银行信用卡(0001),支付成功,0288646122224300429434504980,kWIARbfGhlPOnjAN1ZLPA7xxxxqXijCx,/
2026-10-05 12:41:34,商户消费,美团,团购-美团微信小程序-21555066967721922299381287341047,支出,181.3,示例银行信用卡(0001),支付成功,6831283424415448429473770985,20261005124129U90473659111043320,/
2026-10-04 13:06:58,商户消费,同程旅行,巴士快线,支出,224,示例银行信用卡(0001),支付成功,9041222145559572807278004154,DCY6AC1DUUDSGUPHHF2UJ,/
2026-10-03 22:56:22,扫二维码付款,个人收款码,收款方备注:二维码收款,支出,25,示例银行储蓄卡(0002),已转账,00164566131105551840618139905735,41561274403224328701337524112062,/
2026-10-03 14:55:20,商户消费,同程旅行,巴士快线,支出,224,示例银行信用卡(0001),支付成功,5893240002351715875881499321,DCY6AC0A6C53HHC7U6JE9,/
2026-10-03 14:40:18,商户消费,滴滴出行平台商户,先乘车后付款,支出,22.08,示例银行信用卡(0001),支付成功,1402686543023682216553356509,R0YJaC_t8zftkeH_s5atJ7xxxxxdKP0x,/
2026-10-03 11:28:26,商户消费,示例书店,示例书店 店内购物,支出,53,示例银行信用卡(0001),支付成功,9765835543340800349204644051,54557483143360005236,/
2026-10-03 11:16:13,扫二维码付款,示例小吃摊,收款方备注:二维码收款,支出,5,示例银行储蓄卡(0002),已转账,04903180481503816655304854439550,10748038244603629229151263010157,/
2026-10-03 10:56:02,商户消费,滴滴出行,先乘车后付款,支出,9.9,示例银行信用卡(0001),支付成功,0795405378886323881414220545,Il1CZzCBvP7YBLUpP_iM-xxxxxjyNJZx,/
2026-10-03 10:11:43,商户消费,滴滴出行,先乘车后付款,支出,10.2,示例银行信用卡(0001),支付成功,9321544041252059313900287554,T9kk_10FWm5zuSP73Qx25CxxxxjKmjFx,/
2026-10-02 20:23:51,商户消费,美团,团购-美团微信小程序-20811118348241148617348888253324,支出,183.01,示例银行信用卡(0001),支付成功,2114352556529314410488202088,20261002202345U46343030477694231,/
2026-10-02 17:17:13,商户消费,滴滴出行平台商户,先乘车后付款,支出,22.68,示例银行信用卡(0001),支付成功,9114646024342448449471101210,lx65O1X8ragv7M6jI9Mlr7xxxxPwwiyx,/
2026-10-02 12:39:09,扫二维码付款,青桔,收款方备注:二维码收款,支出,4,零钱,已转账,16353449521851501170953135901059,05331021000007204081529581475530,/
2026-10-02 12:29:09,商户消费,蜜雪冰城,蜜雪冰城示例店,支出,10.8,示例银行信用卡(0001),支付成功,1293780003534435402503550572,053092810117268377404661128257,/
2026-10-02 12:03:44,商户消费,滴滴出行,先乘车后付款,支出,33,示例银行信用卡(0001),支付成功,5200626332305424655245030187,g68O4SID_g8NFu5o9l-IUzxxxxjf2Tix,/
2026-10-02 07:15:55,商户消费,滴滴出行,先乘车后付款,支出,29.6,示例银行信用卡(0001),支付成功,0098627301380342135084232975,xShD23JM5Gkl-thjRX4kOxxxxxqULxyx,/
2026-10-01 19:54:58,商户消费,示例茶饮,【堂食】_示例门店,支出,35.8,示例银行信用卡(0001),支付成功,2309281440273435838215031290,139435013301923545221083231452,/
2026-10-01 13:13:19,商户消费,美团平台商户,团购-大众点评微信小程序-95256770260560445975081404044297,支出,14,示例银行信用卡(0001),支付成功,5421095267026406598878422047,20261001131314U40394303008505736,/
2026-10-01 13:12:16,商户消费,美团平台商户,团购-大众点评微信小程序-95939047427137361853056753520859,支出,165.5,示例银行信用卡(0001),支付成功,3305449464200317015523544236,20261001131210U79801666147426710,/
2026-10-01 06:22:55,商户消费,肯德基,KFC_PRE_PLUSWX21616050054641143667442,支出,43.9,示例银行信用卡(0001),支付成功,1334233001550431345554280241,WX21616050054641143667442,/
2026-10-01 05:56:25,商户消费,滴滴出行,先乘车后付款,支出,125,示例银行信用卡(0001),支付成功,2753994689145554047213201152,SawhaluRpZ0KVtVyEqX7-Cxxxxjn7xxx,/
2026-09-29 16:38:30,商户消费,腾讯,支付订单(为1**000购买),支出,24,示例银行信用卡(0001),支付成功,3476169568830340175002849493,product_SZ_54680854103345515,/
2026-09-29 09:01:09,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,零钱,已转账,00272253727325407050456921593520,33235844212226383508777904235327,/
2026-09-27 22:59:08,商户消费,示例景区,示例景区,支出,192,示例银行信用卡(0001),支付成功,7282433542707379283123340188,454215223135531233283099351831,/
2026-09-27 19:51:18,转账-退款,/,转账备注:微信转账,收入,600,示例银行储蓄卡(0002),已全额退款,718823320140544337343433623015052100517,,/
        """.trimIndent()

        val lines = csv.lines()
        val rows = CsvStatementImporter.parseStatementRows(lines)
        println("REAL USER CSV PARSED ROWS: " + rows.size)
        assertEquals(65, rows.size)
    }

    @Test
    fun testZipArchiveHandling() {
        val originalCsv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号\n" +
                "2026-10-27 12:00:00,商户消费,全家,早餐,支出,15.50,零钱,支付成功,TX12345"
        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)
        zos.putNextEntry(java.util.zip.ZipEntry("wechat_bill.csv"))
        zos.write(originalCsv.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val zipBytes = baos.toByteArray()
        assertTrue(CsvStatementImporter.isZipArchive(zipBytes))
        val lines = CsvStatementImporter.decodeBytesToLines(zipBytes)
        val rows = CsvStatementImporter.parseStatementRows(lines)
        assertEquals(1, rows.size)
        assertEquals(1550L, rows[0].amountCents)
        assertEquals("全家 - 早餐", rows[0].merchant)
    }

    @Test
    fun testFallbackRowWithoutHeader() {
        val snippet = "2026-10-27 15:19:46,微信红包（单发）,发给a,/,支出,0.01,零钱,支付成功,145524795938735547867909404750439930,8115172330892815761231121901522,/"
        val rows = CsvStatementImporter.parseStatementRows(listOf(snippet))
        assertEquals(1, rows.size)
        assertEquals(1L, rows[0].amountCents)
        assertEquals("EXPENSE", rows[0].type)
        assertEquals("发给a", rows[0].merchant)
    }

    @Test
    fun testParseCsvLine() {
        val line = "2024-05-01 12:30:00,商户消费,\"瑞幸咖啡,北京三里屯店\",生椰拿铁,支出,\"¥15.00\",微信零钱,支付成功,\t4200001234,/"
        val cells = CsvStatementImporter.parseCsvLine(line)
        assertEquals(10, cells.size)
        assertEquals("2024-05-01 12:30:00", cells[0])
        assertEquals("商户消费", cells[1])
        assertEquals("瑞幸咖啡,北京三里屯店", cells[2])
        assertEquals("生椰拿铁", cells[3])
        assertEquals("支出", cells[4])
        assertEquals("¥15.00", cells[5])
        assertEquals("微信零钱", cells[6])
        assertEquals("支付成功", cells[7])
        assertEquals("4200001234", cells[8])
        assertEquals("", cells[9])
    }

    @Test
    fun testParseWeChatCsv() {
        val csv = """
            微信支付账单明细
            微信支付账单明细列表
            ----------------------
            制表时间:2024-05-01 10:00:00
            ----------------------
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2024-05-01 08:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,微信零钱,支付成功,1731911556046407310089904748,1234567890,/
            2024-05-01 12:15:30,微信红包,用户A,/,支出,¥10.00,零钱通,已送达,0305165682612810983499944324,0305165682612810983499944324,/
            2024-05-01 18:20:00,转账,张三,/,支出,¥50.00,招商银行(1234),对方已收钱,1308436556320624154457091891,1308436556320624154457091891,/
            2024-05-01 19:00:00,转账,李四,/,收入,¥100.00,/,已存入零钱,0925463260315003832832604450,0925463260315003832832604450,/
        """.trimIndent()

        val lines = csv.lines()
        val rows = CsvStatementImporter.parseStatementRows(lines)

        assertEquals(4, rows.size)

        // 验证瑞幸咖啡
        val row1 = rows[0]
        assertEquals("EXPENSE", row1.type)
        assertEquals(1500L, row1.amountCents)
        assertEquals("1731911556046407310089904748", row1.externalId)
        assertTrue(row1.merchant?.contains("瑞幸咖啡") == true)
        assertEquals("餐饮美食", row1.category)

        // 验证红包
        val row2 = rows[1]
        assertEquals("EXPENSE", row2.type)
        assertEquals(1000L, row2.amountCents)
        assertEquals("用户A", row2.merchant)

        // 验证转账支出
        val row3 = rows[2]
        assertEquals("EXPENSE", row3.type)
        assertEquals(5000L, row3.amountCents)
        assertEquals("张三", row3.merchant)

        // 验证转账收入
        val row4 = rows[3]
        assertEquals("INCOME", row4.type)
        assertEquals(10000L, row4.amountCents)
        assertEquals("李四", row4.merchant)
        assertEquals("其他收入", row4.category)
    }

    @Test
    fun testParseAlipayCsv() {
        val csv = """
            ------------------------------------------------------------------------------------
            支付宝交易记录明细查询
            账号:[xxxxxx]
            ------------------------------------------------------------------------------------
            交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,资金状态
            3135659795340020154017422250,MER12345,2024-05-01 10:00:00,2024-05-01 10:00:05,2024-05-01 10:00:05,手机,即时到账,星巴克,拿铁咖啡,38.00,支出,交易成功,0.00,0.00,已支出
            2520224705722543389365016517,MER99999,2024-05-01 11:00:00,2024-05-01 11:00:05,2024-05-01 11:00:05,手机,即时到账,全家便利店,三明治,12.50,支出,交易成功,0.00,0.00,已支出
        """.trimIndent()

        val lines = csv.lines()
        val rows = CsvStatementImporter.parseStatementRows(lines)

        assertEquals(2, rows.size)
        assertEquals(3800L, rows[0].amountCents)
        assertEquals("3135659795340020154017422250", rows[0].externalId)
        assertTrue(rows[0].merchant?.contains("星巴克") == true)
        assertEquals("餐饮美食", rows[0].category)

        assertEquals(1250L, rows[1].amountCents)
        assertEquals("2520224705722543389365016517", rows[1].externalId)
        assertTrue(rows[1].merchant?.contains("全家便利店") == true)
    }

    @Test
    fun testGbkEncodingDecoding() {
        val original = "交易号,商家订单号,付款时间,交易对方,商品名称,金额（元）,收/支,交易状态\n" +
                "20240501001,MER1,2024-05-01 12:00:00,肯德基,香辣鸡腿堡,21.00,支出,交易成功"
        val gbkBytes = original.toByteArray(Charset.forName("GBK"))
        val decodedLines = CsvStatementImporter.decodeBytesToLines(gbkBytes)
        val rows = CsvStatementImporter.parseStatementRows(decodedLines)

        assertEquals(1, rows.size)
        assertEquals(2100L, rows[0].amountCents)
        assertEquals("肯德基 - 香辣鸡腿堡", rows[0].merchant)
        assertEquals("餐饮美食", rows[0].category)
    }

    @Test
    fun testDeduplicationAndMerge() = runBlocking {
        val existingRecordFromNotification = TransactionRecord(
            id = 101L,
            amountCents = 1500L,
            type = "EXPENSE",
            channel = "微信支付",
            merchant = "微信支付",
            category = "日常支出",
            timestamp = 1714523415000L, // 2024-05-01 08:30:15
            externalId = null
        )

        val records = mutableListOf(existingRecordFromNotification)

        val fakeDao = FakeTransactionDao(records)

        // CSV 数据包含两条：
        // 1. 瑞幸咖啡 15.00元（与已有通知记录时间、金额一致） -> 应合并去重
        // 2. 新记录 28.00元 -> 应新增
        val csv = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2024-05-01 08:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,微信零钱,支付成功,4200000001,1001,/
            2024-05-01 14:20:00,商户消费,喜茶,芝芝莓莓,支出,¥28.00,微信零钱,支付成功,4200000002,1002,/
        """.trimIndent()

        val summary = CsvStatementImporter.importFromStream(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            fakeDao
        )

        assertEquals(2, summary.totalRows)
        assertEquals(1, summary.mergedExisting)
        assertEquals(1, summary.newInserted)
        assertEquals(0, summary.skipped)

        // 验证合并后的记录
        val merged = records.first { it.id == 101L }
        assertEquals("4200000001", merged.externalId)
        assertEquals("瑞幸咖啡 - 生椰拿铁", merged.merchant)
        assertEquals("餐饮美食", merged.category)

        // 再次重复导入同一个 CSV，应当全部 skipped
        val repeatSummary = CsvStatementImporter.importFromStream(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            fakeDao
        )
        assertEquals(2, repeatSummary.totalRows)
        assertEquals(0, repeatSummary.mergedExisting)
        assertEquals(0, repeatSummary.newInserted)
        assertEquals(2, repeatSummary.skipped)
    }

    @Test
    fun testDirectRealUserUploadedXlsx() {
        val file = java.io.File("src/test/resources/wechat_bill_test.xlsx")
        assertTrue("Test xlsx fixture must exist", file.exists())
        val bytes = file.readBytes()

        // 1. 验证 OpenXML 格式识别
        assertTrue("Must be identified as OpenXML spreadsheet", CsvStatementImporter.isOpenXmlSpreadsheet(bytes))

        // 2. 验证行提取与日期浮点转换
        val lines = CsvStatementImporter.decodeBytesToLines(bytes)
        assertTrue("Lines extracted from XLSX should not be empty", lines.isNotEmpty())

        // 3. 验证 65 笔账单全量解析
        val rows = CsvStatementImporter.parseStatementRows(lines)
        assertEquals("Should parse exactly 65 transactions", 65, rows.size)

        val expenses = rows.filter { it.type == "EXPENSE" }
        val incomes = rows.filter { it.type == "INCOME" }
        assertEquals("Should have exactly 53 expenses", 53, expenses.size)
        assertEquals("Should have exactly 12 incomes", 12, incomes.size)

        // 验证第一笔红包记录
        val firstRow = rows[0]
        assertEquals("EXPENSE", firstRow.type)
        assertEquals(1L, firstRow.amountCents)
        assertEquals("145524795938735547867909404750439930", firstRow.externalId)
        assertEquals("微信支付 (零钱)", firstRow.channel)
        assertEquals("转账红包", firstRow.category)

        // 验证金额转换与商户识别 (如老乡鸡充值400元)
        val laoxiangji = rows.firstOrNull { it.merchant?.contains("老乡鸡") == true && it.amountCents == 40000L }
        assertNotNull("Should parse laoxiangji 400 transaction", laoxiangji)
        assertEquals("EXPENSE", laoxiangji?.type)
        assertEquals("餐饮美食", laoxiangji?.category)
        assertEquals("0418820657106045038056276461", laoxiangji?.externalId)
    }

    @Test
    fun testZipContainingXlsx() {
        val file = java.io.File("src/test/resources/wechat_bill_test.xlsx")
        val xlsxBytes = file.readBytes()

        // 将 xlsx 打包进 zip
        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)
        zos.putNextEntry(java.util.zip.ZipEntry("bill_20261027.xlsx"))
        zos.write(xlsxBytes)
        zos.closeEntry()
        zos.close()

        val zipBytes = baos.toByteArray()
        assertTrue(CsvStatementImporter.isZipArchive(zipBytes))

        val lines = CsvStatementImporter.decodeBytesToLines(zipBytes)
        val rows = CsvStatementImporter.parseStatementRows(lines)
        assertEquals("Should parse 65 transactions from zip containing xlsx", 65, rows.size)
    }
}
