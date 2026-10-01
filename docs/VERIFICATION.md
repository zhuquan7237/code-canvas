# v0.1.6 验收记录（主页预览图 · 渲染尺寸 · 脚本默认 · 外部打开）

- 正式签名版 versionCode 7 / versionName 0.1.6；签名证书与 0.1.0–0.1.5 一致（可原地升级）。
- 单元测试 **69 项 0 失败**；模拟器仪器测试 **28 项 0 失败**。
- 关键实测（emulator-5556，非预览）：
  - 主页为**未打开过**的作品生成真实缩略图（`ThumbnailBackfillTest`：480 宽、非空白、含页面实际绘制的背景色，且页面脚本确实执行过）。
  - 浅色页面背景 `#F8FAFC` **不再**被判为空帧（`ThumbnailBlankTest.testLightPageBackgroundIsNotBlank`）；纯白仍判空。
  - 脚本默认开启且**关掉后确实拦得住**（`CodeCanvasActivityE2ETest`：开关开=页面脚本执行，关=`evaluateJavascript` 返回 null）。
  - 外部唤起：`file://…/demo.html` + `application/octet-stream` 的候选列表里**只有**代码画布；`content://…` + `text/html` 与 Chrome/HTMLViewer 并列；`ACTION_SEND` 对 `image/svg+xml`、`text/html`、`application/octet-stream` 均出现代码画布。
  - 上一版发布的应用内升级在模拟器上确认落地：0.1.4 → 0.1.5，重开显示「已是最新 · 0.1.5」。
- 截图证据：`docs/evidence/v0.1.6/`。

# v0.1.5 验收记录（评审第二轮补齐 + 手动下载兜底）

- 正式签名版：**84183 字节（约 82.2 KiB）**，Android 8.0+（minSdk 26），versionCode 6，versionName 0.1.5。
- APK SHA-256：`fddc184ea2045d68b403f184d0580bfdb2d7ce799ac24b5cca8ce5211194624e`（以已发布文件为准）。
- 签名证书与 0.1.0–0.1.4 一致 → 可原地升级。
- 单元测试 **62 项 0 失败**（新增 4 项：说明文字夹代码的截取规则）；模拟器仪器测试 **26 项 0 失败**（无回归）。
- 公网自测：清单 `http=200`、安装包 `http=200` **84183 字节**、SHA-256 与清单一致（`relay.zhuquan.xyz`）。
- 应用内更新实测：已装的 **0.1.4（versionCode 5）** 经应用内更新升到 **0.1.5（versionCode 6）**，系统安装器自报 `App updated`，重开显示「已是最新 · 0.1.5」——**连续第二次**走通自建源通道。
- 顺手修掉发布脚本一个坑：`--notes` 指向不存在的文件时会把**路径本身**写进清单（更新对话框里就会显示一串路径）。现在会直接报错退出，并新增 `--notes-file`。

# v0.1.4 验收记录（应用内更新 + 外部评审修复）

- 正式签名版：**83487 字节（约 81.5 KiB）**，Android 8.0+（minSdk 26），versionCode 5，versionName 0.1.4。
- APK SHA-256：`a2ee2993ecbbee2c5b6181a08270e42fcd21bc8ec6df28b2196eda5a89f480fe`（**以已发布文件为准**；本机重新构建不保证逐字节相同）。
- 签名证书 SHA-256 `a6f25e1a…f553d19b6`，与 0.1.0–0.1.3 一致 → 老用户可原地升级。
- 单元测试 **58 项 0 失败**；模拟器仪器测试 **26 项 0 失败**（emulator-5556）。
- 截图证据：`docs/evidence/v0.1.4/`（主页列表/方格、主页夜间、编辑器代码浅色/夜间、更新提示与安装落地）。

## 更新通道（自建源）验收

| 项 | 证据 |
|---|---|
| 清单可达 | `https://relay.zhuquan.xyz/dl/codecanvas-latest.json` 公网 200，内容与仓库内 `docs/update/latest.json` 一致 |
| 安装包可达且内容寻址 | `.../dl/code-canvas-0.1.4.apk?v=a2ee2993ecbb` 公网 200，83,487 字节，SHA-256 与清单一致 |
| 备用地址 | GitHub Release 资产同名文件下载 200、83,487 字节、同一摘要 |
| 应用内提示 | versionCode=4 构建启动即显示 `新版本 0.1.4 · 更新`（数据来自自建源） |
| 下载与校验 | 下载后 SHA-256 / 大小 / 包名 / versionCode / **签名与已安装包一致** 逐项通过，随后交给系统安装器 |
| 安装落地 | 系统安装器确认后，应用由 versionCode 4 变为 **5 / 0.1.4**，安装器自报 `App updated`；重开显示 `已是最新 · 0.1.4` |
| 坏包被拒 | 过程中出现一次下载字节与清单不符（偶发），应用当场拒绝并报原因，未安装；重试通过 |
| 测试环境说明 | 模拟器自带 Google Play Protect 会拦「未见过的自签名包」，这是系统级安全门；验证时在这台一次性模拟器上临时关闭、装毕**已恢复**（`package_verifier_enable=1`、Play 商店已启用、未知来源权限还原）。未改动用户任何真机安全设置 |

