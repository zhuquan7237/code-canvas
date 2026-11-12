# CodeCanvas 发布签名（备份）

**这个目录不要提交到公开仓库**（含私钥）。仅作本地/离线备份。

| 项 | 值 |
|---|---|
| Keystore | `code-canvas-signing.jks` |
| 格式 | PKCS12 |
| storePassword | `codecanvas123` |
| keyAlias | `code-canvas` |
| keyPassword | `codecanvas123` |
| 证书主体 | CN=CodeCanvas, OU=Dev, O=Nous, L=Beijing, ST=Beijing, C=CN |
| 算法 | SHA256withRSA / 2048-bit RSA |
| 有效期 | 2026-10-01 → 2054-02-16 |
| SHA1 | 17:51:59:4D:60:27:C1:52:42:EA:12:6F:03:AE:08:51:37:F4:1C:4F |
| SHA256 | A6:F2:5E:1A:49:0E:FB:F9:85:D7:53:D2:16:FF:DB:AE:EA:36:0A:9A:B4:B5:7A:D2:C1:56:EA:BF:55:3D:19:B6 |
| 文件 SHA256 | cd06fb8a02fea399109b962b0519c1f96521cfab1823bdefca3aa3cf2646df8c |

## 用法

项目根目录 `signing.properties`（已被 .gitignore 忽略）：

```properties
storeFile=D:/hermes_workspace/tools/code-canvas-signing.jks
storePassword=codecanvas123
keyAlias=code-canvas
keyPassword=codecanvas123
```

或用环境变量（CI 用）：

```bash
export CODE_CANVAS_KEYSTORE_PATH=/path/to/code-canvas-signing.jks
export CODE_CANVAS_KEYSTORE_PASSWORD=codecanvas123
export CODE_CANVAS_KEY_ALIAS=code-canvas
export CODE_CANVAS_KEY_PASSWORD=codecanvas123
```

## 警告

- **换签名 = 用户无法覆盖安装**（Android 会报签名冲突），必须卸载重装且丢失本地数据。
- 这个 keystore 丢了就永远无法给已发布版本出更新包，请务必多处备份。
- 密码 `codecanvas123` 强度很低，仅防误用不防破解；不要把这个文件放进任何公开仓库/网盘直链。
