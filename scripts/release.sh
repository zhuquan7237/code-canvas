#!/usr/bin/env bash
# End-to-end release publisher for CodeCanvas (代码画布).
#
# Synchronizes the signed release APK + latest.json to BOTH:
#   1. Domestic mirror: https://relay.zhuquan.xyz/dl/codecanvas-latest.json + code-canvas-<version>.apk
#   2. GitHub repo & release: zhuquan7237/code-canvas
#
# And purges Cloudflare cache so in-app "检查更新" immediately sees the new version.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GRADLE="app/build.gradle.kts"
VERSION_NAME="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE" | head -1)"

APK="app/build/outputs/apk/release/app-release.apk"
ASSET_NAME="code-canvas-${VERSION_NAME}.apk"
MANIFEST="docs/update/latest.json"

SSH_KEY="${CODE_CANVAS_RELEASE_SSH_KEY:-C:/Users/zhuquan/.ssh/aliyun-light-8.148.233.40.pem}"
SSH_HOST="${CODE_CANVAS_RELEASE_SSH_HOST:-root@8.148.233.40}"
REMOTE_DIR="/opt/dsh-relay/dl"
CF_ZONE="ad700d49156d0c2dec2b1765fe49b152"
HERMES_ENV="C:/Users/zhuquan/AppData/Local/hermes/.env"

if [ ! -f "$APK" ]; then
  echo "Release APK not found at $APK — run ./gradlew :app:assembleRelease first" >&2
  exit 1
fi

echo "==> Uploading ${ASSET_NAME} and codecanvas-latest.json to ${SSH_HOST}:${REMOTE_DIR}"
scp -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new \
  "$APK" "${SSH_HOST}:${REMOTE_DIR}/${ASSET_NAME}.tmp"
scp -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new \
  "$MANIFEST" "${SSH_HOST}:${REMOTE_DIR}/codecanvas-latest.json.tmp"
ssh -i "$SSH_KEY" "$SSH_HOST" "cd ${REMOTE_DIR} && \
  cp ${ASSET_NAME}.tmp code-canvas.apk.tmp && \
  mv -f ${ASSET_NAME}.tmp ${ASSET_NAME} && \
  mv -f code-canvas.apk.tmp code-canvas.apk && \
  mv -f codecanvas-latest.json.tmp codecanvas-latest.json && \
  chmod 644 ${ASSET_NAME} code-canvas.apk codecanvas-latest.json"

if [ -f "$HERMES_ENV" ]; then
  CF_KEY="$(grep '^CF_API_KEY=' "$HERMES_ENV" | cut -d= -f2- | tr -d '\r')"
  CF_EMAIL="$(grep '^CF_API_EMAIL=' "$HERMES_ENV" | cut -d= -f2- | tr -d '\r')"
  if [ -n "$CF_KEY" ] && [ -n "$CF_EMAIL" ]; then
    echo "==> Purging Cloudflare cache for relay.zhuquan.xyz"
    curl -sS -X POST "https://api.cloudflare.com/client/v4/zones/${CF_ZONE}/purge_cache" \
      -H "X-Auth-Email: ${CF_EMAIL}" \
      -H "X-Auth-Key: ${CF_KEY}" \
      -H "Content-Type: application/json" \
      --data "{\"files\":[\"https://relay.zhuquan.xyz/dl/codecanvas-latest.json\",\"https://relay.zhuquan.xyz/dl/${ASSET_NAME}\",\"https://relay.zhuquan.xyz/dl/code-canvas.apk\"]}" \
      | grep -q '"success":true' && echo "    cache purged" || echo "    WARN: cache purge did not confirm success"
  fi
fi

echo "==> Done: v${VERSION_NAME} (versionCode ${VERSION_CODE})"
