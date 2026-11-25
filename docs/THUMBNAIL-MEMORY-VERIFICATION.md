# Android 首页缩略图缓存验证

本轮基线：远端 `main`、本地 `main` 和开始时 HEAD 均为
`110e8d84cf78344e0a0285178f2c8d600f1b72d2`（已用 `git ls-remote origin refs/heads/main` 核对）。
交付目标为 main 的 draft PR；不合并、不发版。

## 改动与追踪

- WebView 回填：`captureWhenReady()` 原有 `lacksContent(framed)` 通过后，将相同像素的副本用 `memoryPutContent()` 入缓存，复用已有结论。编辑器捕获仍只经 `store()` 写磁盘，后续由首页解码入内存。
- 文本卡片：`TextTile.draw()` 的副本经 `memoryPut()` 在入内存时判定一次；拒绝的副本回收，不参与可见卡片刷新。
- 磁盘解码：保留后台解码、原有空白判定和坏缓存删除，通过后用 `memoryPutContent()` 入缓存。
- 内存缓存仅保存内容判定通过、入缓存后不再修改的位图。`memoryGet()` 只查缓存及回收状态；回收项移除并作为 miss。缓存条目固定记录入缓存时的字节数，避免回收后移除导致 LruCache 大小记账失真。`bindThumbnail()` 命中即显示并返回，不扫描、不缩放采样、不提交磁盘解码。已绑定但回收的位图也回落类型占位图。
- 格子、列表、侧栏继续共用绑定路径；移除 `DrawerAdapter.getView()` 未使用的重复查询。`paintVisible()` 复用相同缓存约束，并检查缩略图开关。
- `PreviewThumbnailCache` 的白色/纸色规则、缓存键、16:10 framing、占位图、回填调度、滚动中只读内存和异步卡片 key 校验不变；图片、GIF、PDF 的原解码与内容例外不变。

新增 `ui.ThumbnailMemoryTest` 七项仪器测试，覆盖两种入缓存方式的同一位图重复绑定（idle/fling）、空白捕获及缺失项回落类型占位图、已回收项、关闭缩略图及可见卡片刷新。重复绑定测试关闭解码 executor，并用仅测试中修改像素的哨兵证明绑定复用入缓存判定。生产代码不修改缓存位图。
原 `ThumbnailCaptureTest` / `ThumbnailBackfillTest` 的 480px 旧断言更新为当前实际存储的 320×200，未放宽空白或颜色断言。

## 本轮实际结果

| 检查 | 结果 |
| --- | --- |
| JDK | mise 安装 Temurin 17.0.21+2，`java -version` 确认 |
| `./gradlew testDebugUnitTest assembleDebug` | **失败**：配置阶段解析 AGP 8.7.3 依赖时，Aliyun 镜像 `gradle-settings-api-8.7.3.pom` 返回 HTTP 502，并报告另外 54 项同类失败；未执行测试或生成 APK |
| SDK / 设备 | 未配置 Android SDK 35，`android info --no-metrics` 报告的 `/home/linear/Android/Sdk` 不存在；无可用 adb。本轮未使用模拟器或真机，已停止环境排障 |
| 独立 JDK/JUnit 检查 | `ThumbnailFrameTest`、`PreviewKeyTest`、`SvgGeometryTest`：**20 项通过**；绕过 Android Gradle 配置，仅验证这些纯 Java 类，不等于 Gradle 测试通过 |
| 样例几何检查 | 临时 Java 检查读取 `wide-ride.svg` 的实际 1200×400 viewBox，经现有 fitScale 得到约 320×106.667；读取 `pelican-ride.html`（49700 字符），现有 390×844 首屏配置的 topCrop 为 `[0,0,390,244]`。仅几何检查，未渲染 WebView |
| diff | `git diff --check` 通过 |
| 模拟器 | 未执行：新增/相关仪器测试、样例视觉验收、滚动/停滚回填/开关、截图录像、A/B |
| 真机 | 未执行同上；未取得任何帧数、刷新率、p50、p90、>16ms 或 GPU 性能数据 |

没有本轮 Android UI 截图或录像可上传；须在有设备时补抓真实界面和交互。`docs/RELEASE-v0.6.1.md` 的历史数据仅作参考，不代表本轮实测；p90 ≤20ms 不是本轮门槛。

独立 JUnit 实际命令（依赖放 `/tmp`，不改构建配置）：

