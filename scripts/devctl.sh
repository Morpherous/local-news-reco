#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"
BACKEND_LOG="$RUN_DIR/backend.log"
FRONTEND_LOG="$RUN_DIR/frontend.log"
BACKEND_JAR="$ROOT_DIR/target/local-news-reco-0.0.1.jar"

BACKEND_PORT="${PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-4321}"
STOP_TIMEOUT_SECONDS="${STOP_TIMEOUT_SECONDS:-15}"
START_STABLE_SECONDS="${START_STABLE_SECONDS:-4}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/devctl.sh start
  ./scripts/devctl.sh stop
  ./scripts/devctl.sh restart
  ./scripts/devctl.sh status
  ./scripts/devctl.sh logs [backend|frontend]

Env:
  PORT                  Backend port (default: 8080)
  FRONTEND_PORT         Astro dev port (default: 4321)
  STOP_TIMEOUT_SECONDS  Graceful stop timeout (default: 15)
  START_STABLE_SECONDS  Seconds to verify process remains alive after start (default: 4)
EOF
}

ensure_run_dir() {
  mkdir -p "$RUN_DIR"
}

read_pid() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    tr -d '[:space:]' <"$pid_file"
  fi
}

is_pid_running() {
  local pid="${1:-}"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

stop_pid_file() {
  local name="$1"
  local pid_file="$2"
  local pid
  pid="$(read_pid "$pid_file")"

  if [[ -z "$pid" ]]; then
    echo "[$name] not running"
    rm -f "$pid_file"
    return 0
  fi

  if ! is_pid_running "$pid"; then
    echo "[$name] stale pid $pid, cleaning"
    rm -f "$pid_file"
    return 0
  fi

  echo "[$name] stopping pid=$pid"
  kill "$pid" 2>/dev/null || true

  local i
  for ((i = 0; i < STOP_TIMEOUT_SECONDS; i++)); do
    if ! is_pid_running "$pid"; then
      rm -f "$pid_file"
      echo "[$name] stopped"
      return 0
    fi
    sleep 1
  done

  echo "[$name] force killing pid=$pid"
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$pid_file"
}

start_backend() {
  local pid
  pid="$(read_pid "$BACKEND_PID_FILE")"
  if is_pid_running "$pid"; then
    echo "[backend] already running pid=$pid"
    return 0
  fi
  rm -f "$BACKEND_PID_FILE"

  if [[ ! -f "$BACKEND_JAR" ]]; then
    echo "[backend] jar not found, building..."
    (cd "$ROOT_DIR" && mvn -q -DskipTests package)
  fi

  echo "[backend] starting on port $BACKEND_PORT"
  (
    cd "$ROOT_DIR"
    PORT="$BACKEND_PORT" nohup java -jar "$BACKEND_JAR" >>"$BACKEND_LOG" 2>&1 &
    echo $! >"$BACKEND_PID_FILE"
  )

  sleep "$START_STABLE_SECONDS"
  pid="$(read_pid "$BACKEND_PID_FILE")"
  if is_pid_running "$pid"; then
    echo "[backend] started pid=$pid log=$BACKEND_LOG"
  else
    echo "[backend] failed to start, check log: $BACKEND_LOG"
    return 1
  fi
}

start_frontend() {
  local pid
  pid="$(read_pid "$FRONTEND_PID_FILE")"
  if is_pid_running "$pid"; then
    echo "[frontend] already running pid=$pid"
    return 0
  fi
  rm -f "$FRONTEND_PID_FILE"

  if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
    echo "[frontend] node_modules missing, installing..."
    (cd "$ROOT_DIR/frontend" && npm install)
  fi

  echo "[frontend] starting on port $FRONTEND_PORT"
  (
    cd "$ROOT_DIR/frontend"
    nohup npm run dev -- --host 0.0.0.0 --port "$FRONTEND_PORT" >>"$FRONTEND_LOG" 2>&1 &
    echo $! >"$FRONTEND_PID_FILE"
  )

  sleep "$START_STABLE_SECONDS"
  pid="$(read_pid "$FRONTEND_PID_FILE")"
  if is_pid_running "$pid"; then
    echo "[frontend] started pid=$pid log=$FRONTEND_LOG"
  else
    echo "[frontend] failed to start, check log: $FRONTEND_LOG"
    return 1
  fi
}

show_status() {
  local pid

  pid="$(read_pid "$BACKEND_PID_FILE")"
  if is_pid_running "$pid"; then
    echo "[backend] running pid=$pid port=$BACKEND_PORT"
  else
    echo "[backend] stopped"
  fi

  pid="$(read_pid "$FRONTEND_PID_FILE")"
  if is_pid_running "$pid"; then
    echo "[frontend] running pid=$pid port=$FRONTEND_PORT"
  else
    echo "[frontend] stopped"
  fi
}

show_logs() {
  local target="${1:-all}"
  ensure_run_dir
  touch "$BACKEND_LOG" "$FRONTEND_LOG"
  case "$target" in
    backend)
      tail -n 120 -f "$BACKEND_LOG"
      ;;
    frontend)
      tail -n 120 -f "$FRONTEND_LOG"
      ;;
    all)
      tail -n 120 -f "$BACKEND_LOG" "$FRONTEND_LOG"
      ;;
    *)
      echo "Unknown logs target: $target"
      usage
      exit 1
      ;;
  esac
}

main() {
  ensure_run_dir
  local cmd="${1:-}"

  case "$cmd" in
    start)
      start_backend
      start_frontend
      ;;
    stop)
      stop_pid_file "frontend" "$FRONTEND_PID_FILE"
      stop_pid_file "backend" "$BACKEND_PID_FILE"
      ;;
    restart)
      stop_pid_file "frontend" "$FRONTEND_PID_FILE"
      stop_pid_file "backend" "$BACKEND_PID_FILE"
      start_backend
      start_frontend
      ;;
    status)
      show_status
      ;;
    logs)
      show_logs "${2:-all}"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
