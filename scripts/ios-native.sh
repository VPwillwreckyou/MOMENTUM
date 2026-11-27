#!/usr/bin/env bash
# Build the classic React app into the Capacitor iOS shell (apps/mobile/ios/App).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT/apps/mobile/ios/App/App.xcodeproj"
COMMAND="${1:-build}"
NODE_RUNTIME="$HOME/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin"
if [[ -x "$NODE_RUNTIME/node" ]]; then
  export PATH="$NODE_RUNTIME:$PATH"
fi

build_classic_app() {
  (cd "$ROOT/apps/web" && ../../node_modules/.bin/tsc -b && ../../node_modules/.bin/vite build)
  (cd "$ROOT/apps/mobile" && node_modules/.bin/cap sync)
}

case "$COMMAND" in
  build)
    build_classic_app
    xcodebuild -project "$PROJECT" -scheme App \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      CODE_SIGNING_ALLOWED=NO build
    ;;
  test)
    DEVICE="${IOS_SIMULATOR_DEVICE:-iPhone 17 Pro Max}"
    build_classic_app
    (cd "$ROOT/apps/web" && ../../node_modules/.bin/vitest run)
    xcodebuild -project "$PROJECT" -scheme App \
      -sdk iphonesimulator -destination "platform=iOS Simulator,name=$DEVICE" \
      CODE_SIGNING_ALLOWED=NO build
    ;;
  *)
    echo "Usage: $0 [build|test]" >&2
    exit 2
    ;;
esac
