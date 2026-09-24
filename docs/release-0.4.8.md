# OpenScope Android 0.4.8 本地验收

0.4.8 修复搜索首页两处四平台选择栏在窄屏上裁切抖音按钮的问题。普通首页的搜索源与热搜选择均改为四等宽按钮，保持单行、官方图标和至少 48 dp 的触控高度；极简首页原有单行布局保留。

抖音官方视频详情页在手机模式下收起桌面侧栏和站内搜索头部，播放器、标题、互动区及作者信息适配视口。点赞、评论、收藏和转发四项等宽排列，转发图标与其他图标处在同一水平线上。仅对非登录的手机页面生效；平板页面和登录页仍使用原有显示方式。

## 模拟器验收

API 35、1080×2400 的 x86_64 模拟器上，最终 0.4.8 APK 覆盖安装成功。普通首页两排四平台按钮均完整显示，点击最右侧抖音按钮可切换选择。已保存会话的抖音直接视频页中，文档宽度和视口宽度均为 412 CSS 像素；四个互动按钮顶部均为 389 CSS 像素。竖屏页面、横屏全屏及退出后恢复竖屏均经截图目视检查，截图位于 `artifacts/final048-home.png`、`artifacts/final048-douyin-video-loaded.png`、`artifacts/final048-douyin-landscape.png` 和 `artifacts/final048-douyin-exit.png`。

这些是模拟器上的页面布局验证，不代表所有真机尺寸和抖音页面类型均已覆盖。站点修改 DOM 后，手机适配选择器仍可能需要更新。

## BewlyBewly 评估

按用户最终决定，0.4.8 不内置 BewlyBewly。v0.41.1 是浏览器扩展而非可直接放入 Android WebView 的网页脚本；在 OpenScope 的 B 站 WebView 里隔离注入原版内容脚本时立即报错 `This script should only be loaded in a browser extension.`。完整依据与后续可选路径见 [bewlybewly-feasibility.md](bewlybewly-feasibility.md)。APK 不含该扩展。

## 构建与安装

`scripts/build-local.ps1 -Target Verify` 通过：58 个 JVM 测试、0 失败，Android Lint 0 错误，Release 构建成功。版本号为 `versionCode=12`、`versionName=0.4.8`，包名仍为 `dev.mediasearch`，可覆盖安装同签名的旧版测试包。Release 使用现有内部测试签名，未安装到用户真机，也未发布到 GitHub Release。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| `artifacts/OpenScope-0.4.8-arm64-v8a.apk` | 10,785,623 | `F576BB951A5AD2A9F1F351107A5E360B7C9FBE9060862EA1F391EC5262DC8597` |
| `artifacts/OpenScope-0.4.8-x86_64.apk` | 11,479,457 | `DE4DF22F51AC0DE4217A0D75E6558A636A0996BCE258879F71C68A16A4E827F0` |
