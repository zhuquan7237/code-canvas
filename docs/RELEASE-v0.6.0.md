# 代码画布 0.6.0

versionCode 60 · versionName 0.6.0 · minSdk 26

签名包在 GitHub Release `v0.6.0`，文件名 `code-canvas-0.6.0.apk`。证书必须与 0.1.0–0.5.6 相同，SHA-256 为 `a6f25e1a490efbf985d753d216ffdbaeea360a9ab4b57ad2c156eabf553d19b6`。

## 这一版有什么

- 编辑器按扩展名选择颜色：`.py`、`.js` / `.ts`、`.kt` / `.java`、`.css`、`.sql`、`.sh`、`.yml`。关键字、字符串、数字、注释分开。浅色和深色都用对比足够的颜色。
- 上色只覆盖可见窗口，并且单次不超过 6000 个字符。8000 字以内的短文件整篇上色，打开就能看见颜色。
- XML 预览是可折叠结构。解析错误会标出那一行，点「定位」回到代码。没有 DOCTYPE 的文件，在解析器无法关闭 DOCTYPE 开关时仍然可以预览。带 DOCTYPE 的文件继续拒绝。
- YAML 有下级的行可以折叠。缩进检查标出的行可以跳回代码。界面仍写明没有做完整语法校验。
- 点保存并且写入成功时，按钮有一次轻触反馈。

模拟器 API 34、360dp。截图在 `docs/screenshots/android/v5-*.png`。
