# 智能记账 (Bookkeeping) · v1.5.0 HyperOS 设计版

基于 Android 原生现代化架构（Kotlin 2.0 + Jetpack Compose + Room + Material Design 3），专为 **小米 15 Pro (HyperOS 2 / Android 15)** 深度调优的极简高性能自动记账应用。

1:1 复刻「钱迹」纯粹记账哲学：**零广告、零浮夸 AI 营销文案与插画、单人专属个人账本**，提供全生态自闭环支付感知与全格式（CSV / XLSX / ZIP）账单极速导入对账。

---

## 🌟 核心特性

1. **v1.2.0 HyperOS 全新设计**（参考钱迹、鲨鱼记账）：
   * 全部自绘组件替换安卓原生按钮/弹窗/输入框：大标题、大圆角卡片、柔和渐变、弹性按压动效、底部悬浮对话框，完整深色模式。
   * **记一笔**：底部中间悬浮「＋」进入，5 列分类宫格 + 自绘计算器键盘（支持 + - 连续计算）、日期、账户、商家、「再记一笔」。
   * **自定义分类**：支出/收入分类可新建（选图标、选颜色）、编辑、删除；删除后历史账单自动归入「其他」。
   * **备注与图片**：每笔账可写备注、附最多 9 张图片（小票/发票），详情页全屏查看、缩放。
   * **明细**：渐变月度看板 + 月度预算进度与日均可用、按天分组卡片、全局搜索。
   * **日历视图（v1.3.0）**：明细页「列表 / 日历」切换并记住选择；月历格子按当日支出深浅着色，左右滑动切月，点选某天看当天账单。
   * **多设备加密同步（v1.5.0）**：同一账号的多台手机自动同步账单、备注、分类与预算，端到端加密（密钥由密码在本机派生，服务器只存密文），冲突按修改时间合并；图片暂只保存在本机。
   * **应用内更新（v1.4.0）**：「我的 → 检查更新」应用内下载、校验 sha256 并调起安装，无需打开网页；后台每 12 小时检查一次。
   * **批量删除（v1.3.0）**：长按账单或点「批量管理」进入多选，支持全选（列表为当月、日历为当天）后一次删除。
   * **统计**：周/月/年翻页、支出/收入切换、趋势柱状图、分类环形图与可展开排行。
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
│       │   ├── data/                    # Room 持久化（账单、分类、迁移）、图片附件、偏好设置
│       │   ├── parser/                  # 核心解析器 (CsvStatementImporter, NotificationParser)
│       │   ├── service/                 # NotificationListenerService 监听器与 Watchdog 看门狗
│       │   └── ui/                      # HyperOS 风格界面
│       │       ├── theme/ components/       # 设计令牌、自绘组件与弹层宿主
│       │       ├── main/                    # 4 Tab + 中间「＋」主框架与页面栈
│       │       ├── detail/ chart/ asset/ profile/  # 四个主页面
│       │       ├── record/                  # 记一笔键盘、账单详情、搜索
│       │       ├── category/                # 分类管理与图标库
│       │       └── common/                  # 格式化、账单导入、预算弹窗
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

### 3. 发布到线上环境（生产服务器，nginx 根目录 `/opt/nuanyue/web`）
线上同机还托管「暖阅」应用：`/api/version` 和 `/download/` 默认文件名都属于暖阅，**不要覆盖其文件**。记账只涉及以下三个文件：

| 本地 | 线上 |
|---|---|
| `dist/bookkeeping-vX.Y.Z.apk` | `/opt/nuanyue/web/download/bookkeeping.apk` |
| `server/web/download.html` | `/opt/nuanyue/web/ledger.html` |
| 版本信息 JSON（versionCode / versionName / size / sha256 / apkUrl / notes） | `/opt/nuanyue/web/bookkeeping-version.json`（下载页与 App 内更新都读取它） |
| `server/app.py` | `/opt/bookkeeping/app/app.py`（`sudo systemctl restart bookkeeping`） |

nginx 只把 `/api/auth/`、`/api/user/`、`/api/sync` 转发给记账服务（8095），新增接口需同步改 nginx。覆盖前先把旧 APK 与 ledger.html 备份到 `~/backup/bookkeeping-<时间戳>/`。服务器地址与 SSH 凭据从私有密钥库获取，**不要写进仓库**。

---

## 🔗 在线服务与重要链接

* 📱 **应用下载与落地主页**：[https://leafy.fxxkopenai.com/ledger.html](https://leafy.fxxkopenai.com/ledger.html)
* 📦 **APK 直链下载 (v1.5.0)**：[https://leafy.fxxkopenai.com/download/bookkeeping.apk](https://leafy.fxxkopenai.com/download/bookkeeping.apk)
* 📖 **工程交接与运维排错手册**：[docs/HANDOVER.md](docs/HANDOVER.md)
* 🧭 **工程经验沉淀（踩坑与验证方法）**：[docs/ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md)
* 📑 **底层调研与架构设计文档**：[docs/RESEARCH.md](docs/RESEARCH.md)
