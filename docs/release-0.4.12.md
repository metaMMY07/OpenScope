# OpenScope Android 0.4.12 本地验收

极简首页的四个平台图标改为占满图标位，移除了图标外侧的灰色边框和选中外圈；选中平台改用文字颜色区分。首页右上角增加月亮／太阳按钮，一次点击切换深浅色。底部导航参考 LiteTale 的错时移动选中指示器，使用 Compose 实现动画和半透明渐变背景；LiteTale 源项目仅作只读参考，未作修改。当前背景为轻量半透明效果，**没有实时背景模糊采样**。

`scripts/build-local.ps1 -Target Verify` 通过：58 项 JVM 单测、Android Lint、Debug 和 Release 构建成功。API 35 x86_64 模拟器已检查极简首页的浅色和深色图标、主题按钮及“内容库”导航选中状态；x86_64 Release 覆盖安装并启动，设备报告 `versionCode=16` / `versionName=0.4.12`。平台真实登录和网页浏览流程未在本轮重测。

包名仍为 `dev.mediasearch`，沿用内部测试签名；两个 APK 的 v2 签名均已验证。本版仅在本地打包，未发布 GitHub Release。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| [ARM64 APK](../artifacts/OpenScope-0.4.12-arm64-v8a.apk) | 10,786,513 | `E6821560B3F8E388375CABD7AE441527317E1E01E0DF197A6BFF15B2DACBB7C8` |
| [x86_64 APK](../artifacts/OpenScope-0.4.12-x86_64.apk) | 11,480,347 | `59B643058E12C61B192068088FEEFA4E881B1F791F41AEAAEBFFDCB8C248CE40` |

视觉记录：[浅色极简首页](../artifacts/openscope-ui-0412-minimal-final.png)、[深色极简首页](../artifacts/openscope-ui-0412-dark-final.png)、[深色内容库](../artifacts/openscope-ui-0412-library-final.png)。