## 本版修复清单（外部评审 + 自审）

| 问题 | 证据 |
|---|---|
| **夜间代码页正文几乎不可读**（真 bug） | 夜间正文色 `#0F172A` 压 `#101722` → `#DDE7F5`；删除只有测试在用的死资源；断言改比真实底色；新增真实控件对比度仪器测试 |
| 长 SVG / 大文档被当作纯文本 | 渲染类型改按**根元素**判定（原来只看前 3000 字符），新增单元测试覆盖长文档/自闭合 SVG/含 div 的 XML |
| 读文件失败静默新建空文档 | 读失败即禁用编辑与保存并报错；空流拒绝导入 |
| 「已保存」可能撒谎 | `CanvasDocument.revision` 单调递增 + `DocumentSaveCoordinator` 单飞，旧修订写入被拒 |
| 分享/导出文件名未转义 `#`/`%` | 新增 `ShareExporter`（转义 + 唯一名 + 收窄 MIME） |
| XML 预览格式化会改写语义 | 仅在结构安全时格式化（无文本内容/CDATA/属性内 `>`），否则原样；加深度与长度上限 |
| 预览渲染进程崩溃拖垮应用 | 实现 `onRenderProcessGone`，拆除死 WebView 并可一键重建；相关调用补空保护 |
| 空白抓帧盖掉类型封面 | `PreviewThumbnailCache.looksBlank`：仍是白底就不存/不显示；新增 4 项仪器测试 |
| 主页底部页脚式更新入口 | 移到「我的文件」标题行 |
| 方格卡片固定 152dp 裁掉时间 | 改 `wrap_content` + `minHeight`，实拍确认时间行完整 |
| 编辑器上部工具栏过厚 | 58/48/48dp → 52/44/44dp |
| 撤销栈无内存上限 | 加总字符预算，超限丢最旧快照 |
| 导出/分享文案含糊 | 改「导出源码文件」「分享源码」 |

---

# v0.1.3 验收记录（界面重做 · 历史）

- 正式签名版：**75823 字节（约 74.0 KiB）**，Android 8.0+（minSdk 26），versionCode 4，versionName 0.1.3。
- APK SHA-256：`fef30aeca0fbedc5051b8992f7741b615b9a7268c386a3f2fa97af313963afc9`。
- 签名证书 SHA-256 `a6f25e1a…f553d19b6`，与 0.1.0 / 0.1.1 / 0.1.2 一致 → 老用户可原地升级。
- 单元测试 **47 项 0 失败**（`--rerun-tasks` 全量重跑）。
- 模拟器仪器测试 **20 项 `OK (20 tests)`**（`am instrument`，emulator-5556）。
- 截图证据：`docs/evidence/v0.1.3/`，浅色 + 夜间，覆盖主页列表/方格、编辑器代码/预览、更新横幅。

## 本版验收标准的修正（重要）

v0.1.2 的"验收通过"只覆盖了对比度数值、测试全绿、不崩溃——**这是无障碍与功能验收，不是画面验收**。用户逐屏查看模拟器真实画面后否决了那个结论，指出的问题（白底与近黑画布硬切、内容挤顶导致下方大片死空、图标被灰方框包裹、多套高饱和色互相抢焦点、蓝色版本号常驻主页）**全部不在原有测试的覆盖范围内**。

因此本版把"渲染后的真实截图逐屏复核"作为独立且必需的验收关卡，并把每条修正对应到截图证据。**只有数值达标不再视为界面验收通过。**

## 本版修复清单

