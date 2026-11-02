# CodeCanvas

- `app/` 为 Java 原生 Android + 系统 WebView（`com.nous.codecanvas`，minSdk 26），预览 HTML/SVG/XML，不自带浏览器内核或前端编译链。
- Windows 当前打包入口是 `desktop/native/build-native.ps1`，实现为 C# + 系统 WebView2；目录仍有 Electron 源码，不能据此误判当前发布架构。
- Android 版本在 `app/build.gradle.kts`，桌面版本在 `desktop/package.json`，两端不要求同号。
- Android 预览默认阻断网络，JavaScript 需显式启用；禁止放宽 file/content 访问。XML 校验保留 DOCTYPE/XXE 防护，XML 结构预览不依赖 WebView。
- 粘贴时多段代码分别选择，不擅自拼接 HTML/CSS/JS；保存和导出保持标准文件与已有作品可重开。
- Android release 签名从 `signing.properties` 或 `CODE_CANVAS_KEYSTORE_PATH` 等环境变量读取；该文件及 keystore 已忽略，不得改用另一签名破坏覆盖升级。
- UI 改动先读 `docs/DESIGN.md`；历史验证与已知限制见 `docs/VERIFICATION.md`，不能把历史通过项当成本轮实测。
