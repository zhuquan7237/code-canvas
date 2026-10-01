# 代码画布 · CodeCanvas

把 AI 给出的代码，变成看得见的作品。轻量原生 Android 应用，使用系统 WebView，不打包浏览器内核。

[下载最新 APK](https://relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk)（自建源，国内可直连）· [GitHub Release](https://github.com/zhuquan7237/code-canvas/releases/latest) · [构建状态](https://github.com/zhuquan7237/code-canvas/actions) · [验收与边界](docs/VERIFICATION.md)

## 怎么用

1. 在 AI 聊天里复制 HTML、SVG 或 XML 代码。
2. 点 **粘贴并预览**，不必先理解文件后缀。
3. 多段代码分别选择，不擅自拼接 HTML/CSS/JS。
4. 点作品名称重命名；导出为标准文件，其他编辑器和浏览器也能使用。

已有作品默认打开预览，需要修改时再切「代码」。空白新建仍直接编辑。主页不显示代码片段；打开 HTML/SVG 预览后缓存真实封面，下次回主页更容易认出作品。封面为预览首屏而非实时网页，修改或切换主题后旧封面失效，临时缓存最多 40 张，系统可清理。

## v0.1.1 打磨

- 修复命名/重命名黑底黑字：主题、输入框、提示、按钮统一适配深浅色。
- 首页突出「粘贴并预览」，隐藏源码片段；作品点开先看效果。
- 提取 AI 回复的 Markdown 代码块，支持多段选择和保留原文；不拆源码内部的 Markdown 示例。
- 粘贴到已有作品时选择追加或覆盖，覆盖可撤销；语法高亮保留完整选区。
- 预览支持刷新；长按「代码」标签复制全部源码。
- 对 CDN、缺失本地资源、需要编译的代码给出持续可读的说明。
- 保存使用不可变快照与原子替换，多页面并发不丢其他作品；清空作品后不再自动出现示例。

## v0.1.4 本次更新（应用内更新 + 外部评审修复）

**更新不再需要去仓库拉。** 本版起版本清单与安装包都优先走自建源 `relay.zhuquan.xyz`（Cloudflare 前缀，国内可直连），GitHub 只作兜底；下载后校验大小、SHA-256、包名、版本号与签名，再交给系统安装器。**0.1.3 及更早的版本只认 GitHub，所以要手动装一次这一版，之后才能用上应用内更新。**

这一版的问题清单来自外部模型的**全维度评审**（逻辑 / 页面 / 功能 / 美观 / 交互 / 实际体验 / 更新方案），逐条核实后才改；我的逐屏实拍自审也额外抓到一个评审没提到的缺陷。

- **夜间代码页正文几乎不可读（P0，真 bug）**：夜间正文色是近黑 `#0F172A` 压在 `#101722` 的代码底上；更糟的是对比度测试比的是**没人用的死资源**（夜间仍为浅色），所以测试一直是绿的。改色、删死资源、断言改比真实底色，并新增**真实控件**对比度仪器测试。
- **长 SVG / 大文档被当成纯文本**：渲染类型原来只看前 3000 字符里有没有 `</svg>`，长文档的结束标签在窗口外。改成按**根元素**判定。
- **读文件失败不再静默新建空文档**；保存引入 `revision` 单调递增 + 单飞协调器，写入前校验，旧修订号的写入被拒——「已保存」不再撒谎。
- **分享/导出**：文件名转义 `#`/`%` 等 URI 保留字符、保证唯一、收窄 MIME；文案改成「导出源码文件 / 分享源码」。
- **XML 预览不再改写语义**：只在结构安全（纯元素嵌套、无文本内容、无 CDATA）时才格式化，否则原样展示；并加深度与长度上限。
- **预览渲染进程崩溃/被回收不再拖垮应用**：补 `onRenderProcessGone`，拆掉死 WebView 并支持一键重建预览；所有 WebView 调用补空保护。
- **空白抓帧不再盖掉类型封面**：抓帧先铺白底，页面没画完就抓会存下纯白图，主页那行就变成白框。现在「还是白底」判定为没画，不存也不显示（纯色帧是真内容，保留）。
- **主页**：更新入口从底部挪到「我的文件」标题行，底部不再有页脚式按钮；**方格卡片**固定 152dp 会裁掉时间行，改 `wrap_content` + `minHeight`；**编辑器**上部工具栏 58/48/48dp 压到 52/44/44dp；**撤销栈**加总字符预算，大文档不再吃上百 MB。

哪些是评审指出的、哪些我**没做到**（行号栏、真机测试、若干 P1/P2 视觉项、内置证书指纹）逐条写在 [docs/RELEASE-v0.1.4.md](docs/RELEASE-v0.1.4.md)。

## v0.1.3 更新（界面重做）

**这一版不加功能，只修"上一版界面根本不能交付"这件事。**

上一版的验收只对了对比度数值、测试全绿和不崩溃——那是无障碍与功能验收，不是画面验收。把模拟器真实画面逐屏看下来，一堆问题：白底工具栏直接硬切到近黑画布、内置示例内容挤在顶部而下面七成是死空、每个图标被一圈灰方框包着、一屏里蓝/薄荷绿/紫色各自抢焦点、蓝色版本号常驻主页底部。这些测试一条都测不到。

- **内置示例重做**：原来是青绿 + 近黑、内容挤顶、下方大片空白。现在是垂直居中的极简页，浅色/深色各自适配，只用**一个**强调色；文案缩短到一行不再孤字折行；示例里直接写明"脚本开关关闭时按钮不会响应"，不再自相矛盾。
- **预览画布**：由顶到屏幕边缘的深色块改为**落在浅色底上的圆角卡片**，底色跟随主题——白 chrome 与画布之间不再生硬硬切。
- **统一细线条语言**：顶栏 / 工具栏 / 卡片上的图标**全部去掉灰色描边框**，只在按下时出现浅色底；分段控件选中态由"方角纯色块"改为 999 圆角胶囊（原来那方块必然溢出圆角轨道）；主页视图切换由薄荷绿方框改为浅轨道里的蓝色胶囊。
- **主页**：搜索框补上放大镜、去掉常驻描边（只在获得焦点时描边）；文件类型标签去掉紫色改中性；卡片信息从三行压成两行；底部版本号从蓝色大字改为安静的中性灰小字，**只有真的发现新版本才变主色**。
- **文件图标重画**：三个类型的封面改为单色线性图形（浏览器窗口 / 贝塞尔曲线 / 尖括号）——原来三个都是紫色，其中一个几乎看不见。缩略图加了一层 foreground 描边，避免白底页面渲染图把边框盖掉、看起来像图标丢了。
- **编辑器**：符号条移到底部（键盘弹出时正好在键盘上方），不再白占顶部一行，并加横向渐隐提示；撤销/重做/粘贴统一为无框样式，"已自动保存"由鲜绿改中性灰。
- 验证：单元测试 47 项、模拟器仪器测试 20 项全部通过，另按浅色/夜间逐屏截图复核（见[截图](#截图)）。

## v0.1.2 更新

- **颜色系统重做**：深浅色各自一套完整调色板，正文/次要文字/图标/状态色按 WCAG 2.1 AA 真机实测（正文 ≥4.5:1，功能图标与描边 ≥3:1）。修掉了「文字和背景颜色差不多」的老问题；禁用态按钮改为浅灰底 + 深灰字，不再出现白字压在浅灰底上看不清。
- **主页可切布局**：紧凑列表（默认）/ 两列方格，选择会记住。列表一行约 76dp，一屏能看到 6 个以上文件；卡片右上角「⋮」集中放编辑 / 分享 / 删除，方格模式曾一进主页就崩溃，本版已修。
- **代码 / 预览不再白屏闪**：内容没变就不重新加载 WebView，渲染完成后才撤掉加载遮罩，切换用 160ms 位移过渡并遵守系统「关闭动画」设置；加载超时 15 秒会给出提示而不是一直遮着。
- **编辑器工具栏**：撤销 / 重做 / 直接粘贴，加一条可横向滑动的常用符号条（Tab、`<`、`>`、`/`、`=`、`"`、`{}`、`()`、`[]`，配对符号把光标留在中间），以及查找/替换（上一处、下一处、替换、全部替换，可撤销）和「更多」（全选、复制全部代码、跳转到行）。
- **应用内检查更新**：默认每 6 小时静默检查（可在「检查更新」上长按关闭），主页底部入口显示已装版本和检查结果。下载前需你确认；下载后校验大小、SHA-256、包名与签名一致，才交给系统安装器。**0.1.1 没有更新器，需要手动安装一次。**
- 验证：JVM 单元测试 47 项、模拟器仪器测试 20 项全部通过。

## 功能与边界

- 新建、任意后缀、重命名、搜索、自动保存、粘贴、撤销/重做、系统文件导入导出与分享。
- HTML/SVG 可视化渲染，普通 XML 提供格式化文本、语法校验与防 XXE。
- JavaScript 与网络加载默认关闭。需要按钮/动画时开启「启用交互」；需要 HTTPS 图片或 CDN 时点「联网加载」并确认，仅对当前页面生效。不开放本地文件访问，HTTP 明文资源不支持。只开启你信任的代码。
- 不包含 Vue/React 编译器、Node.js、Python 或 XSLT。请让 AI 提供「单个自包含 HTML 文件，CSS/JS 内嵌，不依赖 CDN」。
- 相对路径图片、CSS、JS 不会靠粘贴自动补齐；需要内嵌资源或可访问链接。
- 文件导入上限 2MB，超大剪贴板有明确提示；大文件性能不作为本版保证范围。
- Android 8.0+，需要系统 WebView；厂商输入法和动画体验待实机反馈。

## 构建

JDK 17、Android SDK API 35。Windows 使用 `gradlew.bat`，Linux/macOS 使用 `./gradlew`。

```sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

签名由未跟踪的 `signing.properties` 或环境变量提供，密钥、密码和本机配置不会发布。

```properties
storeFile=/path/to/your-keystore.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_password
```

## 发布（维护者）

应用内「检查更新」按顺序读三个版本清单源：**自建源** `https://relay.zhuquan.xyz/dl/codecanvas-latest.json` → `docs/update/latest.json` 的 raw 地址 → GitHub Contents API。**清单里的哈希、大小、URL 必须来自真实已上传的 APK**，顺序不能颠倒：

1. 升 `app/build.gradle.kts` 的 `versionName` / `versionCode`。
2. `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleRelease`，两套测试必须全绿。
3. 把 APK 放到自建源的**带版本号路径**（内容不变，不会被 CDN 缓存成旧包），并在同一目录写清单：
   ```sh
   scp app/build/outputs/apk/release/app-release.apk root@<server>:/opt/dsh-relay/dl/code-canvas-<版本>.apk
   ```
4. 打 tag 并建 GitHub Release，上传同一个 APK 文件（作为备用下载地址）。
5. 生成清单（写两份：仓库里的兜底清单 + 上传到自建源的那份）并提交推送：
   ```sh
   python scripts/release.py --apk app/build/outputs/apk/release/app-release.apk \
     --version-name 0.1.4 --version-code 5 --tag v0.1.4 \
     --asset-name code-canvas-0.1.4.apk \
     --apk-url https://relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk \
     --fallback-apk-url https://github.com/zhuquan7237/code-canvas/releases/download/v0.1.4/code-canvas-0.1.4.apk \
     --also-out <自建源用清单路径> --notes "本次更新内容…"
   ```
   脚本从文件本身算 SHA-256 与字节数，不接受手填；`latest.json` 只有正向递增的 `versionCode` 才会被应用接受。

## 截图

v0.1.4（[全部截图](docs/evidence/v0.1.4/)）：

![主页列表](docs/evidence/v0.1.4/home_list_day.png)
![主页方格](docs/evidence/v0.1.4/home_grid_day.png)
![主页列表·夜间](docs/evidence/v0.1.4/home_list_night.png)
![编辑器代码](docs/evidence/v0.1.4/editor_code_day.png)
![编辑器代码·夜间](docs/evidence/v0.1.4/editor_code_night.png)
![应用内更新](docs/evidence/v0.1.4/update_offer.png)

v0.1.3（[全部截图](docs/evidence/v0.1.3/)）：

![主页列表](docs/evidence/v0.1.3/home_list_day.png)
![主页列表·夜间](docs/evidence/v0.1.3/home_list_night.png)
![编辑器代码](docs/evidence/v0.1.3/editor_code_day.png)
![预览画布](docs/evidence/v0.1.3/editor_preview_day.png)
![预览画布·夜间](docs/evidence/v0.1.3/editor_preview_night.png)

v0.1.2（[全部截图](docs/evidence/v0.1.2/)）：

![主页列表](docs/evidence/v0.1.2/home_list_day.png)
![主页方格](docs/evidence/v0.1.2/home_grid_day.png)
![深色方格](docs/evidence/v0.1.2/home_grid_night.png)
![编辑器工具栏](docs/evidence/v0.1.2/editor_code_toolbar_day.png)
![命名弹窗](docs/evidence/v0.1.2/naming_day.png)

v0.1.1：

![主页](docs/evidence/v0.1.1/main_light.png)
![命名框](docs/evidence/v0.1.1/naming_light.png)
![SVG 预览](docs/evidence/v0.1.1/svg_preview_light.png)

MIT License。

