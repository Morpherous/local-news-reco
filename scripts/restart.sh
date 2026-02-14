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

mkdir -p "$RUN_DIR"

read_pid() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 0
  tr -d '[:space:]' <"$pid_file"
}

is_running() {
  local pid="${1:-}"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

stop_by_pid_file() {
  local name="$1"
  local pid_file="$2"
  local pid
  pid="$(read_pid "$pid_file")"

  if [[ -z "$pid" ]]; then
    echo "[$name] not running"
    rm -f "$pid_file"
    return 0
  fi

  if ! is_running "$pid"; then
    echo "[$name] stale pid $pid, cleaning"
    rm -f "$pid_file"
    return 0
  fi

  echo "[$name] stopping pid=$pid"
  kill "$pid" 2>/dev/null || true

  local i
  for ((i = 0; i < STOP_TIMEOUT_SECONDS; i++)); do
    if ! is_running "$pid"; then
      echo "[$name] stopped"
      rm -f "$pid_file"
      return 0
    fi
    sleep 1
  done

  echo "[$name] force killing pid=$pid"
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$pid_file"
}

start_backend() {
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
  local pid
  pid="$(read_pid "$BACKEND_PID_FILE")"
  if is_running "$pid"; then
    echo "[backend] started pid=$pid"
  else
    echo "[backend] failed. check: $BACKEND_LOG"
    tail -n 40 "$BACKEND_LOG" || true
    exit 1
  fi
}

start_frontend() {
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
  local pid
  pid="$(read_pid "$FRONTEND_PID_FILE")"
  if is_running "$pid"; then
    echo "[frontend] started pid=$pid"
  else
    echo "[frontend] failed. check: $FRONTEND_LOG"
    tail -n 40 "$FRONTEND_LOG" || true
    exit 1
  fi
}

echo "== restart frontend + backend =="
stop_by_pid_file "frontend" "$FRONTEND_PID_FILE"
stop_by_pid_file "backend" "$BACKEND_PID_FILE"
start_backend
start_frontend
echo "== done =="
