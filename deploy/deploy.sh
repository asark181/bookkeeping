#!/usr/bin/env bash
# Usage: [SSH_BIND_INTERFACE=en0] deploy/deploy.sh <ssh-key> [apk]
# Deploys the bookkeeping sync server and updates version/APK on the host.
set -euo pipefail
KEY=${1:?ssh key path required}
APK=${2:-}
HOST=${DEPLOY_HOST:-ubuntu@your-server-ip}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# 站点地址不进 git：deploy/private/site.env 里写 SITE_URL=https://你的域名
SITE_ENV="$ROOT/deploy/private/site.env"
[[ -f "$SITE_ENV" ]] || { echo "缺少 $SITE_ENV" >&2; exit 1; }
SITE_URL=$(grep -E '^SITE_URL=' "$SITE_ENV" | head -1 | cut -d= -f2- | tr -d '[:space:]'); SITE_URL=${SITE_URL%/}
[[ -n "$SITE_URL" ]] || { echo "$SITE_ENV 里没有 SITE_URL" >&2; exit 1; }
SSH_ARGS=(-i "$KEY" -o StrictHostKeyChecking=accept-new)
if [[ -n "${SSH_BIND_INTERFACE:-}" ]]; then
  SSH_ARGS+=(-o "BindInterface=$SSH_BIND_INTERFACE")
fi

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/app" "$STAGE/web/download"
cp "$ROOT/server/app.py" "$ROOT/server/requirements.txt" "$STAGE/app/"
cp "$ROOT/deploy/bookkeeping.service" "$ROOT/deploy/bookkeeping-backup.cron" "$STAGE/"

if [[ -n "$APK" ]]; then
  cp "$APK" "$STAGE/web/download/bookkeeping.apk"
  VERSION_CODE=$(grep -E 'versionCode *=' "$ROOT/android/app/build.gradle.kts" | grep -oE '[0-9]+' || echo "1")
  VERSION_NAME=$(grep -E 'versionName *=' "$ROOT/android/app/build.gradle.kts" | grep -oE '"[^"]+"' | tr -d '"' || echo "1.0.0")
  SIZE=$(du -m "$APK" | cut -f1)
  SHA=$(sha256sum "$APK" | awk '{print $1}')
  cat > "$STAGE/web/version.json" <<JSON
{"versionCode": $VERSION_CODE, "versionName": "$VERSION_NAME", "url": "$SITE_URL/", "apkUrl": "$SITE_URL/download/bookkeeping.apk", "sha256": "$SHA", "size": "${SIZE}MB", "notes": "v1.5.0 多设备端到端加密同步、多设备同时登录"}
JSON
fi

COPYFILE_DISABLE=1 tar --no-xattrs -C "$STAGE" -czf "$STAGE.tgz" .
scp "${SSH_ARGS[@]}" "$STAGE.tgz" "$HOST:/tmp/bookkeeping.tgz"
rm -f "$STAGE.tgz"

ssh "${SSH_ARGS[@]}" "$HOST" 'bash -s' <<'REMOTE'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
id bookkeeping >/dev/null 2>&1 || sudo useradd --system --home /var/lib/bookkeeping --shell /usr/sbin/nologin bookkeeping
sudo mkdir -p /opt/bookkeeping/app /opt/bookkeeping/web/download /var/lib/bookkeeping/backups
sudo chown -R bookkeeping:bookkeeping /var/lib/bookkeeping

T=$(mktemp -d); tar -C "$T" -xzf /tmp/bookkeeping.tgz; rm /tmp/bookkeeping.tgz
sudo cp "$T"/app/* /opt/bookkeeping/app/
if [[ -f "$T/web/download/bookkeeping.apk" ]]; then
  sudo cp -R "$T"/web/. /opt/bookkeeping/web/
fi
sudo cp "$T"/bookkeeping.service /etc/systemd/system/bookkeeping.service
sudo cp "$T"/bookkeeping-backup.cron /etc/cron.d/bookkeeping-backup
sudo chmod 644 /etc/cron.d/bookkeeping-backup
rm -rf "$T"

[[ -x /opt/bookkeeping/venv/bin/python ]] || sudo python3 -m venv /opt/bookkeeping/venv
sudo /opt/bookkeeping/venv/bin/pip install -q --upgrade pip
sudo /opt/bookkeeping/venv/bin/pip install -q -r /opt/bookkeeping/app/requirements.txt

sudo systemctl daemon-reload
sudo systemctl enable -q bookkeeping
sudo systemctl restart bookkeeping
sleep 2
curl -fsS http://127.0.0.1:8095/api/health && echo
systemctl is-active bookkeeping
REMOTE
echo "Bookkeeping deployed successfully!"
