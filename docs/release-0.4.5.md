# OpenScope Android 0.4.5 本地验收

0.4.5 在设置中新增“官方网页版本”：手机默认移动网页，平板默认电脑网页，用户可随时切换。选择只影响 App 内打开的官方内容网页；登录继续使用各平台已知兼容布局，后台搜索、签名与请求 UA 不受此选项影响。B站移动布局会使用 `m.bilibili.com` 的首页和视频页，保留链接的查询参数与片段；其他站点按所选移动或电脑 UA、视口打开。若平台移动网页只提供“打开 App”引导，可在设置中切回电脑网页。

## 验证与边界

API 35、360dp 宽 x86_64 模拟器从已安装的 0.4.4 覆盖安装 0.4.5 成功，原有极简首页设置保留。新选项首次显示“移动网页”已选；切换“电脑网页”后重启 App，选择仍保留。选择移动模式打开 B站官方搜索页，实际落在 `m.bilibili.com · 移动版`，结果卡片和文字按手机宽度显示；切回电脑模式则打开 `search.bilibili.com · 电脑版`，页面明显缩小。小红书在选择移动模式时，账号登录仍打开兼容的 `www.xiaohongshu.com · 电脑版`。

实拍：[设置选项](images/openscope-web-mode-settings-045.png) · [B站移动网页](images/openscope-bili-mobile-045.png) · [B站电脑网页](images/openscope-bili-desktop-045.png)。

小红书未登录模拟器中的移动 Explore 页未呈现可读内容，因此不能声称已登录后的小红书移动页可用；遇到平台网页仅显示 App 引导或空白时，选择电脑网页。抖音移动内容页和平板默认选项尚未做真机视觉验收。

## 构建与安装

`scripts/build-local.ps1 -Target Verify` 通过：58 个 JVM 测试通过，Android Lint 0 错误、33 个警告，Release 构建成功。`aapt` 确认包名 `dev.mediasearch`、`versionCode=9`、`versionName=0.4.5`。`apksigner` 确认测试证书 SHA-256 `583079A20081A1BEDAF8C1FBDF93E92BF3CC3544E0F7246FEF7B1625D9066A02`，与 0.4.4 一致。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| `artifacts/OpenScope-0.4.5-arm64-v8a.apk` | 10,764,663 | `53EBD089DBDFD63B319E20AB80DDB43B1C05E009B1FF0725A9C434D9BC098764` |
| `artifacts/OpenScope-0.4.5-x86_64.apk` | 11,458,497 | `CAC90119373030D8A917D246280AB6C161E49BAE5079606473468DEB41552DE4` |

0.4.5 使用内部测试签名，尚未作为 GitHub Release 发布。
