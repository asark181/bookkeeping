# 智能记账 · 工程经验沉淀

本文记录 v1.2.0 ~ v1.5.0 迭代中踩过的坑和验证过的做法。每条都写了**为什么**和**怎么用**，供后续开发、排错、发布时对照。架构与接口细节见 [HANDOVER.md](HANDOVER.md)。

---

## 一、线上环境：与「暖月」同机共存

| 事实 | 后果 / 做法 |
|---|---|
| 记账与暖月共用一台服务器和同一个 nginx server 块，静态根目录是暖月的 `/opt/nuanyue/web`。 | 记账只能动三个文件：`download/bookkeeping.apk`、`ledger.html`、`bookkeeping-version.json`。其余文件属于暖月，不要覆盖。 |
| nginx 只把 `/api/auth/`、`/api/user/`、`location = /api/sync` 转发到记账服务（8095）；其余 `/api/` 都转发到暖月（8090）。 | **新增记账接口必须同时加 nginx 转发**，否则请求会落到暖月，返回 404，或者拿到暖月的数据。v1.4.0 下载页读 `/api/version`，结果显示成暖月的 1.10.3；v1.5.0 的 `/api/sync` 也因此 404 过。 |
| nginx 默认 `client_max_body_size` 为 1MB。 | 同步密文快照最大 8MB，所以 `/api/sync` 单独设为 10m。新增上传类接口时同样要检查。 |
| 版本信息用静态文件 `bookkeeping-version.json`，不走 `/api/version`。 | 下载页和 App 内更新都读它。发版时三个文件必须一起更新，而且 json 里的 `sha256` 必须与 APK 一致，否则客户端拒绝安装。 |
| `deploy/deploy.sh` 写的上传路径（`/opt/bookkeeping/web`）不是线上真正对外提供下载的目录。 | 以 README「发布到线上环境」的手工流程为准。脚本若要继续用，需要先改路径。 |

**改 nginx 的固定动作**：备份到 `~/backup/nginx-*.conf` → 用精确匹配的 `location =` 插入 → `nginx -t` 通过才 `reload`，失败就恢复备份 → 公网 `curl` 验证新接口，同时确认暖月的 `/api/version` 和 `/download/nuanyue.apk` 不受影响。

## 二、发布 SOP（每次都按这个顺序）

1. **本地验证**：`./gradlew assembleDebug testDebugUnitTest`、`python -m pytest server/tests`。下载页用 `html.parser` 校验能完整解析，并在 375px 宽度下确认没有横向滚动。
2. **先演练、再上线**：服务端只要改表，就先在服务器上用 `sqlite3 .backup` 拷一份生产库到临时目录，用新的 `app.py` 导入一次（启动即迁移），查表结构和行数，然后删掉临时目录。
3. **备份**：旧 APK、`ledger.html`、`bookkeeping-version.json`、`app.py`，以及生产库的 `.backup`，统一放进 `~/backup/bookkeeping-<时间戳>/`。
4. **部署**：用 `install -m 644` 覆盖文件 → `systemctl restart bookkeeping` → 检查 `journalctl` 有没有 Traceback。
5. **公网核对**：
   - 公网下载的 APK 的 `sha256` 与本地一致；
   - `bookkeeping-version.json` 是新版本；
   - 下载页 `<title>` 是新版本；
   - 新接口在没有令牌时返回 JSON 格式的 401，而不是 404。
6. **回滚**：把 `~/backup` 里的对应文件拷回去，再重启服务。

服务器地址和 SSH 凭据只从私有密钥库读取，**不写进仓库、文档和提交信息**。

## 三、服务端数据库演进的教训

- **旧表约束会「咬人」**：最早的手机号验证码登录留下了 `phone UNIQUE NOT NULL`。后来改成用户名注册时，代码只检查 `username` 是否重复，于是用以前登录过的手机号注册会撞上唯一约束，服务端返回纯文本 500，客户端把它当 JSON 解析后报 `Value Internal of type java.lang.String cannot be converted to JSONObject`。
  → 改表时要把旧约束、旧字段的影响逐条过一遍。SQLite 不能删约束，就**重建表**（`ALTER TABLE ... RENAME` → 新表 → 逐行迁移 → `DROP` 旧表）。迁移前用 `conn.backup()` 备份；WAL 模式下直接复制文件可能不完整。
