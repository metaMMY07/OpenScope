# OpenScope Android 0.4.11 本地验收

本版针对小红书官方网页的“保存图片”按钮：页面生成的临时图片 Blob 由限定为 `https://www.xiaohongshu.com` 主页面的 WebView 消息通道传给 App，识别图片格式后写入系统相册 `Pictures/OpenScope`；普通 HTTPS 图片下载也接入同一保存路径。单张上限 20 MB，来源域名、跳转和图片格式均有限制。正常浏览内容页不再每 1.5 秒做登录状态 JS 与 Cookie 检查；登录页仍保留检测。

`scripts/build-local.ps1 -Target Verify` 通过：58 项 JVM 单测、0 失败，Android Lint 和 Release 构建成功。API 35 x86_64 模拟器离线探针用与网页相同的 Blob 下载动作，得到 `bridge_installed=true`、`bytes_match=true`、`album_bytes_match=true`。x86_64 Release 覆盖安装、启动成功，设备报告 `versionCode=15` / `versionName=0.4.11`。模拟器此前无法稳定解析小红书域名，因此**尚未在真实小红书登录页点击按钮验收，也没有实测卡顿改善幅度**；这些需要在联网真机确认。

包名仍为 `dev.mediasearch`，签名仍为内部测试密钥，ARM64 APK 的 v2 签名已验证，与 0.4.10 测试包证书相同。此版仅在本地打包，未发布 GitHub Release。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| [ARM64 APK](../artifacts/OpenScope-0.4.11-arm64-v8a.apk) | 10,786,513 | `8758F78E27437B389A62C307AC04DC512899164330CCF6957908A43F32F73B27` |
| [x86_64 APK](../artifacts/OpenScope-0.4.11-x86_64.apk) | 11,480,347 | `F5E36872CA350AF898E0AA62B5616EBC0A0E5EEC8DAE6DC6BF1B750731C9DFD3` |

后续真机验收：在已登录小红书网页打开图片，点击网页自身“下载图片/保存图片”，确认相册 `Pictures/OpenScope` 中可打开图片；连续浏览和翻页观察卡顿；页面改版后若按钮不再生成 Blob，检查新的下载流程再调整接管方式。

后续参考项目 [chinasoul/XT](https://github.com/chinasoul/XT)：公开仓库网页当前仅列出 README，其中将 XT 定位为 Android TV 的小红书客户端并提到 APK；未见可直接评估或移植的公开源代码。可参考其大屏媒体浏览思路，手机端浏览与图片保存仍按 OpenScope 的真机验收优先推进。
