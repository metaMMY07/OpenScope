# OpenScope Android 0.4.13 本地验收

修复底部导航点击时出现整列灰色矩形的问题：导航项关闭默认矩形点击涟漪，只保留移动的圆角选中指示器。底栏改为 Haze 1.6.9 的实时背景模糊，列表可在底栏后方滚动；各列表增加底部留白，最后一项仍能完整滚到可点击区域。没有内容经过底栏时，模糊区域自然显示为主题背景色。LiteTale 仍只作只读视觉参考。

`scripts/build-local.ps1 -Target Verify` 通过：58 项 JVM 单测、0 失败，Android Lint 和 Release 构建成功。API 35 x86_64 模拟器在 1080×2400 手机尺寸和临时 1920×1358 平板尺寸下检查了底栏，按住未选中的导航项期间未再出现灰色矩形；模拟器分辨率已恢复。x86_64 Release 覆盖安装并启动，设备报告 `versionCode=17` / `versionName=0.4.13`。旧版 Android 和用户真机的模糊性能仍需实际体验验证。

Haze 源码：[chrisbanes/haze](https://github.com/chrisbanes/haze)，Apache License 2.0；许可全文随 APK 内置，并在“设置 → 关于 → 来源与许可”中显示。包名仍为 `dev.mediasearch`，使用与此前测试版相同的内部签名；ARM64、x86_64 APK 的 v2 签名均已验证。本版仅在本地打包，未发布 GitHub Release。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| [ARM64 APK](../artifacts/OpenScope-0.4.13-arm64-v8a.apk) | 10,830,107 | `038561F67EF62F723E7CE8F66924E9A9BD54919B50AAA0A7688F153188BCC85E` |
| [x86_64 APK](../artifacts/OpenScope-0.4.13-x86_64.apk) | 11,523,941 | `E1D0451D2568D93BA3ADAD8DF3E6A39BFA269172E4D1C4E4089D37ADAFF6AE9B` |

视觉记录：[按住导航项的手机画面](../artifacts/openscope-ui-0413-nav-pressed.png)、[平板尺寸内容库](../artifacts/openscope-ui-0413-glass-tablet.png)、[Release 极简首页](../artifacts/openscope-ui-0413-release.png)。
