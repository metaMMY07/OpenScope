# BewlyBewly v0.41.1 与 OpenScope WebView 兼容性

结论：原版扩展不能直接内置到当前 OpenScope 的 Android WebView。用户已决定本轮不装插件，因此 0.4.8 不引入其代码或资源。

- 源码仓库 `BewlyBewly/BewlyBewly` 的 v0.41.1 标签指向 `d421435`，许可证为 MIT。发行包 `extension.zip` 为 16,126,298 字节，其中 `assets/fonts/ShangguSansSC-VF.ttf` 压缩后约 14.3 MB。把完整 ZIP 加入 APK 会明显超过当前约 10.8 MB 的 ARM64 包体目标。
- 扩展清单是 Manifest V3，包含后台脚本、`storage`、`declarativeNetRequest`、`tabs` 权限及多个站点的 `document_start` 内容脚本。源码通过 `browser.storage.local` 保存设置，通过 `browser.runtime.sendMessage` 向后台请求 B 站 API，样式资源使用 `browser.runtime.getURL`。这些是扩展运行环境的一部分，不是 WebView 页面 API。
- 在 OpenScope 0.4.8 的模拟器 B 站 `search.bilibili.com` WebView 中，`chrome` 和 `browser` 均未定义；把发行包的 `dist/contentScripts/index.global.js` 直接注入，立即抛出 `Error: This script should only be loaded in a browser extension.`。该试验没有加入正式 APK，也没有更改账户数据。
- 官方 Android WebView 文档描述的是嵌入网页的控件；Mozilla GeckoView 则提供内置 WebExtensions API。切换到 GeckoView 需要更换浏览器引擎、重新处理 B 站等平台的 WebView 会话与测试，也会与当前小体积 APK 目标冲突。

若未来重新评估，只能作为独立移植项目：按页面选择性打包资源，替换后台消息、存储、资源地址和跨域 API 调用，再在手机屏幕上逐页重做适配与登录验证。不能把原版扩展 ZIP 简单复制进 APK 并声称可用。

参考：[BewlyBewly v0.41.1](https://github.com/BewlyBewly/BewlyBewly/releases/tag/v0.41.1)、[源码和许可证](https://github.com/BewlyBewly/BewlyBewly)、[Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)、[GeckoView WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)。