| 问题 | 证据 |
|---|---|
| 内置示例：青绿+近黑、内容挤顶、下方 70% 死空 | 重做为垂直居中、浅/深自适应、单一强调色，文案不折行 |
| 白 chrome 硬切深色画布 | 预览画布改为圆角卡片落在浅色底上，底色跟随主题 |
| 图标被灰描边框包裹 | 顶栏/工具栏/卡片图标全部改为无框细线，按下态才出现浅底 |
| 分段控件选中态是**方角纯色块**压在圆角轨道上 | 改为 999 圆角胶囊（`bg_segment_item`），轨道 `bg_segment_track` |
| 视图切换用薄荷绿方框 | 改为浅轨道 + 蓝色胶囊，图标随选中态换白（`icon_toggle_tint`） |
| 文件类型标签/封面是紫色 | 标签改中性胶囊；封面重画为单色线性图形（浏览器窗口/贝塞尔曲线/尖括号） |
| 渲染出的白底缩略图把边框盖掉，像缺图标 | 缩略图加 `fg_thumbnail` **foreground** 描边（背景描边会被不透明位图遮住） |
| 底部蓝色版本号常驻，像链接 | 改为中性灰小字，**仅在有新版本时**变主色（`AppUpdater` 同步设置文字颜色） |
| 搜索框无放大镜、生硬描边 | 加 `ic_search`；描边只在获得焦点时出现（保留 `border_strong` 作为真实焦点指示） |
| 编辑器符号栏白占顶部一行 | 移到底部键盘配件位，加横向渐隐提示 |
| "已自动保存"鲜绿色抢焦点 | 改为中性灰 |

# v0.1.2 历史验收记录（功能与无障碍）

- 正式签名版：**70787 字节（约 69.1 KiB）**，Android 8.0+（minSdk 26），versionCode 3，versionName 0.1.2。
- APK SHA-256：`b4325c0491ac59ce4d27655b4449ba9e2405f5de15ec2df236b7071b99e61634`。
- `apksigner verify --print-certs`：APK Signature Scheme v2 通过；证书 SHA-256 `a6f25e1a490efbf985d753d216ffdbaeea360a9ab4b57ad2c156eabf553d19b6`，与 v0.1.0 / v0.1.1 相同（老版本可覆盖安装、保留作品）。
- `testDebugUnitTest --rerun-tasks`：**47 项、0 失败、0 错误、0 跳过**。
- 全量 Android 仪器测试：**OK (20 tests)**，独立 QA 模拟器 emulator-5556（Android 17），用 `adb shell am instrument` 直接执行并核对总数与每项结果。
- 截图：[docs/evidence/v0.1.2](evidence/v0.1.2/)（日夜主页列表 / 方格、编辑器工具栏、预览、命名弹窗，均来自实际签名正式版）。

## 本版修掉的真实缺陷

1. **方格模式必崩**：`item_document_card.xml` 没有 `txt_doc_time`，`MainActivity$DocumentAdapter.getView` 无条件 `txtTime.setText(...)` → 切到方格就 `NullPointerException` 崩溃（logcat 实测栈已留存）。修法：补齐控件 + 对卡片所有字段加空值保护，并新增密度/方格仪器测试防回归。
2. **禁用态主按钮文字几乎看不见**：白天填色 `#9AA8BC` 配 `canvas_on_primary` 白字只有 2.41:1。修法：禁用态改 `canvas_disabled_fill` + 新增 `text_on_primary_button` 颜色选择器（禁用时用 `canvas_disabled_ink`），深浅色实测 5.07:1 / 7.50:1。
3. **功能描边不足 3:1**：白天 `border_strong` 压底色 2.41:1、夜间压 `surface_subtle` 2.67:1，且旧测试把这几条断言删掉了。修法：白天改 `#74849B`、夜间改 `#63768F`，并把断言补回（`functionalBoundariesStayVisibleInBothThemes`）。
4. **测试被削弱 / 过期**：`PaletteLogicTest` 的交叉校验下限从 10 被改成 5（实际每个主题能校验 39 个真实色值），已恢复为 ≥30；`NamingDialogThemeContrastTest` 硬编码了 v0.1.1 的旧色值导致假失败，改为断言真实意图（日夜必须解析出不同表面 + 不透明 + 文字/提示 ≥4.5:1）。

## 覆盖的真实操作

- 真实渲染视图的文字对比度：遍历主页所有可见 TextView，按最近的不透明背景用 WCAG 公式测量，任一 <4.5:1 即失败；按需跳过自带 shape 背景的按钮/标签（那类配对由专门的按钮用例测量）。
- 主页紧凑列表 / 两列方格：默认视图、一屏完整可见的条目数、两列列数、切换后跨 Activity 重启记忆、切回列表。
- 代码 ↔ 预览：首次渲染计数、内容未变反复切换 20 次不得重载、改内容必须重载、加载遮罩必须消失、WebView 必须真的装载了内容。
- 编辑器工具栏：真实点击「查找/替换」→ 对话框内输入 → 全部替换生效 → 撤销回原文；真实点击 `{}` 并确认插入位置与光标位置。
- XML 结构视图：不占用 WebView、不显示加载遮罩、立即给出结构化内容与校验结论。
- 粘贴与多段代码选择、保留原文、剪贴板、缩略图缓存、命名框日夜对比度、状态栏图标。

