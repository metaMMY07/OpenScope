# OpenScope Android 0.4.1 本地验收

日期：2026-09-23。工作目录 `D:\OpenScope`，分支 `codex/siye-android-parity`。这是本地侧载开发包，未推送或公开发布。

## 本轮变化

- 搜索页、账号页、来源筛选、热搜和内容库使用四个平台的官方图标。知乎改用 512×512 官方应用图标，避免旧 32×32 favicon 放大后的模糊。来源与文件见 [图标记录](brand-assets.md)。
- 搜索结果与本地内容库卡片采用**左侧封面、右侧标题、下方整行摘要**；卡片增加边框和轻微阴影，便于区分相邻结果。封面按原图比例缩放，图片不存在或加载失败时保留占位。
- 仅在来源确实返回对应字段时展示发布日期、播放量、点赞/赞同、收藏、投币、评论、弹幕等数据；不把本地排序分数冒充互动量。旧收藏的封面与已有字段也能显示。
- 内容库数据库从版本 1 升到 2，保存新互动字段；旧收藏和稍后状态沿用原记录。备份仍是 OpenScope 自有格式，新增字段可往返读取。

## 安装包与检查

| ABI | 本地包 | 大小 | SHA-256 |
| --- | --- | ---: | --- |
| ARM64 真机 | [OpenScope-0.4.1-arm64-v8a.apk](../artifacts/OpenScope-0.4.1-arm64-v8a.apk) | 10,746,771 bytes | `1269CF820905B97CDF957C3B61FE6B4A4632FBDA2412ADEBCB15EACCB5065020` |
| x86_64 模拟器 | [OpenScope-0.4.1-x86_64.apk](../artifacts/OpenScope-0.4.1-x86_64.apk) | 11,440,605 bytes | `3DEB2B3A2324551A11A8D36095D8287FF387FE13A7B18C5483FBE8A6CF50B8E7` |

包名 `dev.mediasearch`；versionCode 5、versionName 0.4.1、minSdk 29、targetSdk 35。ARM64 包签名校验通过，证书 SHA-256 为 `583079a20081a1bedaf8c1fbdf93e92bf3cc3544e0f7246fef7b1625d9066a02`，仍是**内部测试 debug key**，正式分发前需改用受控发布签名。

[最终 Verify 日志](../artifacts/build-0.4.1-final-left.log)显示 Debug/Release 构建、R8、56 项 JVM 单测与 lint 通过；lint 0 error / 31 warnings，耗时 1m 07s。模拟器 API 35 `emulator-5580` 覆盖安装 x86_64 Release 成功；系统报告 versionCode 5、versionName 0.4.1。覆盖安装后原有 B 站收藏仍显示标题、`播放 243.4万` 与 `已收藏`，见 [界面节点](../artifacts/release-library-041.xml)。

人工查看了模拟器 [左侧封面与卡片截图](../artifacts/library-card-left-041.png) 和 [账号页图标截图](../artifacts/account-icons-041.png)：封面已靠左，正文不被挤成窄列，知乎图标清晰。截图来自同源码 Debug 包；随后安装 Release 包并检查版本与收藏节点。未在真实账号下验收新结果卡片。一次 B 站联网搜索遇到平台验证，未继续密集重试；这不是功能或延迟实测通过的证据。

## 尚待真实账号与目标手机验证

小红书、知乎和实验性抖音的登录后搜索仍需已有账号在 App 内官方页面做端到端验收；平台挑战需要用户自行完成。搜索结果中哪些互动字段可用取决于实际响应，不保证每条均有播放/点赞/收藏。封面请求只读取对应平台的公开 HTTPS 图片域名，不携带 Cookie；图片域名、尺寸或网络状态不符时会显示占位。目标手机上的冷/热搜索延迟、内存和耗电未因本轮视觉改动重新测量；上一轮有限数据见 [0.4.0 验收](release-0.4.0.md)。
