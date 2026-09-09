#!/usr/bin/env bash
set -euo pipefail

# ─── Endia — Full-stack launcher ─────────────────────────────────────────────
# Starts MySQL (Docker), backend (Spring Boot) and frontend (Angular dev server).
# If any component is already running, it is stopped first.
#
# Usage:
#   ./scripts/init.sh          # start everything
#   ./scripts/init.sh stop     # stop everything
#   ./scripts/init.sh status   # show component status
# ─────────────────────────────────────────────────────────────────────────────

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
LOG_DIR="$PROJECT_ROOT/.logs"

MYSQL_CONTAINER="endia-mysql"
BACKEND_PORT=8080
FRONTEND_PORT=4200

# ─── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ─── Helpers ──────────────────────────────────────────────────────────────────
log()  { echo -e "${CYAN}[endia]${NC} $*"; }
ok()   { echo -e "${GREEN}[  ok ]${NC} $*"; }
warn() { echo -e "${YELLOW}[skip]${NC} $*"; }
err()  { echo -e "${RED}[fail]${NC} $*" >&2; }

# Check if a TCP port is in use (someone listening)
port_in_use() {
  ss -tlnp 2>/dev/null | grep -q ":$1 " && return 0 || return 1
}

# Kill process listening on a given port
kill_port() {
  local port=$1
  local pids
  pids=$(lsof -ti :"$port" 2>/dev/null || true)
  if [[ -n "$pids" ]]; then
    log "Killing process(es) on port $port (PID: $pids)"
    echo "$pids" | xargs kill -15 2>/dev/null || true
    sleep 1
    # Force kill if still alive
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [[ -n "$pids" ]]; then
      echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
  fi
}

# ─── Status ───────────────────────────────────────────────────────────────────
status() {
  echo ""
  echo "  Component          Status       Port / Detail"
  echo "  ─────────────────  ───────────  ─────────────────────"

  # MySQL
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "  MySQL (Docker)     ${GREEN}running${NC}      :3306"
  elif port_in_use 3306; then
    echo -e "  MySQL (native)     ${YELLOW}running${NC}      :3306 (not managed by init.sh)"
  else
    echo -e "  MySQL              ${RED}stopped${NC}"
  fi

  # Backend
  if port_in_use $BACKEND_PORT; then
    echo -e "  Backend (Spring)   ${GREEN}running${NC}      :$BACKEND_PORT"
  else
    echo -e "  Backend (Spring)   ${RED}stopped${NC}"
  fi

  # Frontend
  if port_in_use $FRONTEND_PORT; then
    echo -e "  Frontend (Angular) ${GREEN}running${NC}      :$FRONTEND_PORT"
  else
    echo -e "  Frontend (Angular) ${RED}stopped${NC}"
  fi
  echo ""
}

# ─── Stop ─────────────────────────────────────────────────────────────────────
stop_all() {
  log "Stopping all components..."

  # Frontend
  if port_in_use $FRONTEND_PORT; then
    kill_port $FRONTEND_PORT
    ok "Frontend stopped"
  else
    warn "Frontend was not running"
  fi

  # Backend
  if port_in_use $BACKEND_PORT; then
    kill_port $BACKEND_PORT
    ok "Backend stopped"
  else
    warn "Backend was not running"
  fi

  # MySQL container
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
    docker stop "$MYSQL_CONTAINER" >/dev/null 2>&1
    ok "MySQL (Docker) container stopped"
  elif docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
    warn "MySQL Docker container exists but is not running"
  elif port_in_use 3306; then
    if systemctl stop mysql 2>/dev/null; then
      ok "Native mysql.service stopped"
    else
      warn "Native MySQL is running but cannot be stopped (need sudo)"
    fi
  else
    warn "MySQL is not running"
  fi

  ok "All components stopped"
}