- **错误必须统一成 JSON**：FastAPI 的未捕获异常默认返回纯文本 `Internal Server Error`；参数校验错误的 `detail` 是数组。现在用三个 exception handler 统一返回 `{"status":"error","detail":"中文"}`。客户端也要防御：响应不是 JSON、`errorStream` 为空、网络异常，都要转成可读的中文提示。
- **单令牌 = 多设备互踢**：`users.token_hash` 只能存一个令牌，在第二台设备登录会让第一台失效。这个问题在做同步、用两台设备实测时才暴露出来。现在改为 `sessions` 表，每台设备一行，每个账号保留最近使用的 10 个会话。
- **下线危险的旧接口**：验证码登录接口里有万能码 888888，相当于后门。不用的接口要删掉，不能只是「客户端不调用」。

## 四、Android / Compose 的坑

| 现象 | 根因 | 做法 |
|---|---|---|
| 弹层盖不住状态栏，底部按钮被挤出屏幕。 | Compose `Dialog` 是独立窗口，在 edge-to-edge 下的系统栏内边距与遮罩不可靠。 | 所有弹层都走 `MiModal`，在应用根层的 `OverlayHostView`（同一窗口）里绘制。**不要改回 Dialog**。 |
| 编辑分类必定提示「已存在同名分类」。 | 弹层关闭动画结束后，先执行 `onSave`，再执行 `onDismiss`（后者把 `editorTarget` 置空）。`onSave` 里 `scope.launch` 的协程真正运行时，读到的已经是 null，于是走了 insert。 | 回调里需要的状态，**在 launch 之前取到局部变量**。`dismiss` 只响应第一次调用，防止退场动画期间按返回键覆盖已触发的回调。 |
| 切换 Tab 后，导入大文件中途停止，也没有结果弹窗。 | `rememberCoroutineScope` 跟着页面一起离开组合被取消。 | 跨页面的长任务用 `BookkeepingApp.mainScope`，IO 放进 `withContext(Dispatchers.IO)`，整批写库用 `db.withTransaction`。 |
| 柱状图再次点击同一根柱子无法取消选中。 | `pointerInput(key)` 的闭包捕获的是旧的 `selectedIndex`。 | 用 `rememberUpdatedState` 读取最新值。 |
| 分类宫格一打开就向下偏移半行。 | LazyGrid 首帧列表为空，只有「设置」一项；数据加载后以它为锚点保持位置。 | 等数据非空再渲染列表。 |
| 连点保存产生两笔账；编辑期间被删除的账单保存后「复活」。 | 保存没有防重入，并且用打开编辑页时的快照整行 `update`。 | 加 `saving` 标志；保存前按 id 重新读取，发现已删除就提示。 |

**Room 迁移要点**：
- `ALTER TABLE ADD COLUMN ... NOT NULL` 必须带 `DEFAULT`。实体上要同步写 `@ColumnInfo(defaultValue = "")`，否则 Room 启动时校验默认值不一致，直接崩溃。
- 需要唯一值的新列（例如 `uid`），在迁移里用 `lower(hex(randomblob(16)))` 回填，再建唯一索引。
- **验证方法**：先装旧版 APK 并写入数据 → 覆盖安装新版 → `run-as <包名> sqlite3 databases/bookkeeping.db 'PRAGMA user_version; ...'`，检查版本号、行数，以及新列有没有空值或重复。

## 五、记账识别与导入准则（v1.4.0 起）

- **先判断方向，再套规则**：「向你」「收到」「到账」「已收款」「退款」表示收入；只有标题是「微信支付 / 微信转账 / 服务通知」等服务号的微信通知才参与解析。好友聊天里出现「转账 100 元」不能记账。
- **无障碍只认结果页**：只在支付或转账结果页（按 Activity 类名或结果页专属文案判断）识别；聊天页一律忽略。去重按「金额 + 对象 + 结果文案」，窗口 2 小时。这部分规则依赖微信版本，**每次微信大版本更新后都要在真机上回归**。
- **「不计收支」和「/」直接跳过**：它们是自己账户之间的资金移动，或者是退款（原交易已经单独记过）。计入任何一方都会让统计失真。
- **查重时包含软删除的墓碑**：否则用户删掉的账单，再导入同一份账单时会「复活」。
- **分类兜底**：推断出的分类名，如果分类表里没有对应收支类型的分类，就落到「其他支出 / 其他收入」。收入单独用一套推断规则。
- **时区**：账单时间一律按 `Asia/Shanghai` 解析，不使用设备时区。

