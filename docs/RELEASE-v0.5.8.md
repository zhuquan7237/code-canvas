# 代码画布 0.5.8

versionCode 58 · versionName 0.5.8 · minSdk 26

这一版还没有签名包。构建机上没有 `signing.properties`，也没有 `CODE_CANVAS_KEYSTORE_PATH`。不要用 debug 签名代替。

## 这一版有什么

- Markdown 和 Mermaid 的首页缩略图不再是白块。后台 WebView 会加载随包的渲染库；图还没好时先显示分色占位。
- 图片和 PDF 的缩略图来自原文件。PDF 能看到第一页上的字。图片显示真实字节数，不再写「0 B · 0 行」。
- 列表在 360dp、1.3 倍字号下能看全大小、行数和时间。今天只显示时刻。
- 右下角「粘贴」留出底部空白，不再挡住最后一行的「⋮」。
- 设置里主题说明改成「浅色、深色，或跟随系统切换」，不再把「切换」拆开。

模拟器 API 34、360dp、字号 1.3 上看过。截图在 `docs/screenshots/android/v3-*.png`。

## 本机发布

缺签名密钥和自建源 SSH 私钥，所以没有 GitHub Release，也没有上传 APK。在有密钥的机器上，于仓库根目录执行：

```sh
cat > signing.properties << 'EOF'
storeFile=/绝对路径/你的.keystore
storePassword=密钥库密码
keyAlias=别名
keyPassword=密钥密码
EOF

./gradlew testDebugUnitTest assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

cp app/build/outputs/apk/release/app-release.apk /tmp/code-canvas-0.5.8.apk
SHA12="$(sha256sum /tmp/code-canvas-0.5.8.apk | cut -c1-12)"

gh release create v0.5.8 /tmp/code-canvas-0.5.8.apk \
  --target main \
  --title "代码画布 v0.5.8 · 缩略图与列表" \
  --notes-file docs/RELEASE-v0.5.8.md

python scripts/release.py \
  --apk /tmp/code-canvas-0.5.8.apk \
  --version-name 0.5.8 --version-code 58 --tag v0.5.8 \
  --asset-name code-canvas-0.5.8.apk \
  --notes-file docs/RELEASE-v0.5.8.md \
  --apk-url "https://relay.zhuquan.xyz/dl/code-canvas-0.5.8.apk?v=${SHA12}" \
  --fallback-apk-url https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.8/code-canvas-0.5.8.apk

git add docs/update/latest.json
git commit -m "release: v0.5.8 update manifest"
git push origin main

export CODE_CANVAS_RELEASE_SSH_KEY=/绝对路径/aliyun-light.pem
export CODE_CANVAS_RELEASE_SSH_HOST=root@8.148.233.40
bash scripts/release.sh
```

`scripts/release.sh` 不接收版本号，它读 `app/build.gradle.kts`。密钥必须和 0.1.0–0.5.6 是同一把，否则不能覆盖安装。
