# 记账

个人用的安卓记账 App。读微信、支付宝等的支付通知自动记一笔，也能导入官方导出的账单文件，按交易单号去重。

只有一本账，没有多人账本、广告和理财推荐。

## 功能

- 手动记账：分类宫格加计算器键盘，可附备注和最多 9 张图片
- 自动记账：监听支付通知，支持微信、支付宝、云闪付、美团、京东、抖音
- 导入账单：微信 `.xlsx`、支付宝 `.csv`、压缩包，也可以从微信「用其他应用打开」直接导入；重复的流水会跳过
- 明细支持列表和日历两种看法，可搜索、批量删除
- 统计：按周、月、年看趋势和分类占比
- 月度预算，自定义分类
- 可选账号同步，端到端加密，冲突按修改时间合并；图片暂不同步
- 应用内更新，深色模式

自动记账依赖通知使用权，在小米 HyperOS 上还要允许自启动、关掉省电限制，详见 [docs/RESEARCH.md](docs/RESEARCH.md)。

## 构建

需要 JDK 17 和 Android SDK 35。

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

同步服务是 FastAPI + SQLite：

```bash
cd server
pip install -r requirements.txt pytest httpx
python -m pytest -q tests
```

站点地址写在 `deploy/private/site.env`，不在仓库里；没有这个文件也能编译，只是同步和应用内更新用不了。

## 目录

```
android/   Kotlin + Jetpack Compose + Room
  parser/    通知解析、账单文件导入
  service/   通知监听服务
server/    同步服务（:8095）和下载页
deploy/    部署脚本、systemd、备份 cron
docs/      交接文档、调研笔记
```

## 部署

```bash
deploy/deploy.sh <ssh-key> dist/bookkeeping-vX.Y.Z.apk
```

服务器和暖月、吃了吗共用一个 nginx 站点，`/api/version` 和 `/download/` 下的默认文件名属于暖月，别覆盖。详见 [docs/HANDOVER.md](docs/HANDOVER.md)。

## 文档

- [docs/HANDOVER.md](docs/HANDOVER.md)：架构、数据模型、服务端和发布流程
- [docs/ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md)：踩过的坑和验证方法
- [docs/RESEARCH.md](docs/RESEARCH.md)：HyperOS 通知监听和保活调研
