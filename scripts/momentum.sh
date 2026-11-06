#!/usr/bin/env bash
# MOMENTUM launcher for Linux and macOS.
# Mirrors scripts/start.ps1 / stop-service.ps1 behavior on Windows.
#
# Usage:
#   ./scripts/momentum.sh start    # build if needed, start service, open browser
#   ./scripts/momentum.sh stop     # stop the background service
#   ./scripts/momentum.sh restart  # stop, then start
#   ./scripts/momentum.sh status   # is the service up?
#   ./scripts/momentum.sh build    # (re)compile the Java service only
#   ./scripts/momentum.sh doctor   # full health check (needs Node.js)

set -u

REPO="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$REPO/services/api/build/momentum-api.jar"
SRC="$REPO/services/api/src/main/java"
PORT="${MOMENTUM_PORT:-8787}"
BASE="http://127.0.0.1:$PORT"
LOGDIR="${TMPDIR:-/tmp}/momentum"
PIDFILE="$LOGDIR/momentum.pid"
mkdir -p "$LOGDIR"

die() { echo "Error: $*" >&2; exit 1; }
command -v java >/dev/null 2>&1 || die "Java is not installed. Install it (e.g. 'sudo apt install default-jdk' / 'brew install openjdk') and retry."

# ---- load .env into the process environment (same rules as the Windows scripts) ----
if [ -f "$REPO/.env" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#$'\r'}"
    [ -z "$line" ] && continue
    case "$line" in '#'*) continue ;; esac
    name="${line%%=*}"; value="${line#*=}"
    [ "$name" = "$line" ] && continue
    value="${value%\"}"; value="${value#\"}"; value="${value%\'}"; value="${value#\'}"
    if [ -z "$(eval "printenv \"$name\" 2>/dev/null || true")" ] 2>/dev/null; then
      export "$name=$value"
    fi
  done < "$REPO/.env"
fi

build_service() {
  command -v javac >/dev/null 2>&1 || die "JDK missing (javac). Install a full JDK, not just the JRE."
  echo "Compiling MOMENTUM service..."
  mkdir -p "$REPO/services/api/build/classes"
  find "$REPO/services/api/build/classes" -type f -delete 2>/dev/null || true
  find "$SRC" -name '*.java' > "$REPO/services/api/build/sources.txt"
  javac --release 21 -encoding UTF-8 -d "$REPO/services/api/build/classes" @"$REPO/services/api/build/sources.txt" || die "compilation failed"
  cat > "$REPO/services/api/build/manifest.txt" <<'EOF'
Manifest-Version: 1.0
Main-Class: dev.momentum.MomentumServer
Implementation-Title: MOMENTUM API
Implementation-Version: 1.0.0
EOF
  jar --create --file "$JAR" --manifest "$REPO/services/api/build/manifest.txt" -C "$REPO/services/api/build/classes" . || die "jar creation failed"
  echo "Built $JAR"
}

health() {
  curl -fsS --max-time 4 "$BASE/api/health" >/dev/null 2>&1
}

start() {
  if health; then
    echo "MOMENTUM is already running at $BASE"
    return 0
  fi
  if [ ! -f "$JAR" ]; then build_service; fi
  if [ ! -f "$REPO/apps/web/dist/index.html" ]; then
    command -v npm >/dev/null 2>&1 || die "Web app not built yet and npm is missing. Run: npm install && npm run build"
    echo "Building MOMENTUM web app..."
    ( cd "$REPO" && npm run build ) || die "web build failed"
  fi
  [ -n "${MOMENTUM_WEB_DIST:-}" ] || export MOMENTUM_WEB_DIST="$REPO/apps/web/dist"
  echo "Starting MOMENTUM service on port $PORT..."
  nohup java -jar "$JAR" > "$LOGDIR/service-out.log" 2> "$LOGDIR/service-err.log" &
  echo $! > "$PIDFILE"
  for _ in $(seq 1 30); do
    if health; then
      echo "Service is up at $BASE"
      echo "Logs: $LOGDIR/service-out.log / service-err.log"
      return 0
    fi
    sleep 1
  done
  echo "--- last log lines ---"; tail -n 20 "$LOGDIR/service-err.log" "$LOGDIR/service-out.log" 2>/dev/null
  die "service did not become healthy in 30 s"
}

stop() {
  if [ -f "$PIDFILE" ]; then
    pid=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
      echo "Stopping MOMENTUM service (pid $pid)"
      kill "$pid" 2>/dev/null && sleep 2 && kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
    fi
    rm -f "$PIDFILE"
  fi
  # sweep any stray jar process (matches the Windows stop script's zombie cleanup)
  pkill -f 'momentum-api\.jar' 2>/dev/null && echo "Stray service process cleaned up."
  health && { echo "Service still responds — check manually: lsof -i :$PORT"; return 1; }
  echo "Stopped."
}

status() {
  if health; then echo "UP — $BASE"; else echo "DOWN"; exit 1; fi
}

open_browser() {
  if command -v open >/dev/null 2>&1; then open "$BASE"
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$BASE" >/dev/null 2>&1 || true
  else echo "Open $BASE in your browser."
  fi
}

case "${1:-}" in
  start) start && open_browser ;;
  stop) stop ;;
  restart) stop >/dev/null 2>&1; sleep 1; start && open_browser ;;
  status) status ;;
  build) build_service ;;
  doctor)
    [ -f "$JAR" ] || { echo "Missing $JAR (run: $0 build)"; exit 1; }
    start >/dev/null 2>&1 || true
    command -v node >/dev/null 2>&1 || die "Node.js missing for doctor"
    node "$REPO/scripts/doctor.mjs" ;;
  *) echo "Usage: $0 {start|stop|restart|status|build|doctor}"; exit 1 ;;
esac
