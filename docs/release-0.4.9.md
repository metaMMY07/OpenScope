# OpenScope Android 0.4.9 本地验收

0.4.9 移除内置平台网页顶部整条原生导航。普通页面只在右上角保留一个小型“刷新”按钮；手机视频页的“全屏”入口留在页面右下角。进入横屏全屏后不显示 OpenScope 的退出按钮、刷新按钮或其他原生悬浮控件。按 Android 系统返回手势或返回键退出全屏，再次返回可回到搜索结果。

B 站和抖音的视频页在横屏时隐藏系统状态栏与导航栏，平板页面也不再占用顶部原生导航空间。登录提示仅在 App 内登录页保留，平台网页内容与会话读取逻辑没有改动。

## 模拟器验收

在 API 35、1080×2400 的 x86_64 模拟器覆盖安装最终 APK 后，打开 B 站搜索结果中的视频。手机页面无顶部原生导航，竖屏显示右上角刷新与右下角全屏；进入全屏后视频占据横屏可用区域，画面无 OpenScope 控件。按系统返回键后恢复竖屏、系统状态栏和原页面，第二次返回恢复原搜索结果。截图见 `artifacts/final049-bili-video-current.png`、`artifacts/final049-bili-fullscreen-current.png`、`artifacts/final049-bili-after-back-current.png`。

切到“平板页面”后横屏打开同一 B 站视频，顶部原生导航和系统栏均未出现，官方网页仍显示其自身导航。截图见 `artifacts/final049-bili-tablet-landscape-clear.png`。未在用户真机复测，也未逐一验证其他平台的所有页面类型。

## 构建与安装

`scripts/build-local.ps1 -Target Verify` 通过：58 个 JVM 测试、0 失败，Android Lint 和 Release 构建成功。版本为 `versionCode=13`、`versionName=0.4.9`，包名 `dev.mediasearch`；可覆盖安装同签名的旧版测试包。ARM64 APK 的 v2 签名已验证。Release 仍使用内部测试签名，尚未发布到 GitHub Release。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| `artifacts/OpenScope-0.4.9-arm64-v8a.apk` | 10,769,239 | `93C6C6D77D3C4B03847E7DD4AD0FED4003604960C4C45C86CD50DF205EEEAC43` |
| `artifacts/OpenScope-0.4.9-x86_64.apk` | 11,463,073 | `C285D771E5751827689D257063C1D4A8C93492F490CD74C1B720EA8FFF02E982` |
