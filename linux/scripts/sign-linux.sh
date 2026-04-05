#!/usr/bin/env bash
## VARYNX 2.0 — Linux Package Signing
##
## Creates a SHA-256 manifest of all installed files and signs it
## with the VARYNX release Ed25519 key (via openssl or age).
##
## Prerequisites:
##   - openssl 1.1+ with Ed25519 support
##   - VARYNX_SIGN_KEY — path to Ed25519 PEM private key
##
## Usage:
##   ./sign-linux.sh --artifact-dir /path/to/package/root
##   ./sign-linux.sh --verify /opt/varynx
##

set -euo pipefail

SIGN_KEY="${VARYNX_SIGN_KEY:-}"
VERIFY_KEY="${VARYNX_VERIFY_KEY:-}"

usage() {
    echo "Usage:"
    echo "  $0 --artifact-dir <dir>   Sign all files in <dir>"
    echo "  $0 --verify <dir>         Verify manifest in <dir>"
    echo ""
    echo "Environment:"
    echo "  VARYNX_SIGN_KEY    Path to Ed25519 private key (PEM)"
    echo "  VARYNX_VERIFY_KEY  Path to Ed25519 public key (PEM)"
    exit 1
}

generate_manifest() {
    local dir="$1"
    find "$dir" -type f ! -name "MANIFEST" ! -name "MANIFEST.sig" -print0 |
        sort -z |
        xargs -0 sha256sum |
        sed "s|${dir}/||"
}

sign_package() {
    local dir="$1"

    if [ -z "$SIGN_KEY" ]; then
        echo "ERROR: VARYNX_SIGN_KEY not set."
        exit 1
    fi
    if [ ! -f "$SIGN_KEY" ]; then
        echo "ERROR: Key file not found: $SIGN_KEY"
        exit 1
    fi

    echo "Generating manifest for: $dir"
    generate_manifest "$dir" > "${dir}/MANIFEST"

    local count
    count=$(wc -l < "${dir}/MANIFEST")
    echo "  $count files hashed."

    echo "Signing manifest..."
    openssl pkeyutl -sign \
        -inkey "$SIGN_KEY" \
        -rawin \
        -in "${dir}/MANIFEST" \
        -out "${dir}/MANIFEST.sig"

    echo "Manifest signed: ${dir}/MANIFEST.sig"
    echo "Done."
}

verify_package() {
    local dir="$1"

    if [ -z "$VERIFY_KEY" ]; then
        echo "ERROR: VARYNX_VERIFY_KEY not set."
        exit 1
    fi
    if [ ! -f "${dir}/MANIFEST" ] || [ ! -f "${dir}/MANIFEST.sig" ]; then
        echo "ERROR: MANIFEST or MANIFEST.sig not found in $dir"
        exit 1
    fi

    echo "Verifying manifest signature..."
    if openssl pkeyutl -verify \
        -pubin -inkey "$VERIFY_KEY" \
        -rawin \
        -in "${dir}/MANIFEST" \
        -sigfile "${dir}/MANIFEST.sig"; then
        echo "  Signature: VALID"
    else
        echo "  Signature: INVALID"
        exit 1
    fi

    echo "Verifying file hashes..."
    local failed=0
    while IFS= read -r line; do
        local hash file
        hash=$(echo "$line" | awk '{print $1}')
        file=$(echo "$line" | awk '{print $2}')
        local actual
        actual=$(sha256sum "${dir}/${file}" 2>/dev/null | awk '{print $1}')
        if [ "$hash" != "$actual" ]; then
            echo "  MISMATCH: $file"
            failed=$((failed + 1))
        fi
    done < "${dir}/MANIFEST"

    if [ "$failed" -gt 0 ]; then
        echo "FAILED: $failed file(s) do not match manifest."
        exit 1
    fi

    local count
    count=$(wc -l < "${dir}/MANIFEST")
    echo "All $count files verified."
}

# --- Main ---

if [ $# -lt 2 ]; then
    usage
fi

case "$1" in
    --artifact-dir)
        sign_package "$2"
        ;;
    --verify)
        verify_package "$2"
        ;;
    *)
        usage
        ;;
esac