```bash
mkdir -p /tmp/fan-5-junit/classes
curl -fL --max-time 30 https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar -o /tmp/fan-5-junit/junit.jar
curl -fL --max-time 30 https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar -o /tmp/fan-5-junit/hamcrest.jar
javac -cp /tmp/fan-5-junit/junit.jar:/tmp/fan-5-junit/hamcrest.jar -d /tmp/fan-5-junit/classes \
  app/src/main/java/com/nous/codecanvas/util/{ThumbnailFrame,PreviewKey,SvgGeometry}.java \
  app/src/test/java/com/nous/codecanvas/util/{ThumbnailFrameTest,PreviewKeyTest,SvgGeometryTest}.java
java -cp /tmp/fan-5-junit/classes:/tmp/fan-5-junit/junit.jar:/tmp/fan-5-junit/hamcrest.jar \
  org.junit.runner.JUnitCore com.nous.codecanvas.util.ThumbnailFrameTest \
  com.nous.codecanvas.util.PreviewKeyTest com.nous.codecanvas.util.SvgGeometryTest
```

## 有 SDK / 设备后的复测

以下为待执行步骤，不是本轮结果。使用 JDK 17，配置包含 platform 35、构建工具和 platform-tools 的 SDK，将其 platform-tools 加入 PATH。仅在专用测试安装中替换测试数据；模拟器、真机各自记录结果，不混合汇总。

```bash
java -version
adb devices -l
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.nous.codecanvas.ui.ThumbnailMemoryTest,com.nous.codecanvas.ThumbnailBlankTest,com.nous.codecanvas.ThumbnailBackfillTest,com.nous.codecanvas.ThumbnailCaptureTest com.nous.codecanvas.debug.test/android.test.InstrumentationTestRunner
```

核对测试 runner 的最终通过/失败和每项结果。新增仪器测试本轮尚未编译或运行。

1. 导入 `docs/samples/wide-ride.svg` 与 `pelican-ride.html`，打开缩略图，依次查看格子、列表、侧栏。格子为 16:10；SVG 完整等比显示整个 1200×400 viewBox，不能裁两端或拉伸；HTML 显示第一屏的顶部区域，不能中间裁切或把整页压缩进去。分别保存实际截图。
2. 使用至少 30 个内容/颜色/编号不同的 HTML、SVG、文本作品，快速上下往返滚动格子、列表、侧栏。核对标题和图像对应，特别是异步任务完成时已复用的卡片不能串图；录屏保留过程。
3. 删除部分测试缩略图后重开首页，立即滚动，确认滑动中保留内存命中或类型占位图，不启动 WebView/磁盘解码；停下后确认可见卡片逐步回填。用调试器方法断点或 profiler 确认已命中的重复绑定不进入 `lacksContent()` / `decodeThumbFile()`。独立记录冷缓存与热缓存，不能将其混作 A/B。
4. 在设置中关闭缩略图，返回首页并切换格子、列表、侧栏；已有内存缓存也只显示类型占位图，无后台回填。重新开启后停滚恢复。空白 HTML 捕获最终仍显示 HTML 类型占位图；回收缓存位图的自动回归见新仪器测试。
5. 用 `adb exec-out screencap -p > /tmp/thumbnail-grid.png` 等命令抓原始 PNG；交互用 `adb shell screenrecord /sdcard/thumbnail-scroll.mp4`，停止后 `adb pull`。检查截图及录像帧，再通过 `upload_media_asset` 上传，在 PR 嵌入截图并逐行链接录像。

## 同设备 A/B：格子、侧栏各至少三轮

两份 APK 均使用 **debug**（同一签名、包名 `com.nous.codecanvas.debug`）。先完成自动化测试，再准备性能数据，防止测试活动干扰采样。不混用 v0.6.1 历史 release 数据。

```bash
git worktree add --detach /tmp/code-canvas-baseline 110e8d84cf78344e0a0285178f2c8d600f1b72d2
(cd /tmp/code-canvas-baseline && ./gradlew assembleDebug)
./gradlew assembleDebug
mkdir -p /tmp/fan5-perf
cp /tmp/code-canvas-baseline/app/build/outputs/apk/debug/app-debug.apk /tmp/fan5-perf/baseline.apk
cp app/build/outputs/apk/debug/app-debug.apk /tmp/fan5-perf/candidate.apk
git rev-parse HEAD > /tmp/fan5-perf/candidate-sha.txt
adb shell getprop ro.product.model > /tmp/fan5-perf/model.txt
adb shell getprop ro.build.fingerprint > /tmp/fan5-perf/fingerprint.txt
adb shell dumpsys display > /tmp/fan5-perf/display.txt
adb shell wm size > /tmp/fan5-perf/size.txt
adb shell wm density > /tmp/fan5-perf/density.txt
```

