#!/usr/bin/env bash
# Repack a jpackage-generated .deb for Chinese domestic Linux distros.
# Fixes: (1) t64-suffixed dependency names → non-t64 equivalents
#        (2) .desktop + icon standardised for UKUI/DDE/Linx desktops
#        (3) /usr/bin/ symlink so `Exec=skerry` works
#        (4) postinst: desktop shortcut + icon cache refresh
#        (5) postrm: cleanup menu + desktop + user data on purge
# Usage: package-kylin-deb.sh <input.deb> <output.deb> [distro-id]
#   distro-id is now unused (unified .desktop Name="Skerry"); kept for CLI compat.

set -euo pipefail

SRC="$1"
DST="$2"
DISTRO_ID="${3:-kylin}"

TMP="$(mktemp -d)"

dpkg-deb -R "$SRC" "$TMP"

# ── 1. Fix t64 dependencies (Kylin V10 has older apt) ────────────────
sed -i -E 's/(lib[a-zA-Z0-9._+-]+)t64\b/\1/g' "$TMP/DEBIAN/control"

# ── 2. Find paths inside the package ─────────────────────────────────
APP_ROOT=$(find "$TMP/opt" -maxdepth 1 -mindepth 1 -type d ! -name . -print -quit 2>/dev/null || echo "")
if [ -z "$APP_ROOT" ]; then
    echo "ERROR: cannot find app root under $TMP/opt" >&2
    rm -rf "$TMP"; exit 1
fi

# Force lowercase: rename any upper-case dirs/files inside the package so
# Exec=, Icon=, /usr/bin symlink, and the on-disk layout all match exactly.
# (jpackage emits /opt/<lower>/bin/<PackageCase> — the mixed case breaks Exec.)
APP_ROOT_LOWER="$(dirname "$APP_ROOT")/$(basename "$APP_ROOT" | tr 'A-Z' 'a-z')"
if [ "$APP_ROOT" != "$APP_ROOT_LOWER" ]; then
    mv "$APP_ROOT" "$APP_ROOT_LOWER"
    APP_ROOT="$APP_ROOT_LOWER"
fi

