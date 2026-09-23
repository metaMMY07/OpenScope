# 平台图标来源

OpenScope 0.4.1 的平台标识只用于标明搜索来源，应用名称与图标仍是 OpenScope。四个图标随 APK 打包，页面展示时不请求图标服务器。

| 来源 | 应用内文件 | 核对入口与原始资源 |
| --- | --- | --- |
| 哔哩哔哩 | `platform_bilibili.jpg`，512×512 | [开发者 bilibili 的 App Store 页面](https://apps.apple.com/cn/app/id736536022)；通过 Apple Lookup API `id=736536022` 的 `artworkUrl512` 下载。 |
| 知乎 | `platform_zhihu.jpg`，512×512 | [开发者 Beijing Zhizhetianxia Technology Co., Ltd. 的 App Store 页面](https://apps.apple.com/cn/app/id432274380)；通过 Apple Lookup API `id=432274380` 的 `artworkUrl512` 下载。原官网 favicon 仅 32×32，放大模糊，未使用。 |
| 小红书 | `platform_xhs.png`，180×180 | [官方网站](https://www.xiaohongshu.com/) 的 `apple-touch-icon`：`https://picasso-static.xiaohongshu.com/fe-platform/f43dc4a8baf03678996c62d8db6ebc01a82256ff.png`。 |
| 抖音 | `platform_douyin.png`，100×100 | [官方网站](https://www.douyin.com/) 的 `apple-touch-icon-precomposed`：`https://sf1-cdn-tos.douyinstatic.com/obj/eden-cn/kpchkeh7upepld/fe_app_new/logo_launcher_v2.png`。 |

这些名称和图标的商标权仍属于各平台。来源确认与封面加载逻辑彼此独立；封面只从对应平台的 HTTPS 图片域名读取，无登录 Cookie。
