# OpenScope Android 0.4.5 本地验收

0.4.5 在设置中新增“官方网页版本”：手机页面与平板页面可随时切换。两种模式都请求官方电脑网页；手机页面使用设备宽度视口和较大文字，平板页面保留更宽的电脑布局。选择只影响 App 内打开的官方内容网页；登录继续使用各平台已知兼容布局，后台搜索、签名与请求 UA 不受此选项影响。

## 验证与边界

API 35、360dp 宽 x86_64 模拟器从已安装的 0.4.4 覆盖安装 0.4.5 成功，原有极简首页设置保留。新选项首次显示“手机页面”已选；切换“平板页面”后重启 App，选择仍保留。两种模式均保持官方电脑站点和登录入口，手机页面只改变视口与字号，避免跳转到功能缺失的移动站点；平板页面保留宽布局。实际真机和实体平板仍需补测。

实拍：[手机/平板设置](images/openscope-web-mode-settings-045.png) · [B站手机页面适配](images/openscope-bili-mobile-045.png) · [B站平板页面适配](images/openscope-bili-desktop-045.png)。

小红书未登录模拟器中的官方 Explore 页未呈现可读内容，因此不能声称已登录后的小红书页面可用；遇到平台网页仅显示 App 引导或空白时，可切换到平板页面重试。抖音手机页面和平板页面尚未做真机视觉验收。

## 构建与安装

`scripts/build-local.ps1 -Target Verify` 通过：58 个 JVM 测试通过，Android Lint 0 错误、33 个警告，Release 构建成功。`aapt` 确认包名 `dev.mediasearch`、`versionCode=9`、`versionName=0.4.5`。`apksigner` 确认测试证书 SHA-256 `583079A20081A1BEDAF8C1FBDF93E92BF3CC3544E0F7246FEF7B1625D9066A02`，与 0.4.4 一致。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| `artifacts/OpenScope-0.4.5-arm64-v8a.apk` | 10,765,317 | `FDAF184B9C5650E4509667B4A58405B0BBE55B084F9927824BACB26156CACEC2` |
| `artifacts/OpenScope-0.4.5-x86_64.apk` | 11,459,151 | `DA956C4B80544F98FDB9EC1C137441ED6C30CACAF25C6757B8C1CA6994D8E23E` |

0.4.5 使用内部测试签名，尚未作为 GitHub Release 发布。
