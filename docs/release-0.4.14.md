# OpenScope Android 0.4.14 本地验收

针对用户提供的底栏录屏，修复极简首页与普通页面切换时高亮块跳位的问题：导航动画状态现在由应用主界面持有，两个页面共用同一运动位置。高亮块的前缘采用 ease-out cubic、后缘采用 ease-in-out cubic，连续点击会从当前形状继续移动。保留 0.4.13 的 Haze 背景模糊和无矩形点击涟漪。LiteTale 项目仅作只读参考，未修改。

深色模式下，正文、次要文字和默认图标使用中性白与浅灰；系统动态配色和自选主题色仍控制背景与强调色。浅色模式不变。

`scripts/build-local.ps1 -Target Verify` 通过：58 项 JVM 单测、0 失败，Android Lint 和 Release 构建成功。API 35 x86_64 模拟器在 1080×2400 手机尺寸和 1526×1080 横屏平板尺寸下录屏检查了极简首页、内容库、账号和设置间的连续导航，未再出现高亮块重置跳位；深色模式文字和底栏图标已目视核对。新版界面截图见 [搜索首页](images/openscope-0414-search-home.png)、[聚合结果](images/openscope-0414-results.png)、[账号](images/openscope-0414-accounts.png) 与 [设置](images/openscope-0414-settings.png)。模拟器分辨率与密度已恢复。x86_64 Release 覆盖安装并启动，设备报告 `versionCode=18` / `versionName=0.4.14`。用户真机上的动画帧率仍以实际设备为准。

包名保持 `dev.mediasearch`，与上一版使用同一内部测试签名，ARM64 和 x86_64 APK 的 v2 签名均已验证；可覆盖安装同签名的旧版测试包。本版已发布到 GitHub Releases。

| APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| [ARM64 APK](https://github.com/metaMMY07/OpenScope/releases/download/v0.4.14/OpenScope-0.4.14-arm64-v8a.apk) | 10,830,107 | `B0B2E0694D2AAA683A859E10AF7BA6E7469699943C08361EA83C0580C22FF9A1` |
| [x86_64 APK](https://github.com/metaMMY07/OpenScope/releases/download/v0.4.14/OpenScope-0.4.14-x86_64.apk) | 11,523,941 | `255B39A9567BC8CE9F25E758EE5F7E721D8B9F41FE802F3FA51DBF015D0FB68C` |
