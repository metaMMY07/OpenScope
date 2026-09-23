# OpenScope Android 0.4.0 本地验收与 SiYe 功能对照

日期：2026-09-23。工作分支 `codex/siye-android-parity`，基于 `7e94ffccd55dd547447dd89e3c17cc7a2f34c873`。这是本地构建记录，尚未推送或发布。

## 安装包与实测

- ARM64：[OpenScope-0.4.0-arm64-v8a.apk](../artifacts/OpenScope-0.4.0-arm64-v8a.apk)，10,632,219 bytes；SHA-256 `E5ECB25DE88CD515C41EAB16C7DAF0853951215399C69E9A258599419754E29D`。
- x86_64：[OpenScope-0.4.0-x86_64.apk](../artifacts/OpenScope-0.4.0-x86_64.apk)，11,326,053 bytes，供模拟器验收。
- 包名 `dev.mediasearch`，versionCode 4，versionName 0.4.0，minSdk 29。APK 签名校验通过；证书 SHA-256 `583079a20081a1bedaf8c1fbdf93e92bf3cc3544e0f7246fef7b1625d9066a02`，仍为内部测试 debug key。正式分发应使用私有发布签名。
- [最终 Verify 日志](../artifacts/build-0.4-delivery-final.log)：Debug、Release/R8、54 项 JVM 单测与 lint 通过；lint 0 error / 31 warnings；耗时 2m 02s。备份上限统一为 16 MB；新增满额内容库和历史记录的备份往返测试。
- API 35 `emulator-5580` 覆盖安装 x86_64 Release 成功；一次 ADB install 实测 1457 ms。最终包在模拟器重启后安装并冷启动，`am start -W` TotalTime 2358 ms；同轮此前一次强停后样本为 987 ms。这些是模拟器单次样本，不等于真机安装时间或可感知首屏时间，也未达到冷启动 <0.5s 目标。覆盖安装后原有收藏和稍后状态仍在，[界面节点证据](../artifacts/final-lib2.xml)。
- 实际 Bilibili 搜索 `Android` 一次返回 20 条，网络搜索日志为 1300 ms；每来源界面初始显示 3 条，更多优先展开已加载结果。此单次样本没有达到热搜索 <1s 目标，不代表延迟分布。[日志](../artifacts/search-0.4-metrics.txt)。

## 与主项目 SiYe 的功能对照

参考 [SiYe 源码](https://github.com/KellenGO/SiYe) `31ccca439875235f6c2ec4e1d50d6f47f9cd9a36` 与其 [更新记录](https://github.com/KellenGO/SiYe/blob/master/CHANGELOG.md)。Android 采用原生 Material You 界面；不会运行桌面程序或浏览器扩展。

| SiYe 功能方向 | OpenScope 0.4.0 状态 |
| --- | --- |
| 收藏、稍后再看、文件夹、备注、历史 | 本机 SQLite；收藏和稍后状态独立；最多 500 个库条目和 1000 条历史。实际收藏同一条 B 站结果并加入稍后，再覆盖安装 Release，数据仍在。[界面](../artifacts/release04.png) |
| 备份、恢复、CSV/Markdown 导出、链接复制 | 已实现 Android 系统文件选择器，不要求文件访问权限。实际导出 [JSON 样本](../artifacts/library-0.4-export-smoke.json) 成功；合并导入由独立数据库的 Android 仪器测试验证。当前 JSON 为 OpenScope 自有 version 1 格式，不能直接当作 SiYe 桌面备份导入。 |
| 结果排序、关键词筛选、去重、选择导出 | 已实现已加载结果的本地相关性/最新/互动量排序、筛选和保守去重；不声称对平台全部内容排序。互动数仅用平台返回且能解析的字段。 |
| 分平台继续搜索、刷新、首页与结果切换 | 已实现；每来源默认 3 条，更多按 3 条追加；刷新仅影响指定来源；首页切换保留上次结果与滚动状态。 |
| 热搜 | Bilibili、知乎、抖音公开接口在模拟器分别返回 20、10、20 条；小红书暂无可用公开热搜。失败后不自动密集重试。 |
| 平台收藏同步 | Bilibili 用户主动触发的只读导入，限制 100 项、10 页，取消保留已保存页；实际已登录账号尚未验收。知乎/小红书/抖音平台收藏同步未实现，也没有桌面版全量/增量断点能力。 |
| 诊断、帮助、外观 | 已有本地诊断预览与手动复制、使用说明、系统/浅色/深色模式和预设主题色。诊断不含搜索词、Cookie 或笔记内容。 |

## 抖音试验与平台边界

抖音作为**默认关闭的实验来源**接入。取数路径是在应用内官方 WebView 页面读取可见搜索卡片，不移植私有签名，也不在后台自动破解验证码。模拟器实际打开官方搜索页，页面显示同机可操作的扫码、短信验证码和密码登录表单；未输入真实账号。[本机探针结果](../artifacts/parity-0.4-live-2.txt)显示抖音搜索约 12,494 ms 后返回 `LOGIN_REQUIRED`。已登录后的结果卡片、分页、风控稳定性尚未证明可行，不能视作正式搜索源。Bilibili 可公开搜索；知乎和小红书仍需要用户用已有账号在官方页面完成真实登录验证。小红书继续使用官方页内结果读取路线；其登录后端到端搜索仍待验证。

真实账号测试优先顺序：在目标手机 App 内登录三个原定平台，确认官方认证状态与搜索、更多；强停重启及切换蜂窝/Wi-Fi 后复测。随后自愿试验抖音同机登录、结果读取与分页，记录挑战/限流。最后测目标真机安装、冷/热延迟分布、内存和耗电。登录失效时通过账号页重新打开官方登录页；遇到挑战由用户在官方页面完成，应用不自动绕过。

## 运行与交接

绝对目录 `D:\OpenScope`。复验：`pwsh -File .\scripts\build-local.ps1 -Target Verify`。本轮还运行了隔离 SQLite 仪器探针，证明收藏/稍后独立、历史、重启持久化、备份合并与错误文件回滚；见 [探针输出](../artifacts/parity-0.4-live-2.txt)。本地 `artifacts/` 存放 APK 与验收证据。禁止递归删除或运行 Gradle clean。

下一轮优先验证真实账号和目标真机；未经验证不得把抖音、小红书或 B 站平台收藏同步称为端到端成功。仓库代码与 APK 若要发布，需先处理测试签名和更新公开发布说明。
