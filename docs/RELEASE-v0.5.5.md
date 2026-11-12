## 这一版修的是什么

七项反馈，每一项都定位到根因。

### 1. `.html` 里内嵌单张 SVG：无法缩放、显示不全

你给的那份 `gemini carbon.html` 就是典型：整页只有一个 `<svg viewBox="0 0 1200 675" preserveAspectRatio="xMidYMid slice">`，配 `width:100vw;height:100vh`。

它被当成**网页**预览，而网页预览没有矢量视口。于是 `slice` 按规范把超出部分裁掉——竖屏手机上 16:9 的宽幅画面只剩中间一条；同时没有任何缩放入口。

现在这类文件按**矢量图**预览，走 SVG 视口（适应窗口、双指缩放、拖动、全屏）。关键细节：交给视口的只有 `<svg>` 元素本身，不是整份 HTML——否则会把一整页（含 `<title>`、样式表）嵌进 SVG 舞台里。

边界有测试钉住：带标题、说明文字、两张图、或套在 `<div>` 布局容器里的页面，仍然按网页处理。

实测你那份文件：视口工具栏出现、缩放 100%→140%、图形 403×227 完整落在 419×621 视口内、比例 1.78（正是 16:9）、`slice` 已改写为 `meet`。

### 2. 主页缩略图被压扁

缩略图是按页面**原始比例**抓取的，但显示时被 `centerCrop` 塞进固定方块：宽图丢两边、长图丢上下。改成完整等比显示，超高页面的抓取高度也加了钳制，避免为 44dp 的格子生成巨大位图。

### 3. 检查更新挪到设置页

它原本在主页文件列表的标题行，和文档抢注意力，伸手点文件时容易误触。现在在**设置 → 关于**卡片里，紧挨版本号。主页也不再构建一个自己用不到的网络客户端。

### 4. 一点分享就卡退

`ClipData.newUri(null, null, uri)` 的第一个参数（ContentResolver）传了 `null`。Android 内部会调 `resolver.getType(uri)` 填 ClipDescription，于是主线程直接空指针。

这不是偶发——每次点分享都必崩。堆栈：

```
java.lang.NullPointerException: Attempt to invoke virtual method
'java.lang.String android.content.ContentResolver.getType(android.net.Uri)'
on a null object reference
    at android.content.ClipData.getMimeTypes(ClipData.java:1044)
    at com.nous.codecanvas.util.ShareExporter.chooserIntent(ShareExporter.java:154)
```

修好后实测：系统分享面板正常弹出（"Sharing 1 file / welcome.html"），零 FATAL EXCEPTION。

### 5. 其他应用里没有「代码画布」

两个原因：

- 一个 `<intent-filter>` 里混放了 `scheme`、`mimeType` 和 `pathPattern`。Android 要求过滤器里**所有** `<data>` 都能匹配才算命中，混放等于几乎不匹配任何东西。
- `pathPattern` 只看 URI 路径。聊天软件递来的 `content://` URI 路径通常是一串不透明 id，没有扩展名，所以纯扩展名过滤器永远不命中。

现在按用途拆成独立过滤器（MIME 一组、file:// 扩展名一组），并接受 `*/*` 与 `application/octet-stream`。

实测 `cmd package query-activities` 已能查到 `com.nous.codecanvas.ui.MainActivity`。

### 6. 多文件时启动慢

列表元数据里有三处随文件数线性增长的浪费：

- `content.getBytes(UTF_8)` —— 为了量长度，把整份文本复制一份。
- `content.split("\r\n|\r|\n")` —— 为了数行数，跑一遍正则并生成数组。
- 搜索时每个按键把**全库**正文 `toLowerCase()` 一遍。

全部改成无分配实现（逐字符计算 UTF-8 长度、逐字符数换行），搜索索引按文档缓存一次。

实测 120 个文件：打开 **848ms**（三次采样 765/848/861），主页搜索按键 **169ms**。

### 7. 新应用图标

换成你给的深蓝圆角框 + 亮蓝三角标记。五个密度齐全，含 Android 13+ 主题图标单色层，背景层硬编码白色（不跟随主题，否则浅色启动器上标记会消失）。

实测从已安装 APK 取出图标、套圆形遮罩：图形完整、无裁切、无白边。

## 验证

| 项目 | 结果 |
| --- | --- |
| 单元测试 | 254 项，0 失败 |
| 仪器测试 | 130 项，0 失败 |
| 真机 · 内嵌 SVG | 缩放 100%→140%，图形完整保持 16:9 |
| 真机 · 分享 | 系统面板正常弹出，无崩溃 |
| 真机 · 打开方式 | query-activities 能查到本应用 |
| 真机 · 120 文件 | 打开 848ms，搜索按键 169ms |
| 真机 · 图标 | 圆形遮罩下完整无裁切 |
| 签名 | `a6f25e1a…d19b6`，与 0.1.0–0.5.4 一致，可覆盖升级 |

## 安装

下载 `code-canvas-0.5.5.apk` 覆盖安装，文件与设置都会保留。或在应用内「设置 → 关于 → 检查更新」。

APK SHA-256：`7db305ca90714f422807d29c27a8f84326376415d0e061e21f71a3c20fe8388e`

## 已知限制（未变）

- PDF 不支持应用内翻页，提供「用其他应用打开原文件」，导出保持原始字节一致。
- YAML 只做缩进和括号检查，界面明确标注「未做完整校验」。
- 自签名 APK 首次安装会被 Play Protect 提示扫描，这是 Android 对非商店来源的正常行为。
