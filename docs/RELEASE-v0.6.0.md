# 代码画布 0.6.0

versionCode 60 · versionName 0.6.0 · minSdk 26

签名包在 GitHub Release `v0.6.0`，文件名 `code-canvas-0.6.0.apk`。证书必须与 0.1.0–0.5.6 相同，SHA-256 为 `a6f25e1a490efbf985d753d216ffdbaeea360a9ab4b57ad2c156eabf553d19b6`。

## 这一版有什么

- 带 `<html>` 或 `<body>` 的文件按网页打开，JavaScript、CSS 动画和按钮点击都在 WebView 里发生。HTML 用网页自己的缩放。
- `.svg` 的 SMIL、CSS 动画和脚本会播放。缩放计算在 `ViewportTransform`：双指中点不动，双击绕点击处放大或缩回，拖出边界先阻尼再回到界内，松手可以惯性滑动。加号、减号和「适应窗口」有过渡。
- 缩略图按内容边界裁切后再居中铺满格子，宽图不再变成细条。文本显示开头几行。缓存键包含内容哈希。
- 编辑器按扩展名上色。XML 和 YAML 可以折叠，错误行可以跳回代码。

模拟器 API 34、360dp。截图在 `docs/screenshots/android/v5-*.png`。