## 更新链路验收

- `scripts/release.py` 只从**磁盘上真实存在的 APK** 计算字节数与 SHA-256，不接受手填。
- 已发布资产 `code-canvas-0.1.2.apk`（70787 字节）的 GitHub digest 为 `sha256:b4325c04…e61634`，与本地文件、与 `docs/update/latest.json` 三者完全一致。
- 从清单里的 `apkUrl` 公网实际下载：HTTP 200、一次跳转到 GitHub 资源主机、70787 字节、SHA-256 与清单一致（**MATCH**）。
- 应用侧只接受正向递增的 `versionCode`，下载主机限定本仓库 Release / GitHub 资源域名，校验字节数、SHA-256、包名、versionCode、versionName 与签名证书后才调用系统安装器。

### 真机（模拟器）实测更新链路

1. 装已发布的 0.1.2 → 底部显示 **「代码画布 0.1.2 · 已是最新版本」**：证明真机确实从 `raw.githubusercontent.com` 取到了线上清单并完成解析与版本比较。
2. 用**同签名、versionCode=2 的本地测试夹具**（仅本地构建，从未上传/发布）替换安装 → 底部变为 **「新版本 0.1.2 · 点击更新」**：证明「发现新版本」判定正确。
3. 点击 → 弹出说明框，正文是清单里真实的 `notes` 与字节数（[截图](evidence/v0.1.2/update_offer_dialog.png)）；点「下载并安装」后从公网真实资产下载并完成校验。
4. 首次安装未知来源应用被正确拦下，给出「安装包已校验。请在系统设置中允许代码画布安装应用，然后返回继续」的说明（[截图](evidence/v0.1.2/update_installer.png)）。
5. 授权后应用自动继续，**系统安装器弹出「Update this app?」并识别为同一应用的就地更新**（[截图](evidence/v0.1.2/update_system_installer.png)）——说明大小 / SHA-256 / 包名 / versionCode / versionName / 签名证书全部通过。

**未通过的一步（如实记录）**：点「Update」后，模拟器上的 **Google Play Protect 直接拦截**（先提示扫描，扫描后判定 "Harmful app blocked"），最终没有完成系统安装。这是系统级安全门，应用既无法也不应该绕过（本版不会去关闭 Play Protect 或未知来源设置）。自签名侧载包在装有 Play Protect 的设备上首次安装会出现这一提示，属正常现象。

- 因此：**应用侧到「把校验通过的 APK 交给系统安装器」为止已全链路验证**；「系统安装器真正写盘完成」这一步被模拟器的 Play Protect 拦下，未取得成功证据。国内 ROM（如 HyperOS 多不带 Play Protect）通常不会出现该拦截，但仍需真机确认。

## 功能边界（本版不变）

- 普通 XML 只提供结构/文本与语法校验，**不是** XSLT、Android XML 布局或任意 XML 的可视化运行器。
- 默认离线，JS 独立关闭；用户确认后只允许当前页面联网。禁止本地 file/content 访问与 HTTP 明文资源。
- 任意文件后缀可保存；不代表能执行 Python、Node.js、Vue/React 等需要编译/运行环境的项目。
- 需要相对图片、外部样式等资源的代码不能靠粘贴自动补齐；建议让 AI 输出单个自包含 HTML。
- 导入与剪贴板粘贴上限 2MB；大文件性能不作为本版保证。
- **仍未在小米 Turbo 3 / HyperOS 实机验证**；输入法、手势、高刷动画与更新安装需要实机试用。
- 模拟器短滑动掉帧采样仍不足以定量评价流畅度；本版只声明「不重载、不白屏、有过渡」，未声明帧率。

---

# v0.1.1 历史验收记录

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
- 原生 ListView 复用卡片、后台准备哈希/大小/行数；主页不为每张卡启动 WebView；代码/预览保留轻量过渡动画。未宣称真实手机上的帧率指标。模拟器短滑动采样掉帧较多，系统 Launcher 对照同样严重，因此**流畅性未通过定量验收**，须真机验证；见 [滚动探测记录](evidence/v0.1.1-scroll-probe.md)。

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
