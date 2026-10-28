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
2026-10-27 15:19:46,微信红包（单发）,发给a,/,支出,0.01,零钱,支付成功,702875366606343249856721400021449540,2104259460313224851655266767005,/
2026-10-27 15:19:27,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,31237196882344214423913275000380,5939754217660512135252830984902,/
2026-10-27 15:08:30,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,38111811200407141665021932625217,2040379048511895522480332590644,/
2026-10-27 15:06:17,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,01065682251121521828184481719083,1598033445221397556355769687809,/
2026-10-27 14:56:45,微信红包（单发）,发给a,/,支出,0.01,零钱,支付成功,912443304152062262252460930322746721,4313215820063166235031323951123,/
2026-10-27 14:56:16,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,24102710366956000605163480186750,3305755210125150422810741431036,/
2026-10-27 14:55:48,转账,a,转账备注:微信转账,支出,0.01,零钱,等待对方确认收钱,62476031219461158045213012281218,5445711424294932176136733554776,/
2026-10-26 13:54:02,商户消费,星巴克,星巴克啡快,支出,6,零钱,支付成功,9240778415812458050188884037,2610261353W00446261,/
2026-10-24 00:07:46,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,312941411245872953796593493250674390193400501,1803064788289424530477291500048014441515,打开拼多多，领更多现金红包
2026-10-23 00:08:12,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,396810206216919229203837532743738167941876150,2044399071165845593465153154155104454643,打开拼多多，领更多现金红包
2026-10-22 19:42:05,商户消费,老乡鸡,微信充值,支出,400,示例银行信用卡(0001),支付成功,4752502191462221822405186402,37751403623725584711762,/
2026-10-22 13:59:41,转账-退款,/,转账备注:微信转账,收入,300,示例银行储蓄卡(0002),已全额退款,435711415070300134331509141716274533361,,/
2026-10-22 13:58:38,转账,亲友,转账备注:微信转账,支出,300,示例银行储蓄卡(0002),对方已退还,34261426135212336277645280452630,3216224763044435468176121149184,/
2026-10-22 09:11:49,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,9.5,零钱,已转账,82453612427763888992113336112255,04560822513639769042346505538023,/
2026-10-22 08:41:19,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,107332585719034375445525195692080819410852352,0213154546042630315113654422470445145039,打开拼多多，领更多现金红包
2026-10-21 18:05:25,转账,好友A,转账备注:微信转账,支出,20.63,零钱,对方已收钱,74321759143522175476912575049510,3242172360173428493401489021141,/
2026-10-21 09:07:30,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,零钱,已转账,47351875730366532103181594084521,17503157203142259552062595332050,/
2026-10-20 14:25:29,微信红包,好友A,/,收入,168,/,已存入零钱,7172027195133070005548401075025,5246445651809564983327811740508,/
2026-10-20 14:11:30,转账,好友B,转账备注:微信转账,收入,72.2,/,已存入零钱,2911231010900720214295314175245,/,/
2026-10-19 00:05:58,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,413864047524310275505710503531315900054382583,4605212141853554555007056108913001354128,打开拼多多，领更多现金红包
2026-10-17 00:07:51,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,526573292730451782359508520070702292039231215,0512140008146515553355599180140552542318,打开拼多多，领更多现金红包
2026-10-16 18:32:30,商户消费,沙县小吃,快餐,支出,16,示例银行信用卡(0001),支付成功,0821819261532312800006335619,03763519050340733532511551823114,/
2026-10-16 08:59:37,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,示例银行储蓄卡(0002),已转账,31232753522625227331973772121141,36141502399248209113304303304761,/
2026-10-15 20:51:03,商户消费,淘宝平台商户,商户单号25147121721424948443010314781,支出,21.2,示例银行信用卡(0001),支付成功,5722416569528335436164848862,25147121721424948443010314781,/
2026-10-15 20:49:53,商户消费,淘宝平台商户,商户单号37782594723091225422622009343,支出,21.2,示例银行信用卡(0001),支付成功,6358703406013200611078709487,37782594723091225422622009343,/
2026-10-15 20:11:43,其他,打开拼多多，领更多现金红包,/,收入,0.66,/,已到账,675666570403027541304350043130096821242931724,1852618295176733187926414808223081598155,打开拼多多，领更多现金红包
2026-10-15 19:23:07,商户消费,老乡鸡,老乡鸡,支出,78.8,示例银行信用卡(0001),支付成功,8822721395004447704265144230,kj137230286415134080158605,/
2026-10-15 12:20:23,商户消费,腾讯游戏,一元福利周 * 1(为1**000购买),支出,1,零钱,支付成功,1052135146400251200031371604,product_SZ_90326650078100944,/
2026-10-15 06:13:14,商户消费,中国电信,充值 20.0 元,支出,19.96,示例银行储蓄卡(0002),支付成功,8850428545175691532295307150,ZY0368344034223117342572,/
2026-10-14 09:01:24,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,示例银行储蓄卡(0002),已转账,81132831251163212115714183114754,13502352252227443857385844352089,/
2026-10-13 08:55:25,商户消费,共享充电,普通订单,支出,10,示例银行储蓄卡(0002),支付成功,3351521152421821053703481190,760552169608529185456030094721,/
2026-10-12 22:49:24,商户消费,起点读书,充值600起点币,支出,6,示例银行信用卡(0001),支付成功,2584450104203144926505152200,9430052435535848743,/
2026-10-09 22:50:29,其他,淘宝签到提现,/,收入,0.15,/,已到账,056301144411725133557113046209499377399850246,4641316453720433258652003220355712,淘宝签到提现
2026-10-09 22:47:30,其他,淘宝签到提现,/,收入,0.4,/,已到账,445516813460141227090722052213743453165540903,5930212214557406562013184442960208,淘宝签到提现
2026-10-09 22:22:58,商户消费,腾讯,支付订单(为1**000购买),支出,24,示例银行信用卡(0001),支付成功,6703516536882574118232335832,product_SZ_42153521729934754,/
2026-10-09 12:00:22,商户消费,腾讯游戏,王者荣耀王者荣耀点券(为1**000购买),支出,6,示例银行信用卡(0001),支付成功,3514959204210436680242125829,product_SH_81253523982850415,/
2026-10-08 19:59:14,商户消费,示例彩票站,示例彩票站,支出,40,示例银行储蓄卡(0002),支付成功,9611214281200123385815731600,30742621334485855544202534064244,/
2026-10-08 19:42:51,商户消费,老乡鸡,老乡鸡,支出,52,示例银行信用卡(0001),支付成功,4562401555671103084835311703,kj202521090547522357132217,/
2026-10-07 09:25:38,商户消费,腾讯游戏,王者荣耀王者荣耀点券(为1**000购买),支出,18,示例银行信用卡(0001),支付成功,1536350853152409944403044073,product_SH_768218420115108326,/
2026-10-05 16:23:58,商户消费,霸王茶姬,CHAGEE霸王茶姬（示例门店）,支出,38,示例银行信用卡(0001),支付成功,3390401835231340318457335495,553226455937761590007247338350,/
2026-10-05 15:55:25,商户消费,滴滴出行平台商户,先乘车后付款,支出,26.39,示例银行信用卡(0001),支付成功,1035211141726428282611110537,kWIARbfGhlPOnjAN1ZLPA7xxxxqXijCx,/
2026-10-05 12:41:34,商户消费,美团,团购-美团微信小程序-35598412006195274354454374922834,支出,181.3,示例银行信用卡(0001),支付成功,1753437560952021353914462471,20261005124129U78544375420654135,/
2026-10-04 13:06:58,商户消费,同程旅行,巴士快线,支出,224,示例银行信用卡(0001),支付成功,3655303514052933252834017187,DCY6AC1DUUDSGUPHHF2UJ,/
2026-10-03 22:56:22,扫二维码付款,个人收款码,收款方备注:二维码收款,支出,25,示例银行储蓄卡(0002),已转账,12941802400763103341290395608351,94066012422105383060076035376019,/
2026-10-03 14:55:20,商户消费,同程旅行,巴士快线,支出,224,示例银行信用卡(0001),支付成功,8528059642599033332305610403,DCY6AC0A6C53HHC7U6JE9,/
2026-10-03 14:40:18,商户消费,滴滴出行平台商户,先乘车后付款,支出,22.08,示例银行信用卡(0001),支付成功,1179357516749548152242249332,R0YJaC_t8zftkeH_s5atJ7xxxxxdKP0x,/
2026-10-03 11:28:26,商户消费,示例书店,示例书店 店内购物,支出,53,示例银行信用卡(0001),支付成功,3501155069362262321053314922,13633841225559438476,/
2026-10-03 11:16:13,扫二维码付款,示例小吃摊,收款方备注:二维码收款,支出,5,示例银行储蓄卡(0002),已转账,02220352146402962503115571072520,01013454442431592002651437157547,/
2026-10-03 10:56:02,商户消费,滴滴出行,先乘车后付款,支出,9.9,示例银行信用卡(0001),支付成功,3200771324373543310254482217,Il1CZzCBvP7YBLUpP_iM-xxxxxjyNJZx,/
2026-10-03 10:11:43,商户消费,滴滴出行,先乘车后付款,支出,10.2,示例银行信用卡(0001),支付成功,5823572063001894547413308624,T9kk_10FWm5zuSP73Qx25CxxxxjKmjFx,/
2026-10-02 20:23:51,商户消费,美团,团购-美团微信小程序-21245193900035224050111034149706,支出,183.01,示例银行信用卡(0001),支付成功,6241493022221013146633413927,20261002202345U10023332351102443,/
2026-10-02 17:17:13,商户消费,滴滴出行平台商户,先乘车后付款,支出,22.68,示例银行信用卡(0001),支付成功,0950904733027003322541723618,lx65O1X8ragv7M6jI9Mlr7xxxxPwwiyx,/
2026-10-02 12:39:09,扫二维码付款,青桔,收款方备注:二维码收款,支出,4,零钱,已转账,55320523494090728354639284045065,14851054623311291095750926044047,/
2026-10-02 12:29:09,商户消费,蜜雪冰城,蜜雪冰城示例店,支出,10.8,示例银行信用卡(0001),支付成功,4057621540446636757455501814,250657595422305244621419231687,/
2026-10-02 12:03:44,商户消费,滴滴出行,先乘车后付款,支出,33,示例银行信用卡(0001),支付成功,3715151724190404259504965040,g68O4SID_g8NFu5o9l-IUzxxxxjf2Tix,/
2026-10-02 07:15:55,商户消费,滴滴出行,先乘车后付款,支出,29.6,示例银行信用卡(0001),支付成功,5503057292954751075589483138,xShD23JM5Gkl-thjRX4kOxxxxxqULxyx,/
2026-10-01 19:54:58,商户消费,示例茶饮,【堂食】_示例门店,支出,35.8,示例银行信用卡(0001),支付成功,3231228310221816294400458163,509045178057555291834781254552,/
2026-10-01 13:13:19,商户消费,美团平台商户,团购-大众点评微信小程序-32324218549575792247461087155736,支出,14,示例银行信用卡(0001),支付成功,5654236203531525961633218074,20261001131314U94641549732202850,/
2026-10-01 13:12:16,商户消费,美团平台商户,团购-大众点评微信小程序-29140156611825754100815575610251,支出,165.5,示例银行信用卡(0001),支付成功,2860953107446189481140732389,20261001131210U71674059566816751,/
2026-10-01 06:22:55,商户消费,肯德基,KFC_PRE_PLUSWX30718248205112470376432,支出,43.9,示例银行信用卡(0001),支付成功,1158504334967195250329605721,WX30718248205112470376432,/
2026-10-01 05:56:25,商户消费,滴滴出行,先乘车后付款,支出,125,示例银行信用卡(0001),支付成功,1050156215144154860331820252,SawhaluRpZ0KVtVyEqX7-Cxxxxjn7xxx,/
2026-09-29 16:38:30,商户消费,腾讯,支付订单(为1**000购买),支出,24,示例银行信用卡(0001),支付成功,3451740105537235234443380374,product_SZ_82638263062103354,/
2026-09-29 09:01:09,扫二维码付款,示例小吃店,收款方备注:二维码收款,支出,5,零钱,已转账,11071600379003023471000905430539,62564144835731960901472544225500,/
2026-09-27 22:59:08,商户消费,示例景区,示例景区,支出,192,示例银行信用卡(0001),支付成功,4859413090434644039436404431,255354230270318650323125371871,/
2026-09-27 19:51:18,转账-退款,/,转账备注:微信转账,收入,600,示例银行储蓄卡(0002),已全额退款,268230971423427428402855671302424510580,,/
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
        val snippet = "2026-10-27 15:19:46,微信红包（单发）,发给a,/,支出,0.01,零钱,支付成功,702875366606343249856721400021449540,2104259460313224851655266767005,/"
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
            2024-05-01 08:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,微信零钱,支付成功,6900642533535840722559127206,1234567890,/
            2024-05-01 12:15:30,微信红包,用户A,/,支出,¥10.00,零钱通,已送达,3084515723323615940604685713,3084515723323615940604685713,/
            2024-05-01 18:20:00,转账,张三,/,支出,¥50.00,招商银行(1234),对方已收钱,5542303430124519331514662055,5542303430124519331514662055,/
            2024-05-01 19:00:00,转账,李四,/,收入,¥100.00,/,已存入零钱,8395336561897298057812585556,8395336561897298057812585556,/
        """.trimIndent()

        val lines = csv.lines()
        val rows = CsvStatementImporter.parseStatementRows(lines)

        assertEquals(4, rows.size)

        // 验证瑞幸咖啡
        val row1 = rows[0]
        assertEquals("EXPENSE", row1.type)
        assertEquals(1500L, row1.amountCents)
        assertEquals("6900642533535840722559127206", row1.externalId)
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
            9273443455751973318055528958,MER12345,2024-05-01 10:00:00,2024-05-01 10:00:05,2024-05-01 10:00:05,手机,即时到账,星巴克,拿铁咖啡,38.00,支出,交易成功,0.00,0.00,已支出
            2722451001547286842739931858,MER99999,2024-05-01 11:00:00,2024-05-01 11:00:05,2024-05-01 11:00:05,手机,即时到账,全家便利店,三明治,12.50,支出,交易成功,0.00,0.00,已支出
        """.trimIndent()

        val lines = csv.lines()
        val rows = CsvStatementImporter.parseStatementRows(lines)

        assertEquals(2, rows.size)
        assertEquals(3800L, rows[0].amountCents)
        assertEquals("9273443455751973318055528958", rows[0].externalId)
        assertTrue(rows[0].merchant?.contains("星巴克") == true)
        assertEquals("餐饮美食", rows[0].category)

        assertEquals(1250L, rows[1].amountCents)
        assertEquals("2722451001547286842739931858", rows[1].externalId)
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
        assertEquals("702875366606343249856721400021449540", firstRow.externalId)
        assertEquals("微信支付 (零钱)", firstRow.channel)
        assertEquals("转账红包", firstRow.category)

        // 验证金额转换与商户识别 (如老乡鸡充值400元)
        val laoxiangji = rows.firstOrNull { it.merchant?.contains("老乡鸡") == true && it.amountCents == 40000L }
        assertNotNull("Should parse laoxiangji 400 transaction", laoxiangji)
        assertEquals("EXPENSE", laoxiangji?.type)
        assertEquals("餐饮美食", laoxiangji?.category)
        assertEquals("4752502191462221822405186402", laoxiangji?.externalId)
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
