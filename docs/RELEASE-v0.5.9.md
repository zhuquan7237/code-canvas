# 代码画布 0.5.9

versionCode 59 · versionName 0.5.9 · minSdk 26

这一版还没有签名包。构建机上没有 `signing.properties`，也没有 `CODE_CANVAS_KEYSTORE_PATH`。不要用 debug 签名代替。

## 这一版有什么

- Markdown 预览补了标题下划线、引用色块、列表和代码块，公式和表格仍离线渲染。浅色和深色都看过。
- JSON 可以折叠对象和数组，点路径复制 `$.a.b`。格式错误会标出那一行，并跳回代码。
- JSONL 坏行单独标出，可以定位到源码行。
- CSV 增加筛选框和点表头排序。宽表仍可横向滑动。
- PDF 点页码弹出跳转。图片和 PDF 沿用双指缩放、双击放大。
- 编辑器菜单增加「查找与替换」：上一处、下一处、替换一处、全部替换。

模拟器 API 34、360dp、字号 1.3。截图在 `docs/screenshots/android/v4-*.png`。

## 本机发布

```sh
cat > signing.properties << 'EOF'
storeFile=/绝对路径/你的.keystore
storePassword=密钥库密码
keyAlias=别名
keyPassword=密钥密码
EOF

./gradlew testDebugUnitTest assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

cp app/build/outputs/apk/release/app-release.apk /tmp/code-canvas-0.5.9.apk
SHA12="$(sha256sum /tmp/code-canvas-0.5.9.apk | cut -c1-12)"

gh release create v0.5.9 /tmp/code-canvas-0.5.9.apk \
  --target main \
  --title "代码画布 v0.5.9 · 预览与查找替换" \
  --notes-file docs/RELEASE-v0.5.9.md

python scripts/release.py \
  --apk /tmp/code-canvas-0.5.9.apk \
  --version-name 0.5.9 --version-code 59 --tag v0.5.9 \
  --asset-name code-canvas-0.5.9.apk \
  --notes-file docs/RELEASE-v0.5.9.md \
  --apk-url "https://relay.zhuquan.xyz/dl/code-canvas-0.5.9.apk?v=${SHA12}" \
  --fallback-apk-url https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.9/code-canvas-0.5.9.apk

git add docs/update/latest.json
git commit -m "release: v0.5.9 update manifest"
git push origin main

export CODE_CANVAS_RELEASE_SSH_KEY=/绝对路径/aliyun-light.pem
export CODE_CANVAS_RELEASE_SSH_HOST=root@8.148.233.40
bash scripts/release.sh
```

密钥必须和 0.1.0–0.5.6 是同一把。`bash scripts/release.sh` 不接收版本号，它读 `app/build.gradle.kts`。
