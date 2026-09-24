# OpenScope Android 0.4.10 本地验收

按用户反馈，内置平台网页不再显示 OpenScope 的“刷新”悬浮按钮。加载失败与 HTTP 错误提示也改为返回重试，不再要求点击已移除的按钮。视频页原有“全屏”入口和 Android 系统返回手势保持不变；0.4.9 中移除的顶部导航与“退出全屏”按钮没有恢复。

`scripts/build-local.ps1 -Target Verify` 通过：58 个 JVM 测试、0 失败，Android Lint 和 Release 构建成功。API 35 x86_64 模拟器覆盖安装成功，实际安装版本为 `versionCode=14`、`versionName=0.4.10`。在 B 站官方网页打开后的错误页面检查，右上角已无 OpenScope 刷新按钮，截图见 `artifacts/final0410-browser-loaded.png`。本次模拟器网络出现 `ERR_NAME_NOT_RESOLVED`，因此未将官方网页加载成功或视频播放标作 0.4.10 的实测；0.4.9 的布局与全屏验收见 [release-0.4.9.md](release-0.4.9.md)。

包名仍为 `dev.mediasearch`，可覆盖安装同签名的旧版内部测试包。ARM64 APK 的 v2 签名已验证。尚未安装到用户真机或发布到 GitHub Release。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| `artifacts/OpenScope-0.4.10-arm64-v8a.apk` | 10,769,235 | `4EC81F01C8C728BD2EB66DB09D7C1816F9965D8DB3D5730C695E2087691C7A5A` |
| `artifacts/OpenScope-0.4.10-x86_64.apk` | 11,463,069 | `79A89FBB9FE0A4263D98103A0098A3A98D4564AC5F859423A966905C6F848079` |
