# 代码画布 0.5.7

versionCode 57 · versionName 0.5.7 · minSdk 26

手机界面按文件列表重做，PDF 可以在应用内翻页。签名包这一版还没从构建机发出去：仓库里没有 `signing.properties`，也没有 `CODE_CANVAS_KEYSTORE_PATH`。不要用 debug 签名代替。

## 这一版有什么

- 首页是紧凑列表：轻顶栏、右下角「粘贴」、按格式分色。缩略图对准有内容的区域。
- 编辑器顶栏只留返回、文件名、保存和菜单。查找、另存为、分享在菜单里。
- 横屏把代码/预览切换收进同一行。模拟器上内容区约占窗口高度的 79%。
- 「允许页面脚本」和「联网加载」只出现在 HTML。
- PDF 用系统渲染器翻页。不能选字、批注或搜索。
- YAML 按缩进展开并标出行号，界面写明没有完整语法校验。
- 代码文件可以改，只做高亮，不执行。
- HTML、SVG、Markdown（表格、公式、图）、Mermaid、JSON、JSONL、XML、CSV、TXT、PNG、JPEG、WebP、GIF 保持可打开。渲染库在 APK 里，不走 CDN。

界面在模拟器 API 34、360dp 宽、1.3 倍字号下看过。截图在 `docs/screenshots/android/v2-*.png`。

## 还没做的发布步骤

缺两样东西，所以 GitHub Release 和自建源都还没有 0.5.7 的 APK：

1. 与 0.1.0–0.5.6 相同的签名密钥（`signing.properties` 或下面的环境变量）。换一把密钥会让用户无法覆盖安装。
2. 上传自建源用的 SSH 私钥（`CODE_CANVAS_RELEASE_SSH_KEY`）。脚本默认路径是 Windows 上的 `C:/Users/zhuquan/.ssh/aliyun-light-8.148.233.40.pem`，这台构建机上没有。

在有密钥的机器上，于仓库根目录执行：

```sh
cat > signing.properties << 'EOF'
storeFile=/绝对路径/你的.keystore
storePassword=密钥库密码
keyAlias=别名
keyPassword=密钥密码
EOF

./gradlew testDebugUnitTest assembleRelease

# 确认是正式签名，而不是 debug 或未签名
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

cp app/build/outputs/apk/release/app-release.apk /tmp/code-canvas-0.5.7.apk
SHA12="$(sha256sum /tmp/code-canvas-0.5.7.apk | cut -c1-12)"

gh release create v0.5.7 /tmp/code-canvas-0.5.7.apk \
  --target main \
  --title "代码画布 v0.5.7 · 手机界面重做 · PDF 翻页" \
  --notes-file docs/RELEASE-v0.5.7.md

python scripts/release.py \
  --apk /tmp/code-canvas-0.5.7.apk \
  --version-name 0.5.7 --version-code 57 --tag v0.5.7 \
  --asset-name code-canvas-0.5.7.apk \
  --notes-file docs/RELEASE-v0.5.7.md \
  --apk-url "https://relay.zhuquan.xyz/dl/code-canvas-0.5.7.apk?v=${SHA12}" \
  --fallback-apk-url https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.7/code-canvas-0.5.7.apk

git add docs/update/latest.json
git commit -m "release: v0.5.7 update manifest"
git push origin main

export CODE_CANVAS_RELEASE_SSH_KEY=/绝对路径/aliyun-light.pem
export CODE_CANVAS_RELEASE_SSH_HOST=root@8.148.233.40
bash scripts/release.sh
```

`scripts/release.py` 从 APK 文件本身计算 SHA-256 和字节数。`scripts/release.sh` 把同一个 APK 和 `docs/update/latest.json` 传到 `relay.zhuquan.xyz`，并在本机有 Cloudflare 凭据时清缓存。清单提交之前，APK 必须已经在 GitHub Release 上，否则应用内更新的备用地址是空的。

没有环境变量文件时，也可以不写 `signing.properties`，改成：

```sh
export CODE_CANVAS_KEYSTORE_PATH=/绝对路径/你的.keystore
export CODE_CANVAS_KEYSTORE_PASSWORD=密钥库密码
export CODE_CANVAS_KEY_ALIAS=别名
export CODE_CANVAS_KEY_PASSWORD=密钥密码
./gradlew assembleRelease
```