# ─── Start ────────────────────────────────────────────────────────────────────
start_all() {
  mkdir -p "$LOG_DIR"

  # ── 1. MySQL ──────────────────────────────────────────────────────────────
  log "Starting MySQL..."

  # If our Docker container is already running, we're good
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
    ok "MySQL (Docker) already running"
  else
    # Check if port 3306 is taken by something else (e.g. native mysql.service)
    if port_in_use 3306 && ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
      log "Port 3306 in use by another process — attempting to stop native MySQL..."
      if systemctl stop mysql 2>/dev/null; then
        ok "Native mysql.service stopped"
        sleep 1
      else
        err "Cannot stop native MySQL (need sudo). Stop it manually: sudo systemctl stop mysql"
        exit 1
      fi
    fi

    # Always recreate the container to ensure fresh, clean state
    docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
    docker run -d --name "$MYSQL_CONTAINER" \
      -e MYSQL_ROOT_PASSWORD=root \
      -e MYSQL_DATABASE=endia \
      -p 3306:3306 \
      mysql:8.0 >/dev/null 2>&1
    ok "MySQL container created"
  fi

  # Wait for MySQL to accept connections
  log "Waiting for MySQL to be ready..."
  local retries=30
  while ! docker exec "$MYSQL_CONTAINER" mysqladmin ping -uroot -proot --silent 2>/dev/null; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      err "MySQL did not become ready in time"
      exit 1
    fi
    sleep 1
  done
  ok "MySQL ready on :3306"

  # ── 2. Backend ────────────────────────────────────────────────────────────
  log "Starting backend..."

  if port_in_use $BACKEND_PORT; then
    warn "Port $BACKEND_PORT already in use — killing existing process"
    kill_port $BACKEND_PORT
  fi

  # Source .env if it exists
  if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$PROJECT_ROOT/.env"
    set +a
  fi

  cd "$BACKEND_DIR"
  nohup mvn spring-boot:run \
    -q \
    >"$LOG_DIR/backend.log" 2>&1 &
  echo $! >"$LOG_DIR/backend.pid"
  cd "$PROJECT_ROOT"

  # Wait for backend to be ready
  log "Waiting for backend on :$BACKEND_PORT..."
  retries=60
  while ! port_in_use $BACKEND_PORT; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      err "Backend did not start in time. Check $LOG_DIR/backend.log"
      exit 1
    fi
    sleep 2
  done
  ok "Backend ready on :$BACKEND_PORT"

  # ── 3. Frontend ───────────────────────────────────────────────────────────
  log "Starting frontend..."

  if port_in_use $FRONTEND_PORT; then
    warn "Port $FRONTEND_PORT already in use — killing existing process"
    kill_port $FRONTEND_PORT
  fi

  cd "$FRONTEND_DIR"
  nohup npx ng serve --proxy-config proxy.conf.json \
    >"$LOG_DIR/frontend.log" 2>&1 &
  echo $! >"$LOG_DIR/frontend.pid"
  cd "$PROJECT_ROOT"

  # Wait for frontend to be ready
  log "Waiting for frontend on :$FRONTEND_PORT..."
  retries=60
  while ! port_in_use $FRONTEND_PORT; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      err "Frontend did not start in time. Check $LOG_DIR/frontend.log"
      exit 1
    fi
    sleep 2
  done
  ok "Frontend ready on :$FRONTEND_PORT"

  echo ""
  log "🎉 Endia is up and running!"
  echo ""
  echo "  Frontend  → http://localhost:$FRONTEND_PORT"
  echo "  Backend   → http://localhost:$BACKEND_PORT"
  echo "  MySQL     → localhost:3306"
  echo ""
  echo "  Logs: $LOG_DIR/"
  echo "  Stop: $0 stop"
  echo ""
}

# ─── Entrypoint ───────────────────────────────────────────────────────────────
case "${1:-start}" in
  start)  start_all ;;
  stop)   stop_all ;;
  status) status ;;
  restart)
    stop_all
    sleep 2
    start_all
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac
