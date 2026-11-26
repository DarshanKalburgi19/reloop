#!/usr/bin/env bash
# Starts the REAL ReLoop stack for the end-to-end scenarios:
#   repair-service :8083 → order-service :8082 → product-service :8081 → frontend dev server (proxy) :8080
# Each service runs from its own module directory, so each keeps its OWN H2 database (<service>/data/…):
# there is no shared database, only REST calls.
#
#   scripts/e2e/start-stack.sh            build missing jars, start everything, wait until it is ready
#   scripts/e2e/start-stack.sh --build    rebuild the jars first (mvn package -DskipTests)
#   scripts/e2e/start-stack.sh --fresh    move the services' data/ dirs aside first (clean databases)
#   scripts/e2e/start-stack.sh --stop     stop what a previous run started
#
# Logs and PIDs go to $E2E_LOG_DIR (default /tmp/reloop-e2e); FRONTEND_PORT (default 8080) moves the proxy.
# Needs Java 21, Maven, Node 18+, curl.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_DIR="${E2E_LOG_DIR:-/tmp/reloop-e2e}"
FRONTEND_PORT="${FRONTEND_PORT:-8080}"
# start order matters: product-service seeds the demo repairer THROUGH repair-service (it retries, but faster this way)
SERVICES=(repair-service order-service product-service)
declare -A PORT=([product-service]=8081 [order-service]=8082 [repair-service]=8083)
mkdir -p "$LOG_DIR"

stop() {
  for f in "$LOG_DIR"/*.pid; do
    [[ -e "$f" ]] || continue
    pid="$(cat "$f")"
    if kill -0 "$pid" 2>/dev/null; then kill "$pid" && echo "stopped $(basename "$f" .pid) (pid $pid)"; fi
    rm -f "$f"
  done
}

wait_http() { # wait_http URL NAME SECONDS
  for _ in $(seq 1 "$3"); do
    if curl -fs -o /dev/null "$1"; then echo "  $2 is up"; return 0; fi
    sleep 1
  done
  echo "  $2 did not become ready ($1); last log lines:" >&2
  tail -n 60 "$LOG_DIR/$2.log" >&2 || true
  return 1
}

BUILD=0; FRESH=0
for arg in "$@"; do
  case "$arg" in
    --stop) stop; exit 0 ;;
    --build) BUILD=1 ;;
    --fresh) FRESH=1 ;;
    *) echo "unknown option $arg" >&2; exit 2 ;;
  esac
done
stop

for s in "${SERVICES[@]}"; do
  jar="$(ls "$ROOT/$s"/target/"$s"-*.jar 2>/dev/null | grep -v original | head -1 || true)"
  if [[ $BUILD -eq 1 || -z "$jar" ]]; then
    echo "building $s…"
    mvn -B -ntp -q -f "$ROOT/$s/pom.xml" -DskipTests package
  fi
  if [[ $FRESH -eq 1 && -d "$ROOT/$s/data" ]]; then
    mv "$ROOT/$s/data" "$ROOT/$s/data.bak-$(date +%Y%m%d%H%M%S)"
    echo "moved $s/data aside"
  fi
done

export RELOOP_INTERNAL_API_KEY="${RELOOP_INTERNAL_API_KEY:-reloop-local-dev-internal-key-change-me}"
for s in "${SERVICES[@]}"; do
  jar="$(ls "$ROOT/$s"/target/"$s"-*.jar | grep -v original | head -1)"
  echo "starting $s on :${PORT[$s]}"
  # cd first, THEN background only the java process (so $! is java and nothing keeps our stdout open)
  (cd "$ROOT/$s" || exit 1; nohup java -jar "$jar" > "$LOG_DIR/$s.log" 2>&1 < /dev/null & echo $! > "$LOG_DIR/$s.pid")
  wait_http "http://localhost:${PORT[$s]}/actuator/health" "$s" 120
done

echo "starting the frontend dev server on :$FRONTEND_PORT (proxy to the services)"
(cd "$ROOT/frontend" || exit 1; PORT="$FRONTEND_PORT" nohup node server.js > "$LOG_DIR/frontend.log" 2>&1 < /dev/null & echo $! > "$LOG_DIR/frontend.pid")
wait_http "http://localhost:$FRONTEND_PORT/healthz" frontend 30

# product-service registers the demo repairer through repair-service, retrying in the background
echo "waiting for the demo repairer account…"
for _ in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:$FRONTEND_PORT/api/auth/login" -H 'Content-Type: application/json' \
    -d '{"email":"repairer.demo@reloop.local","password":"ReLoopDemo#Rep2026"}')"
  if [[ "$code" == "200" ]]; then echo "  demo accounts ready"; echo "Stack is up: http://localhost:$FRONTEND_PORT (logs in $LOG_DIR)"; exit 0; fi
  sleep 1
done
echo "demo repairer login still failing (HTTP $code); see $LOG_DIR/product-service.log" >&2
exit 1
