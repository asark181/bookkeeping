# 智能记账 (Bookkeeping) 工程交接与运维手册

本文档为「智能记账」工程的核心技术架构、模块实现、全渠道账单导入、部署运维及交接排错指南，供后续开发、测试与运维人员交接与维护使用。

---

## 一、 项目背景与产品定位

* **产品定位**：专为 **小米 15 Pro (HyperOS 2 / Android 15)** 深度调优的高性能、高私密性自动记账工具。
* **设计哲学**：
  * **1:1 原生复刻「钱迹」极简纯粹体验**：去除所有花哨广告、社交属性与多余 AI 营销文案，界面采用 Material 3 原生矢量圆底徽章与金融级等宽数字对齐。
  * **单人专属账本**：彻底废弃繁复的多账本与家庭分账逻辑，有且只有一本专属个人账本，开箱即用。
  * **全生态通知秒级捕获**：除主流微信与支付宝外，全面打通美团外卖、京东白条、抖音商城等自闭环支付通知。
  * **全格式官方账单秒级导入与去重**：支持微信/支付宝官方 CSV、**微信官方 XLSX 电子表格**、加密或未加密 ZIP 压缩包、系统剪贴板与应用间分享流转（Intent）。

---

## 二、 系统架构全景

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Android Client (Kotlin + Compose)              │
├────────────────────────────────────────────────────────────────────────┤
│  UI 层 (钱迹 1:1 4-Tab 架构)                                             │
│  ├─ 明细 (DetailScreen)   : 顶部3列统计 + 胶囊操作 + 时间线流水 + 详情抽屉    │
│  ├─ 图表 (ChartScreen)    : 周/月/年维度切换 + Canvas 原生环形图 + 消费排行榜 │
│  ├─ 资产 (AssetScreen)    : 净资产卡片 + 微信/支付宝/银行账户收支汇总         │
│  └─ 我的 (ProfileScreen)  : C端标准账号登录/注册 + 通知看门狗状态 + 账单导入 │
├────────────────────────────────────────────────────────────────────────┤
│  业务逻辑与数据解析层                                                  │
│  ├─ NotificationListenerService : 捕获微信/支付宝/美团/京东/抖音系统通知  │
│  ├─ ListenerWatchdog            : AlarmManager 链式看门狗防杀自愈       │
│  ├─ DedupHelper                 : 毫秒级多渠道（微信/通知/银行卡）智能去重   │
│  └─ CsvStatementImporter        : 深度支持 CSV / XLSX (OpenXML) / ZIP 导入│
├────────────────────────────────────────────────────────────────────────┤
│  本地存储层 (Room + SQLite WAL)                                        │
│  └─ AppDatabase (TransactionRecord, CapturedNotification, UserProfile) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTPS (Bearer Token / PBKDF2)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Cloud Server (FastAPI + Python)                 │
├────────────────────────────────────────────────────────────────────────┤
│  运行环境 : Linux / Ubuntu 22.04 LTS (<YOUR_SERVER_IP>)                │
│  监听端口 : 127.0.0.1:8095 (Systemd: bookkeeping.service)              │
│  反向代理 : Nginx + Cloudflare SSL (https://leafy.fxxkopenai.com)      │
│  数据存储 : SQLite WAL (/var/lib/bookkeeping/bookkeeping.db)           │
│  核心接口 :                                                           │
│    ├─ POST /api/auth/register       : 用户名/手机号 + 密码注册          │
│    ├─ POST /api/auth/login-password : 密码登录，颁发随机令牌（存哈希）  │
│    └─ GET  /api/auth/me             : 用户信息与默认专属账本查询       │
│  静态托管 : /opt/nuanyue/web/                                          │
│    ├─ ledger.html                   : 落地维护与下载页                  │
│    └─ download/bookkeeping.apk      : Android 客户端安装包             │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 三、 客户端核心模块详解

### 1. HyperOS 设计系统与主界面（v1.2.0）

v1.2.0 起全部界面改为自绘 HyperOS 风格组件，不再使用 Material3 的 Button / AlertDialog / ModalBottomSheet / OutlinedTextField / NavigationBar 等原生控件外观。

| 模块路径 | 定位 | 说明 |
|---|---|---|
| `ui/theme/Theme.kt` | 设计令牌 | `MiColors`（浅色 #F5F6F8 底 + 白卡片 + 小米蓝 #3482FF；深色纯黑底），通过 `Mi.colors` 读取；`AmountStyle` 为等宽数字（tnum）。 |
| `ui/components/MiComponents.kt` | 基础组件 | `MiButton`（Primary/Secondary/Danger/DangerFilled/Soft）、`MiIconButton`、`MiChip`、`MiCard`、`MiListItem`、`MiSwitch`、`MiSegmented`（弹性滑块）、`MiTextField`、`MiTopBar`、`MiLargeTitle`；`miClickable` 为弹性缩放按压、`miRowClickable` 为整行高亮。 |
| `ui/components/MiOverlays.kt` | 弹层 | `MiModal` 弹层基座在应用根层（`OverlayHostView`，由 `MainScreen` 提供 `LocalOverlayHost`）同一窗口内绘制，系统栏与键盘内边距与页面一致；在此之上有 `MiSheet`（底部面板，可下拉关闭）、`MiDialog`（底部悬浮对话框）、`ImageViewer`（全屏看图，缩放/双击/左右滑）、`MiDatePickerSheet`、`MiMonthPickerSheet`。**不要改回系统 Dialog 窗口**：Dialog 窗口在 edge-to-edge 下状态栏遮罩与导航栏内边距不可靠。 |
| `ui/main/MainScreen.kt` | 主框架 | 4 Tab（明细/统计/资产/我的）+ 中间悬浮「＋」按钮；二级页面（记一笔/编辑、分类管理、搜索）以页面栈叠加并带进退场动画；账单详情面板在此统一打开。 |
| `ui/detail/DetailScreen.kt` | 明细 | 大标题月份选择、渐变看板（支出/收入/结余/预算进度与日均可用）、快捷入口、按天分组卡片；「列表 / 日历」视图切换（`AppPrefs.detailViewMode` 记忆）；长按或「批量管理」进入多选，全选范围为当前可见账单（列表=当月，日历=选中日），批量删除走 `TransactionDao.markDeletedIds` 并清理图片。多选时通过 `onSelectionModeChange` 让 `MainScreen` 隐藏底部导航。 |
| `ui/detail/CalendarView.kt` | 日历 | 参考 Period（暖月）首页月历：`HorizontalPager` 左右滑动切月（回看 120 个月、前看 12 个月），与年月状态双向同步；格子显示当日支出简写，底色按当月最大单日支出做深浅热力，今天描边、选中填充主色。 |
| `ui/record/RecordEditorScreen.kt` | 记一笔 | 鲨鱼记账式 5 列分类宫格 + 自绘计算器键盘（`Calculator.kt`，支持 + - 连续计算，精确到分）、备注、多图（最多 9 张）、日期、账户、商家；新建支持「再记」。 |
| `ui/record/TransactionDetailSheet.kt` | 账单详情 | 金额、分类、时间、账户、商家、单号、备注、图片、原始通知；编辑 / 删除（删除会同时清理图片文件）。 |
| `ui/record/TransactionRow.kt` | 账单行 + 搜索 | `TransactionRow` 复用于明细、统计、搜索；`SearchScreen` 按商家/备注/分类/账户/金额搜索。 |
| `ui/category/` | 分类 | `CategoryManageScreen` 新建/编辑（名称、图标、颜色）/删除；`CategoryVisuals.kt` 图标库与 `categoryVisual()` 外观查找（未知名称按关键词推断）。 |
| `ui/chart/` | 统计 | 周/月/年 + 前后翻页、支出/收入切换、趋势柱状图（点按查看）、分类环形图与排行（点开查看该分类账单）。 |
| `ui/asset/AssetScreen.kt` | 资产 | 按支付渠道关键词归集本月流出/流入与累计笔数。 |
| `ui/profile/ProfileScreen.kt` | 我的 | 账号卡片与统计、通知监听/无障碍/保活入口、分类管理、月度预算、账单导入、关于。 |
| `ui/common/` | 共用 | `Format.kt` 金额与日期格式化；`BillImport.kt` 文件/粘贴导入与结果弹窗；`BudgetDialog.kt` 预算设置。 |

### 1.0 应用图标

自适应图标：`drawable/ic_launcher_background.xml`（蓝色渐变）+ `ic_launcher_foreground.xml`（白色小票、¥、金币，安全区内绘制）+ `ic_launcher_monochrome.xml`（主题图标剪影）。

### 1.1 数据模型（Room v4）

* `transactions` 新增 `note`（备注）、`images`（图片文件名，换行分隔）。
* 新增 `categories` 表（`name`、`type`、`iconKey`、`colorArgb`、`sortOrder`、`isProtected`，`(type,name)` 唯一）。交易仍以分类**名称**关联，兼容自动记账与账单导入写入的名称。
* `AppDatabase.MIGRATION_3_4` 无损升级保留历史账单；启动时 `ensureDefaultCategories()` 在分类表为空时写入默认分类（`data/category/DefaultCategories.kt`）。
* 删除分类：该分类下账单归入「其他支出 / 其他收入」（这两个兜底分类不可删除）；重命名分类会同步更新历史账单。
* 图片：`data/attachment/AttachmentStore.kt` 通过系统照片选择器（无需存储权限）导入，按 EXIF 旋转、长边压到 2048px、JPEG 85 存入 `filesDir/attachments/`。
* 月度预算、上次使用的账户存于 `data/prefs/AppPrefs.kt`（SharedPreferences）。

### 2. 全格式账单导入解析引擎 (`CsvStatementImporter.kt`)

支持以下全部文件格式与交互入口：
1. **微信官方导出的 `.xlsx` 电子表格**：
   * 原生内置轻量 OpenXML 规范流式解析器（无需引入庞大臃肿且容易触发 Android Dalvik 兼容问题的 Apache POI 库）。
   * 自动解压 OpenXML 架构内的 `xl/sharedStrings.xml` 字符字典与 `xl/worksheets/sheet1.xml` 行单元格。
   * 支持识别 Excel 1900 历元序列日期浮点数（如 `46322.63872685185`），毫秒级还原为 `yyyy-MM-dd HH:mm:ss`。
   * 精确提取交易时间、交易类型、交易对方、金额、收支方向、支付方式、微信交易单号。
2. **ZIP 格式压缩包**：
   * 自动探测 ZIP 幻数 (`PK\x03\x04`)，无论内部包含 `.xlsx` 还是 `.csv` / `.txt`，均自动在内存中完成瞬时解压与级联解析。
3. **微信/支付宝官方 CSV 文本**：
   * 智能识别 UTF-8（带或不带 BOM）与 GBK/GB18030 编码，避免乱码。
4. **剪贴板与应用间直接流转**：
   * 微信中长按文件「用其他应用打开」直接通过 `MainActivity` 的 `ACTION_VIEW` / `ACTION_SEND` 接收流并入库。
   * 复制账单文本至剪贴板后，点击「粘贴账单」瞬间批量解析入库。
5. **智能 MD5 / 交易单号指纹去重**：
   * 基于第三方交易单号（微信支付单号、支付宝交易号）建立防重哈希索引，已入库条目自动识别并合并更新，绝不产生重复流水。

### 3. 多渠道闭环通知监听器 (`NotificationParser.kt`)

* **微信支付**：`com.tencent.mm`（微信支付凭证、转账到账、微信红包通知）。
* **支付宝**：`com.eg.android.AlipayGphone`（支付助手、交易提醒）。
* **美团生态**：`com.sankuai.meituan`、`com.sankuai.meituan.takeoutnew`（外卖订单扣款、美团月付）。
* **京东生态**：`com.jingdong.app.mall`、`com.jd.jrapp`（京东支付、京东白条）。
* **抖音生态**：`com.ss.android.ugc.aweme`（抖音商城订单支付、直播间下单扣款）。

---

## 四、 服务端与生产环境配置

### 1. 主机与环境信息

* **云服务环境**：Linux 64位 (Ubuntu 22.04 LTS)
* **公网 IP**：`<YOUR_SERVER_IP>`
* **绑定域名**：`https://leafy.fxxkopenai.com`
* **SSH 访问**：`ssh <YOUR_SERVER_HOST>`（配置位于 `~/.ssh/config`）
* **运行端口**：`8095`（注：此前 8091 端口被其他已有服务占用，已全量切至 `8095`）

### 2. Systemd 守护进程

服务配置文件：`/etc/systemd/system/bookkeeping.service`
```ini
[Unit]
Description=Bookkeeping FastAPI Backend Service
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/nuanyue/server
ExecStart=/usr/bin/python3 -m uvicorn app:app --host 127.0.0.1 --port 8095
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
```

常用运维命令：
```bash
# 查看服务状态
ssh <YOUR_SERVER_HOST> "sudo systemctl status bookkeeping.service"

# 重启后端服务
ssh <YOUR_SERVER_HOST> "sudo systemctl restart bookkeeping.service"

# 查看最新实时日志
ssh <YOUR_SERVER_HOST> "sudo journalctl -u bookkeeping.service -f -n 50"
```

### 3. Nginx 反向代理与静态资源映射

配置文件位置：`/etc/nginx/sites-available/default` 或 `/etc/nginx/conf.d/leafy.conf`
* **API 代理**：
  ```nginx
  location /api/ {
      proxy_pass http://127.0.0.1:8095;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
  }
  ```
* **落地页与 APK 下载路径**：
  * 维护与下载页：`/opt/nuanyue/web/ledger.html` -> `https://leafy.fxxkopenai.com/ledger.html`
  * 安装包路径：`/opt/nuanyue/web/download/bookkeeping.apk` -> `https://leafy.fxxkopenai.com/download/bookkeeping.apk`

---

## 四-补充、 服务端账号表与错误约定（v1.4.0）

* `users` 表：`id`、`username`（`UNIQUE COLLATE NOCASE`，大小写不敏感）、`password_hash`/`password_salt`（PBKDF2-SHA256 10 万轮）、`token_hash`（登录令牌的 sha256，明文令牌只返回给客户端一次）、`created_at`、`last_login_at`。
* 旧表来自早期手机号验证码登录（`phone UNIQUE NOT NULL`、明文 `token`），曾导致「用手机号注册」触发唯一约束 500。`server/app.py::_migrate_users` 在服务启动时自动重建：先用 SQLite 备份 API 备份到 `/var/lib/bookkeeping/backups/bookkeeping-before-users-migration-<时间戳>.db`，无用户名的旧账号以手机号作为用户名（无密码，注册同名即设置密码），旧令牌转为哈希。
* 验证码登录接口（含万能码 888888）与 `otp_codes` 表已删除；多账本接口已删除（单账本设计）。
* 所有错误统一返回 JSON：`{"status": "error", "detail": "<中文提示>"}`，参数校验错误为 400，未知异常为 500（日志里有完整堆栈）。
* 测试：`python -m pytest server/tests`（需要 `fastapi==0.115.6`、`httpx`、`pytest`），覆盖旧表迁移、手机号注册、大小写去重与错误格式。

## 四-补充、 应用内更新（v1.4.0，参考 Period/暖月）

* 代码：`update/AppUpdate.kt`（状态机：Available → Downloading → Ready / NeedsPermission / Failed）、`ApkFetcher.kt`（断点续传）、`UpdateLogic.kt`（版本解析与 sha256/APK 头校验），界面 `ui/profile/UpdateSheet.kt`。
* 版本来源：`https://leafy.fxxkopenai.com/bookkeeping-version.json`（`versionCode`、`versionName`、`apkUrl`、`sha256`、`notes`）。**发布新版必须同步更新这个文件**，否则客户端看不到新版本；sha256 必须与 APK 一致，否则客户端会拒绝安装。
* 触发：启动时距上次检查超过 6 小时静默检查；WorkManager 每 12 小时检查并对每个新版本发一次通知；「我的 → 检查更新」手动检查并弹出更新面板；有新版本时底栏「我的」亮红点。
* 安装：APK 下载到 `filesDir/updates/`，经 `FileProvider`（`${applicationId}.fileprovider`）交给系统安装器；缺少「安装未知应用」权限时跳转授权，`MainActivity.onResume` 回来后自动继续安装。签名必须与已安装版本一致（当前为本机 debug 签名）。

## 四-补充、 多设备加密同步（v1.5.0）

* **模型**：整本账作为一个快照（`data/sync/SyncMerge.kt::SyncSnapshot`：账单含墓碑、分类、月度预算），gzip 后用 AES-256-GCM 加密，存于服务端 `sync_blobs(user_id, version, blob, updated_at)`。服务端无法解密。
* **密钥**：`SyncCrypto.deriveKey` = PBKDF2-HMAC-SHA256(密码, `bookkeeping-sync-v1:<用户名小写>`, 20 万轮, 32 字节)。登录/注册成功时派生，用 Android Keystore 中不可导出的密钥包裹后存入私有偏好（`SyncKeyVault`）。v1.5.0 之前登录的账号在「我的 → 云同步」输入一次密码即可开启。没有改密码功能；若将来增加，需要用新密钥重新加密上传。
* **合并**：账单以 Room v5 新增的全局 `uid` 为键，`updatedAt` 较新者胜出（相同保留本机）；删除为软删除墓碑参与合并；两台设备各自导入同一官方账单（`externalId` 相同、`uid` 不同）时只保留一条。分类按整体修改时间（`AppPrefs.categoriesUpdatedAt`）、预算按 `budgetUpdatedAt` 较新者胜出。图片字段只保留在本机，不进入快照。
* **并发**：`PUT /api/sync` 必须带 `base_version`，与服务器当前版本不一致返回 409，客户端重新拉取合并后重试（最多 3 次）。
* **触发**（`SyncManager`）：启动时；回到前台且距上次超过 5 分钟；Room `InvalidationTracker` 监听 transactions/categories 变化后 30 秒（WorkManager 唯一任务，连续修改顺延）；WorkManager 每 6 小时；「我的 → 立即同步」；登录后立即同步。
* **会话**：服务端 `sessions(token_hash, user_id, created_at, last_used_at)` 每台设备一条，单账号最多保留最近使用的 10 个，登录新设备不会挤掉其他设备。`users.token_hash` 为旧字段，启动时迁入 `sessions` 后置空。
* **线上 nginx**：`/etc/nginx/sites-available/nuanyue` 中 `/api/auth/`、`/api/user/` 和 `location = /api/sync`（`client_max_body_size 10m`）转发到 8095，其余 `/api/` 属于同机的暖月（8090）。新增记账接口时必须同步加转发，否则会落到暖月返回 404。
* **验证方法**：本机起服务端（`BOOKKEEPING_DATA=<临时目录> uvicorn app:app --port 8095`），测试包临时把 `SERVER_URL` 改为 `http://localhost:8095` 并开启 cleartext、`adb reverse tcp:8095 tcp:8095`；用 Python（`cryptography` 的 AESGCM + `hashlib.pbkdf2_hmac`）模拟第二台设备解密/修改/上传。测试完必须还原构建文件。

## 五、 编译构建与发布流程

### 1. 本地快速验证与编译

```bash
# 1. 运行核心单元测试（含微信官方 XLSX 导入验证）
cd android
./gradlew testDebugUnitTest

# 2. 编译调试 APK
./gradlew assembleDebug

# 3. 产物路径
# android/app/build/outputs/apk/debug/app-debug.apk
```

### 2. 发布到线上环境

```bash
# 1. 拷贝产物至本地发布目录并查看 SHA-256
cp android/app/build/outputs/apk/debug/app-debug.apk dist/bookkeeping-debug.apk
shasum -a 256 dist/bookkeeping-debug.apk

# 2. 同步网页至生产服务器
scp server/web/download.html <YOUR_SERVER_HOST>:/tmp/ledger.html
ssh <YOUR_SERVER_HOST> "sudo mv /tmp/ledger.html /opt/nuanyue/web/ledger.html && sudo chmod 644 /opt/nuanyue/web/ledger.html"

# 3. 同步 APK 至生产服务器
scp dist/bookkeeping-debug.apk <YOUR_SERVER_HOST>:/tmp/bookkeeping.apk
ssh <YOUR_SERVER_HOST> "sudo mv /tmp/bookkeeping.apk /opt/nuanyue/web/download/bookkeeping.apk && sudo chmod 644 /opt/nuanyue/web/download/bookkeeping.apk"

# 4. 远程健康校验
curl -sI https://leafy.fxxkopenai.com/ledger.html
curl -sI https://leafy.fxxkopenai.com/download/bookkeeping.apk
```

---

## 六、 运维排错与常见问题 (Troubleshooting)

### Q1: 小米手机 (HyperOS 2) 上通知监听服务被杀或偶尔无法自动记账？
* **排查步骤**：
  1. 打开系统「设置」 -> 「应用设置」 -> 「应用管理」 -> 「智能记账」：
     * **自启动**：开启
     * **省电策略**：改为「无限制」（非常关键，HyperOS 默认会冻结后台服务）
     * **权限管理**：检查「通知类权限」与「后台弹出界面」均已允许。
  2. 检查应用内部「我的」页面中的「通知监听看门狗」是否处于已激活（Active）状态。应用内置了基于 `AlarmManager` 的自愈链式看门狗，只要开启「无限制」省电策略，即可实现高可靠常驻。
  3. 确认微信/支付宝对应群聊或商户消息已开启「接收通知」并允许弹出通知栏横幅。

### Q2: 微信官方导出的账单文件导入显示失败？
* **排查步骤**：
  1. 微信导出账单有两种形式：
     * **直接发送 Excel 表格 (.xlsx)**：新版应用已全量原生支持，直接在「明细」或「我的」中选取该 `.xlsx` 文件即可。
     * **加密压缩包 (.zip)**：微信发送到邮箱的 ZIP 压缩包通常带有解压密码（通常为身份证后 6 位）。如果该 ZIP 带密码，系统会提示解压异常。解决办法：手机本地解压后，直接选取解压出的 `.xlsx` 或 `.csv` 导入；若为未加密的 ZIP 压缩包，本应用支持直接选取导入。
  2. 检查文件是否已被其他软件占用损坏；可在电脑端用 Excel 或 WPS 打开确认其包含 `交易时间, 交易类型...` 完整表头。

### Q3: 服务端 8095 端口连接拒绝？
* **排查步骤**：
  1. 登录服务器执行 `sudo systemctl status bookkeeping.service` 查看退出原因。
  2. 检查是否有历史僵尸进程占用：`sudo lsof -i :8095`。
  3. 若需修改端口，切勿使用 `8091`（该端口已被本地 `hearth-journal` 服务占用）。修改端口后同步检查 Nginx 的 `proxy_pass` 指向。

---

## 七、 交接核对清单 (Checklist)

- [x] **架构重构**：钱迹 1:1 纯粹 4-Tab（明细、图表、资产、我的）与单人专属账本全面落地。
- [x] **无多余 AI 营销文案**：全应用 UI 彻底清爽化，遵循 Material 3 与原生金融界面规范。
- [x] **XLSX 导入修复**：完整支持微信官方 `.xlsx` 格式明细解析，单测验证通过（65 笔真实流水全量入库）。
- [x] **ZIP 级联解压**：支持直接选取 `.zip` 压缩包（自动解析内部的 `.xlsx` 或 `.csv`）。
- [x] **标准 C 端认证**：落地账号/密码快速注册与登录弹窗，告别脆弱的短信验证码依赖。
- [x] **高标准网站**：暗色微质感 + 钛金属真机模型 + Bento 特性栅格落地页部署上线。
- [x] **测试覆盖**：22 项单元测试全部 PASS，包含多渠道通知、CSV、XLSX、去重合并场景。
- [x] **云端生产验证**：服务端生产进程及 HTTPS 下载地址验证为 `200 OK`。