## 六、多设备同步设计要点

- **端到端加密**：密钥 = PBKDF2(密码, `bookkeeping-sync-v1:<用户名小写>`)，服务器只保存密文。代价是**忘记密码就无法解密云端数据**，而且目前没有改密码功能；将来要加的话，必须用新密钥重新加密并上传。
- **合并**：账单按全局 `uid` 比较修改时间，较新者胜出；删除以墓碑形式参与合并。同一份官方账单在两台设备各导入一次（`externalId` 相同、`uid` 不同）时只保留一条，第三台设备也不会收到两条。
- **并发**：上传时带 `base_version`，服务端发现版本不一致返回 409，客户端重新拉取、合并后重试。
- **防止自触发**：写回云端数据时用 `applyingRemote` 屏蔽 `InvalidationTracker`。由于通知是异步的，仍可能多触发一次空同步，这次同步没有改动可上传，因此不会形成循环。
- **图片不同步**：快照里不包含 `images` 字段；云端记录写回本机时保留本机原有的图片。

## 七、应用内更新

- 流程：读取 `bookkeeping-version.json` → 断点续传下载到 `filesDir/updates/` → 校验 APK 文件头和 `sha256` → 通过 FileProvider 调起系统安装器。缺少「安装未知应用」权限时跳转授权，`onResume` 回来后自动继续安装。
- **签名风险**：目前发布的是 debug 包，签名来自构建机上的 `~/.android/debug.keystore`。**换电脑、或这个文件丢失后，新包无法覆盖安装旧包**，应用内更新也会失败。建议尽快生成正式的 release keystore（不入库，存进私有密钥库），并一次性迁移签名。
- 端到端验证方法：用同一份代码临时把 `versionCode` 改小后构建、安装到模拟器（`adb install -r -d`）→ 点「检查更新」→ 下载 → 授权 → 系统安装 → 用 `dumpsys package` 确认版本号；结束后还原构建文件。

## 八、测试与验证方法

- **模拟器**：`emulator -avd <名称> -no-window`；`adb shell uiautomator dump` 找到文字所在位置后点击（脚本思路：从 dump 里按 `text=` 取 bounds，点中心点），比按坐标点稳定。截图用 `adb exec-out screencap -p`。
- **本地服务端联调**：
  - 本机运行 `BOOKKEEPING_DATA=<临时目录> uvicorn app:app --port 8095`；
  - 测试包临时把 `SERVER_URL` 改成 `http://localhost:8095`，并加 `usesCleartextTraffic`；
  - `adb reverse tcp:8095 tcp:8095`；
  - **测试完立即 `git checkout` 还原构建文件和 Manifest**，并确认还原成功。
- **第二设备模拟**：用 Python（`hashlib.pbkdf2_hmac` + `cryptography` 的 AESGCM）派生同一把密钥，解密快照、修改后上传，可以同时验证跨平台加密格式和冲突处理。
- **不在生产环境创建测试账号**：注册、登录类验证一律用本地服务端；生产环境只做不产生数据的检查，例如参数校验错误、无令牌访问、已下线接口返回 404。

## 九、安全与脱敏

- **测试数据禁止使用真实账单**：曾经直接提交过真实的微信账单（交易单号、银行卡尾号、消费地点、亲友转账），而仓库是公开的。新增样本必须先过 `tools/anonymize_bill_fixture.py`。真实名称的映射放在 `tools/fixture_names.local.json`（git 忽略），脱敏后用 grep 确认没有残留再提交。
- **不入库的内容**：服务器 IP、SSH 用户、私钥、密钥库路径、keystore、`local.properties`、IDE 配置（`.idea/`）、`dist/` 安装包。公开域名是对外下载地址，可以保留。
- **App 侧**：`allowBackup=false`（数据库和登录令牌不进系统备份）；接收器 `exported=false`；分享导入需要用户确认，避免外部应用静默往账本写数据。

## 十、多代理协作的经验

- **先只读审查，再按文件划分修复**：先让一个只读代理全面审查并输出带「文件:行号」的问题清单；修复时按文件集合拆分，交给 worktree 隔离的代理处理（例如解析、服务、DAO 归它），主会话负责 UI、数据层和清单。两边改的文件不重叠，最后 `git merge` 没有冲突。
- 子代理的汇报只是线索：合并后由主会话重新跑全量测试并在模拟器上实测，确认后再写进提交说明。
