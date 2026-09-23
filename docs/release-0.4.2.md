# OpenScope Android 0.4.2 本地验收

日期：2026-09-23。工作目录 `D:\OpenScope`，分支 `codex/siye-android-parity`。这是本地侧载开发包；抖音为默认关闭的实验性来源。

## 抖音登录态搜索

本版在 App 内官方 WebView 登录后识别可见的已登录账号入口，账号页实测显示“已登录”；覆盖安装 0.4.2 Release 并冷启动后仍显示“已登录”。搜索页仍由用户自行勾选抖音来源，不向其他平台发送抖音凭证。官方搜索页改版后，结果列表位于 `.search-result-card`；新版提取该列表的视频地址、标题、作者、缩略图和页面显示的点赞数，同时保留旧选择器回退。没有破解签名或自动完成平台验证。

API 35 x86_64 开发模拟器 `emulator-5580` 使用已有登录会话，OpenScope Debug 包内仅选择抖音进行实测；随后将同一源码构建的 Release 包覆盖安装并检查账号状态：

| 关键词 | App 内结果 | 耗时 | 观察 |
| --- | ---: | ---: | --- |
| `iPhone` | 16 条 | 6185ms | 前两条标题均与 iPhone 相关，卡片展示封面、作者与点赞数。 |
| `Android` | 15 条 | 6027ms | 官方 WebView 搜索页本身展示了明显偏题的视频，App 如实读取；不能认定为有效的相关搜索。 |

这些数字来自单次模拟器测试，不是目标手机、蜂窝网络或热态性能承诺。搜索页仍可能触发验证码、限流或平台改版；需要用户在官方页面自行完成验证。当前没有证明结果对所有关键词均相关，也没有验证分页稳定性。

## 构建与安装

`pwsh -File .\scripts\build-local.ps1 -Target Verify` 成功，耗时 1m57s，56 项 JVM 单测通过（0 失败、0 错误），lint 0 error / 31 warnings。

| ABI | 本地包 | 大小 | SHA-256 |
| --- | --- | ---: | --- |
| ARM64 真机 | [OpenScope-0.4.2-arm64-v8a.apk](https://github.com/metaMMY07/OpenScope/releases/download/v0.4.2/OpenScope-0.4.2-arm64-v8a.apk) | 10,746,951 bytes | `97DDB1D39E5A535E616EC212E9D250193FB163BDED105AC5D05EAE243D0DA199` |
| x86_64 模拟器 | [OpenScope-0.4.2-x86_64.apk](https://github.com/metaMMY07/OpenScope/releases/download/v0.4.2/OpenScope-0.4.2-x86_64.apk) | 11,440,785 bytes | `3F2A83B461A8588859482F0AD3E00A9246FF906FB36CA813F0FEF913100AB9FD` |

包名 `dev.mediasearch`；versionCode 6、versionName 0.4.2、minSdk 29、targetSdk 35。ARM64 包签名校验通过，证书 SHA-256 为 `583079a20081a1bedaf8c1fbdf93e92bf3cc3544e0f7246fef7b1625d9066a02`。当前使用内部测试 debug key，正式分发前需换用受控签名。x86_64 Release 已在 `emulator-5580` 覆盖安装，系统报告 versionCode 6 / versionName 0.4.2；强停后一次冷启动 `am start -W` 为 445ms，仅作模拟器样本，不能外推到真机。OpenScope 代码未保存短信验证码；该轮所有登录操作均在平台官方页面完成。

## 待验证

- 目标真机及蜂窝网络上的登录保持、搜索相关性、挑战频率、冷/热耗时、内存和耗电。
- 抖音的更多结果、切换关键词后的缓存行为与官方页面改版适配。
- 知乎与小红书的真实账号搜索闭环仍独立待验收。
