#!/usr/bin/env bash
set -euo pipefail

# Run the E2E tests in a real, windowed Chrome on a virtual display (Xvfb).
#
# Don't run them headless (it hides failures that happen in CI) nor on your desktop: the compositor stops
# drawing a covered window, its animations freeze and the tests time out waiting for the dialogs.
#
# Usage (any argument is passed to Gradle):
#   ./e2e-test.sh
#   ./e2e-test.sh --tests 'alfio.e2e.SettingsE2ETest'
#
# Required: Chrome, xvfb-run (Debian/Ubuntu: apt install xvfb), and the E2E_* variables described in
# the E2E section of CONTRIBUTING.md.

if ! command -v xvfb-run >/dev/null 2>&1; then
  echo "ERROR: xvfb-run not found in PATH. Install it (Debian/Ubuntu: apt install xvfb)." >&2
  exit 127
fi

if [[ -z "${E2E_SERVER_URL:-}" || -z "${E2E_ADMIN_USERNAME:-}" || -z "${E2E_ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: set E2E_SERVER_URL, E2E_ADMIN_USERNAME and E2E_ADMIN_PASSWORD (see CONTRIBUTING.md)." >&2
  exit 64
fi

cd "$(dirname "$0")"

# Without these, Chrome on a Wayland desktop opens its window on the desktop and ignores the virtual display.
unset WAYLAND_DISPLAY XDG_SESSION_TYPE
exec xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24 -nolisten tcp" \
  ./gradlew e2eTest -Pverbose --console=plain "$@"
