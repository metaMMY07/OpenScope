# OpenScope Android 0.4.4 发布验收

日期：2026-09-23。0.4.4 保留 [0.4.3 的功能和验证边界](release-0.4.3.md)，并将 `versionCode` 从 7 升为 8、`versionName` 从 0.4.3 升为 0.4.4，供现有安装直接覆盖升级。极简首页的底部 dock、深色文字与状态栏对比度修复已包含在内。

本版本使用与 0.4.3 相同的内部测试签名，已作为 GitHub Release `v0.4.4` 发布，适合侧载升级。

## 构建与安装

`scripts/build-local.ps1 -Target Release` 构建成功。0.4.3 的最终逻辑已通过 56 个 JVM 测试与 Android Lint（0 错误）；本轮仅提升版本元数据，未改动功能代码。

| 本地 APK | 字节数 | SHA-256 |
| --- | ---: | --- |
| [ARM64 APK](https://github.com/metaMMY07/OpenScope/releases/download/v0.4.4/OpenScope-0.4.4-arm64-v8a.apk) | 10,764,663 | `5B4894EE13E52C538E5B7785CD3EAC2B1A04BBF08CD00BD25BFA3DA63DD29A63` |
| [x86_64 APK](https://github.com/metaMMY07/OpenScope/releases/download/v0.4.4/OpenScope-0.4.4-x86_64.apk) | 11,458,497 | `EA18E3D6EAEF735AA0B427897F7DB96A7EE939A16B18D86693578F555F1D71C8` |

`aapt` 确认 ARM64 APK 内为 `versionCode=8` / `versionName=0.4.4`；`apksigner` 确认证书 SHA-256 `583079A20081A1BEDAF8C1FBDF93E92BF3CC3544E0F7246FEF7B1625D9066A02`，与 0.4.3 相同。API 35 x86_64 模拟器从已安装的 0.4.3（版本代码 7）执行 `adb install -r` 后返回 `Success`；设备报告版本代码 8，极简首页的保存设置仍生效、底部 dock 显示正常。
