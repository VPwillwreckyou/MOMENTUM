#!/usr/bin/env bash
# Build or test the native SwiftUI iOS app (apps/ios/Momentum).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT/apps/ios/Momentum/Momentum.xcodeproj"
COMMAND="${1:-build}"

case "$COMMAND" in
  build)
    xcodebuild -project "$PROJECT" -scheme Momentum \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      CODE_SIGNING_ALLOWED=NO build
    ;;
  test)
    DEVICE="$(xcrun simctl list devices available 2>/dev/null \
      | grep -m1 -E '^[[:space:]]+(iPhone|iPad)' \
      | sed -E 's/^[[:space:]]*([^()]+) \(.*/\1/' | xargs || true)"
    if [[ -z "$DEVICE" ]]; then
      echo "No iOS Simulator device is available. Open Xcode → Window → Devices and Simulators and add one." >&2
      exit 1
    fi
    echo "Using simulator: $DEVICE"
    xcodebuild test -project "$PROJECT" -scheme Momentum \
      -sdk iphonesimulator -destination "platform=iOS Simulator,name=$DEVICE" \
      CODE_SIGNING_ALLOWED=NO
    ;;
  *)
    echo "Usage: $0 [build|test]" >&2
    exit 2
    ;;
esac
