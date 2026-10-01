# v0.1.0 验收记录

- 本机 JDK 17，Android SDK API 35，模拟器 AtlasRefactorQA（Android 17）。
- `gradlew.bat testDebugUnitTest assembleRelease`：BUILD SUCCESSFUL。
- 单元测试：10 项，0 失败，0 错误。
- `gradlew.bat connectedDebugAndroidTest`：4 项，0 失败，0 跳过；覆盖新建任意后缀、剪贴板粘贴、编辑、撤销重做、保存重开、HTML/SVG WebView DOM、JavaScript 门禁、XML 校验与 XXE 拦截。
- Release APK：42620 字节。SHA-256：669b8b5c6692ed15c2399178dd30f53c804215347f06fda60b7835c52823fe2f。
- apksigner verify 通过；实际 release APK adb install -r 成功，启动主页与编辑器成功。
- Release 页面实测：状态栏安全区无遮挡，标签独立分行；截图 main_screen.png、editor_screen.png 已更新。
- Release SAF 导出：系统 Downloads 中 welcome.html 成功写入，读回 1165 字节并确认 HTML 内容。
- 其余预览截图来自较早构建，仅展示渲染效果，不作为最终安全区验收依据。

## 已知边界

- 普通 XML 提供格式化文本与语法校验，不提供 XSLT 或 Android XML 布局渲染。
- 默认离线；不支持依赖外部 CDN 的完整网站项目，也不提供 Vue/React 编译。
- 导入与快捷粘贴限制 2MB；超大文件不作为流畅性保证范围。
- 自动化行为测试在 debug 构建运行；release 已完成安装、启动、页面与 SAF 导出冒烟测试，不等于 release 全量自动化覆盖。
- 未在小米 Turbo 3 实机测试；不同输入法、HyperOS 和刷新率下的动画流畅性待用户试用。
- SAF 导入、系统分享入口已实现；未完成跨所有文件提供商/接收应用的端到端兼容测试。
