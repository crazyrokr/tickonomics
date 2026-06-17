#!/usr/bin/env bash
# Check if graphify is installed, and if not, attempt to install it.
# This script is non-fatal: it will not fail the installation process if graphify cannot be installed.
set -euo pipefail

if command -v graphify >/dev/null 2>&1; then
    echo "graphify is already installed: $(command -v graphify)"
    exit 0
fi

echo "graphify not found. Attempting to install..."
# Attempt installation, allowing failure without exiting the script with an error
if command -v uv >/dev/null 2>&1; then
    echo "Installing graphify via uv..."
    uv tool install --upgrade graphifyy -q || echo "WARNING: uv installation failed."
elif command -v pip3 >/dev/null 2>&1; then
    echo "Installing graphify via pip3..."
    (pip3 install graphifyy -q || pip3 install graphifyy -q --break-system-packages) || echo "WARNING: pip3 installation failed."
else
    echo "--------------------------------------------------------"
    echo "WARNING: Could not automatically install graphify (uv/pip3 missing)."
    echo "You can still work on the project, but graphify will not be available."
    echo "To install it manually, run: pip install graphifyy"
    echo "--------------------------------------------------------"
fi

# Explicitly exit 0 to ensure the calling script continues.
exit 0
