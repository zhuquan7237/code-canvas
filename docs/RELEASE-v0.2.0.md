# 代码画布 v0.2.0 · 设置中心与卡片操作

## 这一版做了什么

上一版（0.1.8）把行号栏、自适应图标、空态引导做了出来，但**设置中心一直是缺的**：主题只能跟随系统、没有地方关掉缩略图、缓存占用看不见、清理无从下手。这一版补齐了这些，并把主页卡片的操作从两个图标扩展成一个完整的长按菜单。

### 1. 设置中心（新增）

主页右上角新增设置入口，四个分组：

- **外观** — 浅色 / 深色 / 跟随系统。此前只能跟随系统，无法强制指定。
- **预览** — 允许页面脚本运行（默认开）、主页显示渲染缩略图（默认开）、新文档默认允许联网加载（默认关）。
- **存储** — 缩略图缓存占用实时统计，一键清理。
- **关于** — 版本号取自安装包本身，附离线隐私声明。

**主题实现说明（重要，因为这是个坑）**：本应用是纯原生 Java、零第三方 UI 库，所以没有 `AppCompatDelegate` 可用。框架层面的等价做法是给 Activity 换一个带 `uiMode` 覆盖的 Context，让平台自己去解析 `res/values-night/*` —— 这样配色、状态栏图标、对话框主题会一起翻转，而不是每个界面各自决定"深色"是什么。

第一版实现把这件事放在 `onCreate` 里用 `applyOverrideConfiguration` 做，**实测直接崩溃**：

```
java.lang.IllegalStateException: getResources() or getAssets() has already been called
    at android.view.ContextThemeWrapper.applyOverrideConfiguration
```

原因是 `Activity.performCreate` 在 `onCreate` 之前就已经读过资源了，而且这个 API 也不能调用两次（`recreate()` 会再次触发）。正确做法是重写 `attachBaseContext`。这一条已写进技能备忘，避免下次再踩。

### 2. 卡片长按菜单（新增）

长按任意作品卡片，可进行：**打开 / 重命名 / 复制全部代码 / 导出到文件 / 分享源码 / 删除**。

此前重命名必须先进编辑器，导出也只能在编辑器里做。**"删除"用危险色（`canvas_danger`）标注**，其余为常规正文色——菜单里唯一不可撤销的操作必须一眼可辨。

**长按实现说明（同样是个坑）**：长按处理器必须注册在 `ListView` 自己身上（`setOnItemLongClickListener`）。挂在卡片行 view 上时，如果行内的容器 view 是 `clickable`，它会吞掉触摸事件流，`AbsListView` 根本没机会判定"这是一次长按"，表现就是长按菜单永远不出现——和"功能没做"完全一样。实测确认后，行 view 自身的 `setOnClickListener` 可以保留（回归测试靠它驱动），冲突来自那个可点击的子容器。

### 3. 缩略图开关真正生效

关闭"主页显示渲染缩略图"后：主页卡片立刻回退到类型封面，后台的 `ThumbnailBackfiller` 也不再排队生成新缩略图。开关读的是与主页、渲染器同一份共享偏好，不存在"界面关了、后台还在跑"。

## 质量验收

| 项目 | 结果 |
| --- | --- |
| 单元测试 | **85 项全过，0 失败**（含 6 项新增的 `AppearanceManagerTest`） |
| 模拟器仪器测试 | **41 项全过，0 失败**（14 个既有类 + 新增 `SettingsActivityTest` 7 项、`DocumentActionsTest` 2 项） |

### 这一版修掉的三个真实缺陷

均为**实机运行后才暴露**的问题，不是靠读代码推断出来的：

1. **切换主题直接崩溃** —— `applyOverrideConfiguration` 抛 `IllegalStateException`。修法：改用 `attachBaseContext`。
2. **长按菜单不出现** —— 可点击的子容器吞掉触摸流。修法：移到 `setOnItemLongClickListener`。
3. **设置页底部说明文字贴边** —— 34 个字符挤成一行几乎顶到屏幕右缘，观感局促。修法：缩短文案并加大左右边距。

### 关于既有测试的一次真实回归

新增外观偏好后，4 个既有仪器测试（`CodeCanvasRegressionTestSuite`、`HomeLayoutDensityTest`、`ThemeContrastInstrumentedTest`、`ThumbnailBackfillTest`）开始失败。根因不是这些测试写错了，而是**外观偏好是持久化的全局状态，会跨测试泄漏**：一个测试把主题切成浅色，下一个测试就拿到了它没要求的配色，于是针对深色墨色的断言在渲染完全正确的情况下失败。

修法是让测试自己保证隔离（新增 `TestAppearance.resetToSystem`，在断言颜色或主题派生 key 的测试里回到出厂默认"跟随系统"），**没有为了让测试变绿而放宽任何阈值**。

## 已知限制（未做到的部分）

- **深色/浅色切换后，主页需要重建一次才变色**（`onResume` 检测到偏好变化触发 `recreate()`）。视觉上是一次轻微的刷新，不是闪屏。
- **`docs/plans/` 里的设计规范与审计文档部分仍是纸面方案**，未全部落进代码。这一版落地的是设置中心、长按菜单、缩略图开关三项；无障碍、动效、组件库等规范文档已产出但属于后续迭代的输入。
- 长按菜单未做多选批量操作。

## 安装

- 自建源（国内直连）：https://relay.zhuquan.xyz/dl/code-canvas-0.2.0.apk
- GitHub Release：https://github.com/zhuquan7237/code-canvas/releases/tag/v0.2.0

签名证书与 0.1.0 起各版本一致，可直接覆盖安装，已有数据不丢。
