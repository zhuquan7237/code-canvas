# 代码画布 0.6.1

versionCode 61 · versionName 0.6.1 · minSdk 26

## 这一版有什么

- 首页格子是 16:10。SVG 缩略图放下整张 viewBox，等比缩放，空出来的地方用画面四角的颜色填上。不再自动裁到内容中间，也不把图拉变形。
- HTML 缩略图按 390×844 CSS 像素画第一屏，加载完成后再从顶部裁成格子的比例。
- 样例 `docs/samples/pelican-ride.html`（约 50 KB）有海滩、自行车、鹈鹕、顶部信息卡、底部按钮、CSS 动画和定时器。`docs/samples/wide-ride.svg` 是 1200×400 的宽图，带渐变和动画。
- 网页预览默认允许脚本。点按钮、在页面里滑动、双指缩放和全屏互不抢手势。
- 列表滑动时只使用已经在内存里的缩略图，WebView 不留在列表窗口里，解码尺寸不超过格子。

## 滑动测量

在不可调试的 release 包上执行了 `cmd package compile -m speed`。模拟器的渲染是 Skia OpenGL，`ro.hardware.egl` 为 emulation。

格子列表 148 帧：p50 53ms，p90 81ms，约 59% 超过 16ms。侧栏 103 帧：p50 61ms，p90 77ms。这两次滑动的 bitmap upload 都是 0。能记录到的 GPU 时间大多在 9–25ms；其余帧没有 GPU 完成时间，gfxinfo 把它们记进 4950ms 的桶里，所以总的 p90 过不了 20ms。这一版没有把模拟器上的帧时间压进目标。
