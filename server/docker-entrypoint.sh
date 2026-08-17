#!/bin/sh
# Skerry Sync — container entrypoint wrapper.
# Runs as root at container start: fixes /data permissions, then drops to the
# unprivileged `skerry` user (uid 999) and launches the server.
#
# This self-heals the most common deployment mistake: bind-mounting a /data
# directory that isn't owned by 999:999, which causes SQLITE_READONLY → 500
# → clients see "协议错误".  Without this fix the server restarts endlessly
# with no visible error until someone checks the logs.

set -e

APP_USER="skerry"
APP_UID=999
APP_GID=999
DATA_DIR="/data"

# ── Fix /data ownership ──────────────────────────────────────────────
DATA_OWNER=$(stat -c '%u:%g' "$DATA_DIR" 2>/dev/null || echo "unknown")
if [ "$DATA_OWNER" != "$APP_UID:$APP_GID" ]; then
    echo "[entrypoint] /data owner is $DATA_OWNER (expected $APP_UID:$APP_GID) — fixing"
    chown -R "$APP_UID:$APP_GID" "$DATA_DIR" || echo "[entrypoint] WARN: could not chown /data (read-only volume?)"
fi

# ── Fix file ownership inside /data ──────────────────────────────────
# The directory might be correct but individual files might not be (e.g.
# sqlite journal files created by a previous misconfigured run).
find "$DATA_DIR" \( ! -user "$APP_UID" -o ! -group "$APP_GID" \) \
    -exec chown "$APP_UID:$APP_GID" {} + 2>/dev/null || true

# ── Drop privileges and start the server ─────────────────────────────
exec runuser -u "$APP_USER" -- /app/bin/server "$@"
