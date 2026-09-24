# OpenScope

> 0.4.10 已发布。内置平台网页已移除顶部工具条和刷新悬浮按钮；视频全屏使用 Android 系统返回手势退出。见 [0.4.10 验收](docs/release-0.4.10.md)。

**面向 Android 的多平台内容搜索客户端。**输入一个关键词，可聚合浏览 Bilibili、知乎和小红书的公开内容；抖音作为可选实验来源。

[下载最新 APK](https://github.com/metaMMY07/OpenScope/releases/latest) · [查看所有版本](https://github.com/metaMMY07/OpenScope/releases) · [查看 0.4.10 验收记录](docs/release-0.4.10.md)

![GitHub Release](https://img.shields.io/github/v/release/metaMMY07/OpenScope?display_name=tag) ![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white) ![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)

## 界面预览

| 搜索 | 设置 | 账号 |
| --- | --- | --- |
| ![OpenScope 搜索页](docs/images/openscope-search.jpg) | ![OpenScope 设置页](docs/images/openscope-settings.jpg) | ![OpenScope 账号页](docs/images/openscope-accounts.jpg) |

## 主要功能

- 同一关键词并行查看多个平台的结果，每个平台先展示 3 条，点击“更多”再展开。
- 结果卡片显示可取得的封面、时间、播放量、点赞、收藏和评论数据；平台未提供的字段会留空。
- 本地内容库支持收藏、稍后再看、历史记录、筛选、排序和数据备份。
- Material You 界面，支持调整主题色。
- 官方内容网页可切换手机页面与平板页面；两种模式都使用电脑网页功能。手机模式适配 B 站、知乎和抖音的页面宽度与信息布局，抖音视频详情页支持全屏横屏播放；平台网页仍在 App 内打开并保留本机会话。
- 使用 App 内官方网页完成平台登录，会话留在本机；不需要 PC、Termux 或 root，也没有云端代抓服务。

## 下载和安装

从 [GitHub Releases](https://github.com/metaMMY07/OpenScope/releases/latest) 下载已发布版本。ARM64 包 `OpenScope-0.4.10-arm64-v8a.apk` 用于大多数 Android 手机，安装包约 10.8 MB；最低支持 Android 10（API 29）。Android 会要求允许从当前来源安装 APK。

当前 APK 使用内部测试签名，适合侧载测试；若未来签名密钥更换，可能需要先卸载旧版本才能安装新版。x86_64 包仅用于模拟器。

首次使用时，打开“账号”页，选择平台并在官方网页登录；回到“搜索”页，输入关键词并勾选需要的来源。各平台的登录要求不同，已有平台账号不代表 App 已确认登录成功。

## 平台状态

| 平台 | 当前情况 |
| --- | --- |
| Bilibili | 原生搜索适配已接入；官方网页手机模式已适配视频页滚动和全屏播放。 |
| 知乎 | App 内官方登录入口已接入；真实账号搜索与目标手机会话恢复仍待验证。 |
| 小红书 | 使用官方网页会话与页面内容读取；真实账号下的搜索和分页仍待完整验证。 |
| 抖音 | 默认关闭的实验来源。搜索相关性和可用性仍不稳定；官方网页手机模式已适配精选列表、视频详情页和横屏全屏。 |

遇到验证码时，请在平台官方页面自行完成。频率限制、页面改版和网络差异可能导致暂时无结果。BewlyBewly 浏览器扩展没有内置到 APK；它依赖扩展后台 API，不能直接注入 Android WebView，详见 [兼容性评估](docs/bewlybewly-feasibility.md)。

## 隐私与项目来源

平台登录在官方网页中进行，登录信息保留在设备上。本项目没有云端采集服务。请遵守各平台条款和适用法律，避免高频或大规模请求。

OpenScope 是独立维护的 Android 原生客户端，参考了 [SiYe](https://github.com/KellenGO/SiYe) 与 [MediaCrawler](https://github.com/KellenGO/MediaCrawler) 的项目方向。仓库附带的 MediaCrawler 相关许可为**非商业学习许可证 1.1**；使用或修改前请阅读 [许可证文本](app/src/main/assets/licenses/MediaCrawler-LICENSE.txt)。此仓库未授予商业使用权。官方图标来源记录见 [品牌素材说明](docs/brand-assets.md)。

## 开发

需要 JDK 17、Android SDK 35 和 PowerShell：

```powershell
pwsh -File .\scripts\build-local.ps1 -Target Verify
```

项目使用 Kotlin、Jetpack Compose 和 Material 3。构建配置、平台边界和每版验收记录见 [`docs/`](docs/) 与 [`HANDOFF.md`](HANDOFF.md)。

## 致谢

- [KellenGO/SiYe](https://github.com/KellenGO/SiYe)：主项目及功能参考。
- [KellenGO/MediaCrawler](https://github.com/KellenGO/MediaCrawler)：上游项目及技术方向参考。