记录设备序列号、Android/WebView 版本、实际活动刷新率（从 display 输出核对，必要时系统设置固定）、分辨率、主题、字号和动画设置；两版本保持一致。模拟器另记宿主与渲染后端，真机另记型号及温控状态。

安装 baseline，导入同一组至少 30 个带不同编号/颜色的作品（包含两个原始样例），开启缩略图，保持相同脚本/网络设置。最多每次首页访问回填 12 个，可多次离开/返回首页直到全部完成。格子和侧栏各完整上下预滑动，确认已有缩略图。停止进程后保存同一数据/设置/磁盘缓存快照：

```bash
pkg=com.nous.codecanvas.debug
adb shell am force-stop "$pkg"
adb exec-out run-as "$pkg" tar -cf - files shared_prefs cache/previews > /tmp/fan5-perf/data.tar
```

对 baseline 与 candidate 分别 `adb install -r /tmp/fan5-perf/<版本>.apk`，force-stop 后还原相同快照再启动。两版本均在**同一进程内**用下面相同脚本先预滑动以暖内存，再 reset 并测量；每个界面每轮都从顶部开始。不要在暖缓存和 reset 之间 force-stop，否则测到磁盘冷启动。

```bash
adb shell am force-stop "$pkg"
adb shell "run-as $pkg tar -xf -" < /tmp/fan5-perf/data.tar
adb shell am start -W -n "$pkg/com.nous.codecanvas.ui.MainActivity"
```

手动切到格子（或打开侧栏），滚到顶部；从 `wm size` 记录本设备的 W/H 后设置以下变量。格子 X 为宽度的一半，侧栏 X 为宽度的四分之一，必须位于实际可滚区域内；测试前确认坐标有效，此后两版本固定不变。

```bash
# 示例尺寸须替换为本设备实际值；label 分别取 baseline / candidate。
W=1080 H=2400
label=baseline
surface=grid
X=$((W / 2)) # 侧栏时 surface=drawer，X=$((W / 4))
swipe_round() {
  for n in 1 2 3 4 5 6; do
    adb shell input swipe "$X" "$((H * 4 / 5))" "$X" "$((H / 4))" 250
  done
  for n in 1 2 3 4 5 6; do
    adb shell input swipe "$X" "$((H / 4))" "$X" "$((H * 4 / 5))" 250
  done
  sleep 2
}
swipe_round # 暖缓存；确认返回顶部
for round in 1 2 3; do
  adb shell dumpsys gfxinfo "$pkg" reset > "/tmp/fan5-perf/$label-$surface-$round-reset.txt"
  swipe_round
  adb shell dumpsys gfxinfo "$pkg" framestats > "/tmp/fan5-perf/$label-$surface-$round-framestats.txt"
done
```

每版本重复 grid/drawer，至少留下 12 份原始 framestats；保留 reset 输出、数据快照、APK SHA-256、设备信息及相同脚本。A/B 交错执行可减小热状态影响，若有额外轮次也保留全部原始输出。

逐轮报告总帧数、`Flags != 0` 数、有效完成帧数、缺失完成时间数、p50、p90、>16ms 比例（严格大于 16，不用刷新率阈值替换）。使用原始 PROFILEDATA 的 `FrameCompleted - IntendedVsync`（ns 转 ms），p50/p90 固定用 nearest-rank；标志非零帧单列，不并入正常帧分位数。完成时间为 0、最大整数哨兵或早于 IntendedVsync 的帧计为缺失，不补零。

`GpuCompleted` 缺列、为 0、最大整数哨兵或早于 IntendedVsync 均记 GPU 时间缺失，单列数量/比例。不要把 gfxinfo 汇总中的 4950ms 桶解释为真实 GPU 延迟。若 FrameCompleted 本身有效，该帧仍参与上述端到端统计；GPU 子集只用有效 GPU 时间，不能将缺失样本补零或无说明地丢弃。真实长帧不按任意时长裁掉，报告原始统计及 GPU 有效子集，注明各自样本数。无有效样本则报告 N/A；结果和原始输出一起附到 PR，不能只给分位数。
