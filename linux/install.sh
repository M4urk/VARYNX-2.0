#!/usr/bin/env bash
# VARYNX 2.0 — Linux Guardian Install Script
# Installs the daemon, systemd unit, and creates the varynx user.
#
# Usage:
#   sudo ./install.sh              # Install
#   sudo ./install.sh --uninstall  # Remove
#
# Requirements: JDK 17+, systemd

set -euo pipefail

VARYNX_VERSION="2.0.0-alpha"
INSTALL_DIR="/opt/varynx"
DATA_DIR="/var/lib/varynx"
LOG_DIR="/var/log/varynx"
CONFIG_DIR="/etc/varynx"
UNIT_FILE="/etc/systemd/system/varynx-guardian.service"
JAR_NAME="varynx-linux.jar"
SERVICE_USER="varynx"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[VARYNX]${NC} $1"; }
warn() { echo -e "${RED}[VARYNX]${NC} $1"; }
info() { echo -e "${CYAN}[VARYNX]${NC} $1"; }

# ── Uninstall ──

if [[ "${1:-}" == "--uninstall" ]]; then
    log "Uninstalling VARYNX Guardian..."

    if systemctl is-active --quiet varynx-guardian 2>/dev/null; then
        systemctl stop varynx-guardian
    fi
    if systemctl is-enabled --quiet varynx-guardian 2>/dev/null; then
        systemctl disable varynx-guardian
    fi
    rm -f "$UNIT_FILE"
    systemctl daemon-reload

    rm -rf "$INSTALL_DIR"
    log "Removed $INSTALL_DIR"

    # Keep data and logs unless --purge
    if [[ "${2:-}" == "--purge" ]]; then
        rm -rf "$DATA_DIR" "$LOG_DIR" "$CONFIG_DIR"
        if id "$SERVICE_USER" &>/dev/null; then
            userdel "$SERVICE_USER" 2>/dev/null || true
        fi
        log "Purged data, logs, config, and user"
    else
        info "Data preserved at $DATA_DIR, $LOG_DIR, $CONFIG_DIR"
        info "Use --uninstall --purge to remove everything"
    fi

    log "VARYNX Guardian uninstalled"
    exit 0
fi

# ── Preflight checks ──

if [[ $EUID -ne 0 ]]; then
    warn "This script must be run as root (sudo)"
    exit 1
fi

# Check Java
if ! command -v java &>/dev/null; then
    warn "Java not found. Install JDK 17+:"
    warn "  apt install openjdk-17-jre-headless   # Debian/Ubuntu"
    warn "  dnf install java-17-openjdk-headless   # Fedora/RHEL"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d. -f1)
if [[ "$JAVA_VER" -lt 17 ]]; then
    warn "Java 17+ required (found: $JAVA_VER)"
    exit 1
fi

# Check for JAR
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$SCRIPT_DIR/$JAR_NAME"
if [[ ! -f "$JAR_PATH" ]]; then
    # Try build output location
    JAR_PATH="$SCRIPT_DIR/../build/libs/varynx-linux-all.jar"
    if [[ ! -f "$JAR_PATH" ]]; then
        warn "Cannot find $JAR_NAME — build it first with:"
        warn "  ./gradlew :linux:shadowJar"
        exit 1
    fi
fi

# ── Install ──

log "Installing VARYNX Guardian v${VARYNX_VERSION}..."

# Create service user
if ! id "$SERVICE_USER" &>/dev/null; then
    useradd --system --no-create-home --shell /usr/sbin/nologin "$SERVICE_USER"
    log "Created system user: $SERVICE_USER"
fi

# Create directories
mkdir -p "$INSTALL_DIR" "$DATA_DIR" "$LOG_DIR" "$CONFIG_DIR"
chown -R "$SERVICE_USER:$SERVICE_USER" "$DATA_DIR" "$LOG_DIR" "$CONFIG_DIR"

# Copy JAR
cp "$JAR_PATH" "$INSTALL_DIR/$JAR_NAME"
chmod 755 "$INSTALL_DIR/$JAR_NAME"
log "Installed $JAR_NAME → $INSTALL_DIR/"

# Create systemd unit
cat > "$UNIT_FILE" << 'UNIT'
[Unit]
Description=VARYNX Guardian Daemon
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=varynx
Group=varynx
WorkingDirectory=/var/lib/varynx
ExecStart=/usr/bin/java -Xmx256m -jar /opt/varynx/varynx-linux.jar --cycle=5000
Restart=on-failure
RestartSec=5
StartLimitIntervalSec=300
StartLimitBurst=5

# Hardening
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/varynx /var/log/varynx /etc/varynx
PrivateTmp=true
NoNewPrivileges=true
CapabilityBoundingSet=CAP_NET_BIND_SERVICE CAP_NET_RAW

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=varynx-guardian

[Install]
WantedBy=multi-user.target
UNIT

chmod 644 "$UNIT_FILE"
systemctl daemon-reload
log "Installed systemd unit → $UNIT_FILE"

# Create default config
if [[ ! -f "$CONFIG_DIR/varynx.conf" ]]; then
    cat > "$CONFIG_DIR/varynx.conf" << 'CONF'
# VARYNX Guardian Configuration
# device_name: display name for this node in the mesh
device_name=VARYNX Linux

# role: SENTINEL | GUARDIAN | CONTROLLER | NOTIFIER
role=SENTINEL

# mesh_enabled: true | false
mesh_enabled=true

# cycle_interval_ms: guardian loop interval
cycle_interval_ms=5000

# log_level: DEBUG | INFO | WARN | ERROR
log_level=INFO
CONF
    chown "$SERVICE_USER:$SERVICE_USER" "$CONFIG_DIR/varynx.conf"
    log "Created default config → $CONFIG_DIR/varynx.conf"
fi

# Enable and start
systemctl enable varynx-guardian
systemctl start varynx-guardian
log "Service enabled and started"

echo ""
info "╔════════════════════════════════════════════╗"
info "║  VARYNX Guardian v${VARYNX_VERSION} installed  ║"
info "╠════════════════════════════════════════════╣"
info "║  Service:  systemctl status varynx-guardian║"
info "║  Logs:     journalctl -u varynx-guardian   ║"
info "║  Config:   /etc/varynx/varynx.conf         ║"
info "║  Data:     /var/lib/varynx                  ║"
info "║  Uninstall: sudo ./install.sh --uninstall   ║"
info "╚════════════════════════════════════════════╝"
