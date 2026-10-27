# 1:1 原生复刻「钱迹」极简纯粹风与 C 端标准产品落地计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1:1 原生复刻「钱迹」极简纯粹 Android 记账体验（4-Tab导航、收支月度大看板、分类环形统计图与消费排行、资产账户、标准 C 端账号注册登录），并打造顶尖高质感独立发布官网。

**Architecture:** Kotlin 2.0 + Jetpack Compose + Material 3 原生 Canvas 渲染，解耦为 4 大核心 Tab（明细、图表、资产、我的）；服务端重构为标准账号密码哈希注册鉴权与单账本零知识密文同步；官网采用暗夜极光微光与 Bento 特性格局。

**Tech Stack:** Kotlin 2.0, Jetpack Compose (BOM 2024.12.01), Room 2.6.1, FastAPI (Python 3.12), SQLite (WAL), HTML5/CSS3 (Vanilla Dark Canvas)

---

### Task 1: 服务端标准账号注册/登录与单账本体系
**Files:**
- Modify: `server/app.py`
- Modify: `deploy/deploy.sh`

- [ ] **Step 1: 编写标准注册、登录与用户信息接口**
  - 在 `server/app.py` 增加 `RegisterPayload` 与 `LoginPasswordPayload`。
  - 使用 `hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 100000)` 进行密码安全加盐哈希。
  - `POST /api/auth/register`：注册用户，自动创建唯一个人账本，签发 Token。
  - `POST /api/auth/login-password`：校验密码，签发 Token。
  - `GET /api/auth/me`：根据 Bearer Token 返回用户信息与专属账本。
- [ ] **Step 2: 验证并部署服务端到 Oracle Cloud**
  - 运行 `python3 -m py_compile server/app.py` 确保语法通过。
  - 部署到线上服务器并在 8095 端口热重启。
  - 使用 curl 验证注册与登录接口。
- [ ] **Step 3: 提交更改**
  - `git commit -m "feat: 服务端落地标准账号密码注册登录与单账本体系"`

---

### Task 2: Android 客户端网络与账号认证层（AccountManager）
**Files:**
- Modify: `android/app/src/main/java/com/bookkeeping/app/data/auth/AccountManager.kt`

- [ ] **Step 1: 实现标准账号注册与登录网络调用**
  - 增加 `register(username, password): Result<UserProfile>`。
  - 增加 `loginWithPassword(username, password): Result<UserProfile>`。
  - 本地 SharedPreferences 存储 `token`, `username`, `uid`。
  - 增加单一账本标志位。
- [ ] **Step 2: 提交更改**
  - `git commit -m "feat: 客户端支持标准用户名密码注册与登录"`

---

### Task 3: 底部 4-Tab 导航容器与主题规范（MainScreen）
**Files:**
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/main/MainScreen.kt`
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/main/NavigationItem.kt`
- Modify: `android/app/src/main/java/com/bookkeeping/app/MainActivity.kt`

- [ ] **Step 1: 定义 4-Tab 导航项目与图标**
  - `明细`：`Icons.Default.ReceiptLong`
  - `图表`：`Icons.Default.PieChart`
  - `资产`：`Icons.Default.AccountBalanceWallet`
  - `我的`：`Icons.Default.Person`
- [ ] **Step 2: 创建主屏幕容器 `MainScreen`**
  - 使用 Compose `Scaffold` + `NavigationBar` + `NavigationBarItem`。
  - 维护选中 Tab 状态，支持平滑切换。
  - 连接 `MainActivity.kt` 渲染 `MainScreen`。
- [ ] **Step 3: 提交更改**
  - `git commit -m "feat: 搭建钱迹标准底部4-Tab导航架构"`

---

