# v0.1.1 验收记录

- 签名正式版：**57513 字节（56.2 KiB）**，Android 8.0+（minSdk 26），versionCode 2。
- APK SHA-256：`3f84ea83c22c1f59b727083b4080795a728d7543b59fb689898edb81f20797eb`。
- `apksigner verify --print-certs` 通过，证书沿用 v0.1.0：`a6f25e1a490efbf985d753d216ffdbaeea360a9ab4b57ad2c156eabf553d19b6`。
- `testDebugUnitTest --rerun-tasks`：30 项、0 失败、0 错误。摘要见 [单元测试结果](evidence/v0.1.1-unit-results.json)。
- 全量 Android 仪器测试：**OK (11 tests)**，61.718 秒。运行设备为独立 CodeCanvasQA 模拟器（Android 17）。
- Windows Gradle UTP 曾超时/残留文件锁；最终通过 `adb shell am instrument -w -r com.nous.codecanvas.debug.test/android.test.InstrumentationTestRunner` 直接执行同一套全部测试，并核对每项完成与总数。**不把失败的 connectedDebugAndroidTest 描述成成功**。[原始输出](evidence/v0.1.1-validation-final.txt)。

## 覆盖的真实操作

- 首页真实「粘贴并预览」按钮读取前台剪贴板；清理外层 Markdown 围栏。
- 多段 HTML/SVG 弹窗真实选择 SVG；保留全部原文选项；不自动拼接。
- 光标位于中间时真实点击「追加到末尾」；真实点击覆盖替换，再撤销恢复原文。
- 语法高亮保留起止选区；自定义后缀、保存、退出、重新打开实际 EditorActivity 检查内容与名称。
- 真实作品卡片点开默认预览，真实新建命名框确认后默认编辑。
- HTML/SVG WebView DOM、JavaScript 默认关闭/显式启用；XML 解析错误、XXE 拦截。
- 默认 WebSettings 网络阻断；真实确认联网、切回离线、新 Editor 恢复离线。
- 深浅色实际命名输入框文字与 hint 对比度均 >=4.5；浅色状态栏图标回归。
- 真正的 WebView 缩略图缓存、320px 宽度及非白/实际绿色像素检查。

## 正式版独立检查

- 安装实际签名 APK、启动 `com.nous.codecanvas/.ui.MainActivity` 成功。
- 检查浅色主页、命名、HTML/SVG/XML 页面和深色命名弹窗；修复浅色状态栏白底白字。[本版截图](evidence/v0.1.1/)。
- HTML/SVG 内容实际显示，不以仅有页面加载回调证明渲染；主页加载已看过作品的真实缩略图。
- 真实系统 SAF 导出到 Downloads，再由 adb pull 读取：welcome.html **1145 字节**，包含完整 HTML。[导出原文件](evidence/v0.1.1/release-export-welcome.html)。
- 原生 ListView 复用卡片、后台准备哈希/大小/行数；主页不为每张卡启动 WebView；代码/预览保留轻量过渡动画。未宣称真实手机上的帧率指标。

## 功能边界

- 普通 XML 只提供结构/文本与语法校验，**不是** XSLT、Android XML 布局或任意 XML 的可视化运行器。
- 默认离线，JS 独立关闭；用户确认后只允许当前页面联网。禁止本地 file/content 访问、HTTP 明文资源。
- 任意文件后缀可保存；不表示支持执行 Python、Node.js、Vue/React 等需要编译/运行环境的项目。
- 需要相对图片、外部样式等资源的代码不能靠粘贴自动补齐；推荐让 AI 输出单个自包含 HTML（内嵌 CSS/JS，不依赖 CDN）。
- 导入最大 2MB；超大剪贴板有上限提示；大文件性能不作为本版保证。
- 自动化行为测试使用 debug APK；正式版做了安装、启动、截图、渲染与系统导出冒烟，不等于正式版全量自动化。
- **尚未在小米 Turbo 3 / HyperOS 实机验证**；输入法、手势、120Hz 动画和所有外部文件提供商兼容性仍需试用。

---

# v0.1.0 历史验收记录

> 更正：旧版“默认离线”的说明未对应实际网络阻断；v0.1.1 已补 WebSettings 阻断和显式确认。本节只记录旧版本历史结果。


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
