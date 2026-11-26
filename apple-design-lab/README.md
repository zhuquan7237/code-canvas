# Code Canvas Apple Design Lab · 设计验证样机

这是独立的原生 SwiftUI 界面参考，不是 Android APK 更新、iOS 文件编辑器、App Store 发行包或学生挑战赛作品。测试数据为三个本地绘制的样例。无网页内核、无联网、无账户、无真实文件导入。它不替代现有 Code Canvas 的 HTML/SVG、源码编辑或本地文件安全边界。

## 参考了什么，转成哪些决定

| 官方参考 | 应用到 Code Canvas 的设计决定 |
|---|---|
| [文件与 Quick Look](https://support.apple.com/en-om/guide/ipad/ipadb3b759ed/ipados) | 先浏览作品，再进专注预览；不用把操作按钮堆到每张缩略图。缩略图和详情复用同一画面与比例。 |
| [Freeform](https://support.apple.com/guide/ipad/get-started-with-freeform-ipad9c59637d/27/ipados/27) | 作品是画布的主角；缩放、适应和网格归成单个可预期的控件组；控制条不盖住构图。 |
| [Preview（iPad 用户指南）](https://support.apple.com/guide/ipad/welcome/27/ipados/27) | 预览与编辑动作分层；回列表和缩放永远可发现，真实导入和编辑需要另行实现。 |
| [Materials](https://developer.apple.com/design/human-interface-guidelines/materials) 和 [WWDC26](https://developer.apple.com/videos/play/wwdc2026/102/) | 系统导航优先；自定义玻璃仅用于浮层工具组，用 regular 而不是 clear。真实原生反光、折射和亮边交给系统。作品本身有方向明确的左上光，与 UI 材质分离。减少透明度时用不透明系统色和细边。 |
| [Layout](https://developer.apple.com/design/human-interface-guidelines/layout)、[Typography](https://developer.apple.com/design/human-interface-guidelines/typography)、[UI Design Tips](https://developer.apple.com/design/tips/) | 20 pt 水平边距、16 pt 网格间距、20 pt 连续圆角；根据可用宽度决定列数，而不是查设备名称。系统字阶与语义色；图标按钮 44 pt 目标，大字体允许多行并滚动。 |

不复制 Apple 界面、商标或素材。珊瑚色作为 Code Canvas 的视觉识别，不用于显示危险状态或替代文字。样机不包含正式图标；已附 [珊瑚画笔矢量分层](Icon-Layers)，导入 Icon Composer 并检查系统效果是下一步，不要把展示缩略图当作 App Icon。

## 验证与运行

在 Mac 上以 Xcode 27 打开 `DesignLab.xcodeproj`，选择 `DesignLab` 和 iPhone 或 iPad 模拟器，按 Run；按 Test 运行界面测试。不需要团队证书来运行模拟器。CI 记录构建、系统实际渲染截图以及界面测试。

- 模拟器检查：明暗外观、最大辅助字号、两种设备布局，100–200% 缩放边界、复位、网格、搜索、返回与 About。
- 尚需真机验证：VoiceOver 顺序/读值、自定义点按与键盘操作、各种辅助对比和玻璃透明度偏好、窄窗与旋转。不能用模拟器截图替代这些结论。
- 正式产品回归：至少包括新用户首次打开、作品为空、极长文件名、两种方向/多窗、大字和高对比、原始宽/窄/动画 SVG、HTML 离线交互、确定的错误状态、应用中断并返回。需要真实渲染链路验证，样机不计入这些通过项。

## 如果参赛

[Apple 2027 官方条款](https://developer.apple.com/swift-student-challenge/policy/)：使用合格的原生 Xcode app 或 Swift Playground app；评审离线，内容用英文，需在三分钟内体验；参赛者本人要做出实质贡献、理解代码、自己写申请问答，并完整披露 AI 使用。这套样机的调研、SwiftUI 参考和测试由 AI 辅助完成，不应原样作为参赛作品提交。建议参赛者本人实现一个最能展示原项目价值的离线流程（例如导入一个示例 SVG → 用原始尺寸检查画面边界 → 改代码并验证预览），并保留设计取舍、实现与测试的修改记录。