# Lowercase bin/ and lib/ directly, then every file inside lib/app/ INCLUDING the
# jars — but rewrite the cfg's app.classpath entries to match. Two jars ship with
# uppercase in their names (composeApp-desktop-*.jar, jSerialComm-*.jar) and the
# cfg references them verbatim, so renaming files without touching the cfg breaks
# the classpath (ClassNotFoundException on startup). Keep both in sync: lowercase
# the file, then lowercase its mention in every .cfg in the same directory.
for d in "$APP_ROOT/bin" "$APP_ROOT/lib" "$APP_ROOT/lib/app"; do
    [ -d "$d" ] || continue
    for f in "$d"/*; do
        [ -e "$f" ] || continue
        base="$(basename "$f")"
        lower="$(echo "$base" | tr 'A-Z' 'a-z')"
        if [ "$base" != "$lower" ]; then
            mv "$f" "$d/$lower"
        fi
    done
done
for cfg in "$APP_ROOT/lib/app/"*.cfg; do
    [ -e "$cfg" ] || continue
    sed -i -E 's/^(app\.classpath=\$APPDIR\/)(.*)$/\1\L\2/' "$cfg"
done

# Drop the jpackage-bundled .desktop under lib/ (we ship our own in /usr/share/applications/)
rm -f "$APP_ROOT/lib/"*.desktop

APP_NAME=$(basename "$APP_ROOT")
BIN_DIR="$APP_ROOT/bin"

# Find the executable (must exist for a valid package)
MAIN_BIN=$(find "$BIN_DIR" -type f -executable -name "$APP_NAME" -not -name "*.so" 2>/dev/null | head -1)
if [ -z "$MAIN_BIN" ]; then
    MAIN_BIN=$(find "$BIN_DIR" -type f -executable 2>/dev/null | head -1)
fi
if [ -z "$MAIN_BIN" ]; then
    echo "ERROR: no executable found in $BIN_DIR" >&2
    rm -rf "$TMP"; exit 1
fi

# Find the real icon file (jpackage may capitalise differently from APP_NAME)
ICON_REAL=$(find "$APP_ROOT/lib" -maxdepth 1 \( -name "*.png" -o -name "*.svg" -o -name "*.xpm" \) 2>/dev/null | head -1)

INSTALLED_EXEC="/opt/${APP_NAME}/bin/${APP_NAME}"
PIXMAPS_ICON="/usr/share/pixmaps/${APP_NAME}.png"
DESKTOP_FILE_NAME="${APP_NAME}.desktop"

# ── 3. Install icons ────────────────────────────────────────────────
# /usr/share/pixmaps/ for legacy lookup, plus freedesktop hicolor theme
# (48/128/256) — Kylin's software store reads the hicolor path for the
# install-dialog icon and shows a blank placeholder when it is missing.
PIXMAPS_DIR="$TMP/usr/share/pixmaps"
mkdir -p "$PIXMAPS_DIR"
if [ -n "$ICON_REAL" ] && [ -f "$ICON_REAL" ]; then
    cp "$ICON_REAL" "$PIXMAPS_DIR/${APP_NAME}.png"
    echo "Icon installed to ${PIXMAPS_ICON}"
    for size in 48 128 256; do
        HICOLOR_DIR="$TMP/usr/share/icons/hicolor/${size}x${size}/apps"
        mkdir -p "$HICOLOR_DIR"
        if command -v python3 >/dev/null 2>&1 && python3 -c 'import PIL' 2>/dev/null; then
            python3 - "$ICON_REAL" "$HICOLOR_DIR/${APP_NAME}.png" "$size" <<'PYEOF'
import sys
from PIL import Image
src, dst, size = sys.argv[1], sys.argv[2], int(sys.argv[3])
img = Image.open(src).convert('RGBA')
img.resize((size, size), Image.LANCZOS).save(dst, 'PNG')
PYEOF
        else
            cp "$ICON_REAL" "$HICOLOR_DIR/${APP_NAME}.png"
        fi
    done
    echo "hicolor icons installed (48/128/256)"
fi

# ── 4. Create /usr/bin/ symlink (Exec=skerry needs PATH access) ──────
BIN_LINK_DIR="$TMP/usr/bin"
mkdir -p "$BIN_LINK_DIR"
ln -sf "../..${INSTALLED_EXEC}" "$BIN_LINK_DIR/${APP_NAME}"

# ── 5. Write .desktop into /usr/share/applications/ ──────────────────
SYSTEM_DESKTOP_DIR="$TMP/usr/share/applications"
SYSTEM_DESKTOP="$SYSTEM_DESKTOP_DIR/$DESKTOP_FILE_NAME"

mkdir -p "$SYSTEM_DESKTOP_DIR"

# Desktop name: unified "Skerry" for all Chinese domestic distros (kylin/uos/nari)
DESKTOP_NAME="Skerry"

cat > "$SYSTEM_DESKTOP" << EOF
[Desktop Entry]
Type=Application
Version=1.0
Name=${DESKTOP_NAME}
Comment=开源跨平台 SSH 客户端
Comment[zh_CN]=开源跨平台 SSH 客户端
GenericName=SSH Client
GenericName[zh_CN]=SSH 客户端
Exec=${INSTALLED_EXEC}
Icon=${APP_NAME}
Categories=Network;RemoteAccess;
Terminal=false
StartupNotify=false
StartupWMClass=${APP_NAME}
X-GNOME-Autostart-enabled=false
EOF

# ── 6. Write postinst (install-time) ─────────────────────────────────
set +u  # heredoc variables use $var patterns not defined in outer shell
cat > "$TMP/DEBIAN/postinst" << POSTINST
#!/bin/bash
set -e

DESKTOP_SRC="/usr/share/applications/${DESKTOP_FILE_NAME}"

# Copy to users' desktops (both Chinese / English desktop dirs).
# Double-click installs (Kylin software store, gdebi) run postinst as root with
# SUDO_USER unset and logname failing — fall back to every real user (uid>=1000).
REAL_USERS="\${SUDO_USER:-}"
if [ -z "\$REAL_USERS" ]; then
    REAL_USERS=\$(logname 2>/dev/null || echo "")
fi
if [ -z "\$REAL_USERS" ] || [ "\$REAL_USERS" = "root" ]; then
    REAL_USERS=\$(awk -F: '\$3 >= 1000 && \$3 < 65534 {print \$1}' /etc/passwd)
fi
for REAL_USER in \$REAL_USERS; do
    USER_HOME=\$(getent passwd "\$REAL_USER" | cut -d: -f6)
    [ -n "\$USER_HOME" ] || continue
    for dir in "\$USER_HOME/桌面" "\$USER_HOME/Desktop"; do
        if [ -d "\$dir" ] && [ -f "\$DESKTOP_SRC" ]; then
            cp "\$DESKTOP_SRC" "\$dir/${DESKTOP_FILE_NAME}"
            chown "\$REAL_USER:\$REAL_USER" "\$dir/${DESKTOP_FILE_NAME}"
            chmod 755 "\$dir/${DESKTOP_FILE_NAME}"
            break
        fi
    done
done

# Refresh desktop / MIME / icon caches
if command -v update-desktop-database &>/dev/null; then
    update-desktop-database /usr/share/applications 2>/dev/null || true
fi
if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache /usr/share/icons/hicolor 2>/dev/null || true
fi
POSTINST
chmod 755 "$TMP/DEBIAN/postinst"
set -u  # re-enable after heredoc

# ── 7. Write postrm (uninstall/purge) ───────────────────────────────
set +u
cat > "$TMP/DEBIAN/postrm" << POSTRM
#!/bin/bash
set -e

DESKTOP_SRC="/usr/share/applications/${DESKTOP_FILE_NAME}"
SYMLINK_BIN="/usr/bin/${APP_NAME}"

# On remove or purge, delete system .desktop and the /usr/bin symlink
if [ "\$1" = "remove" ] || [ "\$1" = "purge" ]; then
    rm -f "\$DESKTOP_SRC"
    rm -f "\$SYMLINK_BIN"
    if command -v update-desktop-database &>/dev/null; then
        update-desktop-database /usr/share/applications 2>/dev/null || true
    fi
fi

# On purge, also delete user data
if [ "\$1" = "purge" ]; then
    # Remove desktop shortcuts
    for dir in /home/*/桌面/${DESKTOP_FILE_NAME} /home/*/Desktop/${DESKTOP_FILE_NAME}; do
        rm -f "\$dir" 2>/dev/null || true
    done

    # App user data (see main.kt configDir / dataDir)
    for h in /home/*; do
        [ -d "\$h" ] || continue
        rm -rf "\$h/.config/${APP_NAME}"  2>/dev/null || true
        rm -rf "\$h/.local/share/${APP_NAME}" 2>/dev/null || true
    done
fi
POSTRM
chmod 755 "$TMP/DEBIAN/postrm"

# ── 8. Repack ─────────────────────────────────────────────────────────
dpkg-deb --root-owner-group -b "$TMP" "$DST"
rm -rf "$TMP"

echo "✅ ${DISTRO_ID} .deb: $DST"