### Task 4: Tab 1【明细】页面：1:1 原生钱迹极简看板与分组流水
**Files:**
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/detail/DetailScreen.kt`

- [ ] **Step 1: 顶部收支月度资产看板**
  - 年月选择器（`2026年10月 ▾`）。
  - 三列严格对齐大字：本月支出、本月收入、本月结余。
  - 监听状态微标。
- [ ] **Step 2: 快捷操作与按天分组流水列表**
  - 快捷胶囊：`📋 粘贴账单`、`📁 导入文件`、`➕ 记一笔`。
  - 列表按日期倒序分组，头部展示 `10月27日 星期二` 及当日小计。
  - 列表项：原生矢量分类图标、真实商家排版、等宽金额、已对账蓝标。
- [ ] **Step 3: 流水详情底栏与删除/修改**
  - 点击流水弹出底部操作面板。
- [ ] **Step 4: 提交更改**
  - `git commit -m "feat: 1:1原生复刻钱迹明细流水与收支大看板"`

---

### Task 5: Tab 2【图表】页面：多维专业统计报表（Canvas 环形图与排行榜）
**Files:**
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/chart/ChartScreen.kt`
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/chart/DonutChart.kt`

- [ ] **Step 1: 实现 Canvas 环形占比图（DonutChart）**
  - 使用 Compose `Canvas` + `drawArc` 绘制抗锯齿平滑环形图。
  - 中心展示总支出大字与日均消费。
- [ ] **Step 2: 实现分类消费排行榜**
  - 按分类汇总金额倒序排列。
  - 序号、分类名、笔数、金额、彩色占比百分比进度条。
- [ ] **Step 3: 实现每日支出趋势柱状图**
  - 绘制当月 1~31 日消费波动柱状走势。
- [ ] **Step 4: 提交更改**
  - `git commit -m "feat: 落地钱迹级多维环形统计图表与消费排行榜"`

---

### Task 6: Tab 3【资产】页面：账户分类与余额流转
**Files:**
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/asset/AssetScreen.kt`

- [ ] **Step 1: 净资产总额卡片**
  - 计算展示：总资产、总负债、净资产。
- [ ] **Step 2: 账户列表分组**
  - 网络支付（微信零钱、支付宝）、储蓄卡、信用卡/白条。
  - 展示各账户当前余额与笔数。
- [ ] **Step 3: 提交更改**
  - `git commit -m "feat: 落地资产账户管理与资金流转看板"`

---

### Task 7: Tab 4【我的】页面：标准 C 端注册登录与单账本中心
**Files:**
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/profile/ProfileScreen.kt`
- Create: `android/app/src/main/java/com/bookkeeping/app/ui/profile/AuthModalDialog.kt`

- [ ] **Step 1: 打造标准 C 端注册与登录弹窗**
  - 切换「登录」与「注册新账号」两个 Tab。
  - 账号与密码输入框，立即注册登录。
- [ ] **Step 2: 个人中心与功能列表**
  - 用户头像与信息卡片，单人专属个人账本显示。
  - 云端备份同步触发、自动记账保活引导、账单导入入口。
- [ ] **Step 3: 提交更改**
  - `git commit -m "feat: 落地标准C端注册登录与个人中心"`

---

### Task 8: 高标准独立发布官网与下载落地页（`ledger.html`）
**Files:**
- Modify: `server/web/download.html`

- [ ] **Step 1: 暗黑极光与高质感 Hero 设计**
  - 深空背景（`#090D16`）+ 极光微光渐变。
  - 高拟真手机界面原型 Mockup 展示（呈现钱迹流水与环形图表）。
- [ ] **Step 2: Bento 功能网格与下载指引卡片**
  - 毫秒级闭环自动记账、钱迹级专业图表、全场景账单对账、端到端隐私加密。
  - 突出版本号、SHA256、文件大小、下载按钮、微信防拦截提示。
- [ ] **Step 3: 部署与在线验证**
  - 同步到服务器 `/opt/nuanyue/web/ledger.html` 并验证。
- [ ] **Step 4: 提交更改**
  - `git commit -m "feat: 重构顶尖质感独立发布官网与下载落地页"`

---

### Task 9: 全量编译构建、单元测试、打包与真机部署
**Files:**
- Modify: `android/app/build.gradle.kts` (versionCode = 6, versionName = "1.1.0")

- [ ] **Step 1: 运行全量单元测试**
  - `cd android && ./gradlew testDebugUnitTest` 确保 100% 通过。
- [ ] **Step 2: 编译打包 Debug APK**
  - `cd android && ./gradlew assembleDebug`
- [ ] **Step 3: 上传部署至 Oracle Cloud**
  - 上传至 `/opt/nuanyue/web/download/bookkeeping.apk`。
  - 验证 curl 状态码 200。
- [ ] **Step 4: 提交更改**
  - `git commit -m "build: 发布1:1钱迹极简重构版v1.1.0"`
