# 智能记账 (Bookkeeping) · 钱迹 1:1 纯粹质感重构版

基于 Android 原生现代化架构（Kotlin 2.0 + Jetpack Compose + Room + Material Design 3），专为 **小米 15 Pro (HyperOS 2 / Android 15)** 深度调优的极简高性能自动记账应用。

1:1 复刻「钱迹」纯粹记账哲学：**零广告、零浮夸 AI 营销文案与插画、单人专属个人账本**，提供全生态自闭环支付感知与全格式（CSV / XLSX / ZIP）账单极速导入对账。

---

## 🌟 核心特性

1. **钱迹 1:1 纯粹 4-Tab 原生架构**：
   * **明细 (Transactions)**：年月快速切换、3 列金融指标（支出/收入/结余）、按天时间线分组、Material 矢量分类图标与等宽数字对齐。
   * **图表 (Charts)**：Compose 原生 Canvas 硬件加速绘制动态环形统计图，周/月/年多维度无感切换，分类排行榜平滑彩色进度条。
   * **资产 (Assets)**：净资产总览、微信/支付宝/银行账户分组汇总与当月支出统计。
   * **我的 (Profile)**：C 端标准账号密码登录/注册，通知常驻守护状态与全能导入入口。
2. **单人专属账本**：
   * 彻底摒弃复杂的多人分账与家庭共账逻辑，有且只有一本专属个人账本，开箱即记。
3. **全闭环生态通知感知**：
   * 微信支付、支付宝支付毫秒级捕获。
   * 美团外卖、京东白条、抖音商城等不走微信/支付宝的自有闭环支付全量支持。
4. **全能账单导入与去重引擎**：
   * **微信官方导出的 `.xlsx` 电子表格**：原生轻量 OpenXML 解析，自动还原 Excel 日期浮点历元时间。
   * **ZIP 压缩包**：自动探测并级联解压内部 `.xlsx` 或 `.csv`。
   * **剪贴板识别与文本一键粘贴**：快速补录历史账单。
   * **微信内应用间直接流转**：通过系统 Share Intent 接收账单文件并秒级入库。
   * **智能 MD5 / 交易单号去重**：已记账流水自动识别，绝不产生重复记录。
5. **C 端标准账号与端侧密文同步**：
   * 标准账号/手机号 + 密码注册与登录，PBKDF2 加盐哈希，保障资产隐私完全自主可控。

---

## 📂 项目结构全景

```
.
├── android/                             # Android 客户端工程
│   └── app/src/
│       ├── main/java/com/bookkeeping/app/
│       │   ├── MainActivity.kt          # 入口 Activity，支持 ACTION_VIEW/SEND 流水接收
│       │   ├── data/                    # Room 本地持久化 (TransactionRecord, AppDatabase)
│       │   ├── parser/                  # 核心解析器 (CsvStatementImporter, NotificationParser)
│       │   ├── service/                 # NotificationListenerService 监听器与 Watchdog 看门狗
│       │   └── ui/                      # 钱迹 1:1 界面实现
│       │       ├── main/                # MainScreen (4-Tab 导航) 与 AuthModalDialog
│       │       ├── detail/              # DetailScreen (明细流水时间线)
│       │       ├── chart/               # ChartScreen 与 DonutChart (Canvas 原生图表)
│       │       ├── asset/               # AssetScreen (资产账户面板)
│       │       └── profile/             # ProfileScreen (个人中心与设置)
│       └── test/                        # 单元测试 (包含 XLSX 真实用户账单用例)
├── server/                              # 云端后端服务与 Web
│   ├── app.py                           # FastAPI 后端服务 (端口 :8095)
│   └── web/                             # 落地维护与下载页 (ledger.html)
├── deploy/                              # 一键部署脚本与 systemd 服务配置
├── dist/                                # 最新发布的安装包 (bookkeeping-debug.apk)
└── docs/                                # 核心技术方案与运维交接文档
    ├── HANDOVER.md                      # 完整工程交接与运维手册
    └── RESEARCH.md                      # 小米 HyperOS 2 适配与无障碍调研报告
```

---

## 🚀 快速上手与常用命令

### 1. 运行核心单元测试
```bash
cd android
./gradlew testDebugUnitTest
```

### 2. 编译调试 APK
```bash
cd android
./gradlew assembleDebug
```
产物位置：`android/app/build/outputs/apk/debug/app-debug.apk`

### 3. 一键部署至线上环境
```bash
# 拷贝产物至 dist 并部署
cp android/app/build/outputs/apk/debug/app-debug.apk dist/bookkeeping-debug.apk
scp dist/bookkeeping-debug.apk <YOUR_SERVER_HOST>:/tmp/bookkeeping.apk
ssh <YOUR_SERVER_HOST> "sudo mv /tmp/bookkeeping.apk /opt/nuanyue/web/download/bookkeeping.apk && sudo chmod 644 /opt/nuanyue/web/download/bookkeeping.apk"
```

---

## 🔗 在线服务与重要链接

* 📱 **应用下载与落地主页**：[https://leafy.fxxkopenai.com/ledger.html](https://leafy.fxxkopenai.com/ledger.html)
* 📦 **APK 直链下载 (v1.1.0)**：[https://leafy.fxxkopenai.com/download/bookkeeping.apk](https://leafy.fxxkopenai.com/download/bookkeeping.apk)
* 📖 **工程交接与运维排错手册**：[docs/HANDOVER.md](docs/HANDOVER.md)
* 📑 **底层调研与架构设计文档**：[docs/RESEARCH.md](docs/RESEARCH.md)
