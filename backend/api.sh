#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
# SECURITAS - controle da API
#
# Sobe/derruba a API e o simulador de hardware juntos, com PID e log
# por processo. Pensado para uso diário durante o desenvolvimento.
#
# Uso:
#   ./api.sh start     inicia API + um simulador por veículo (padrão)
#   ./api.sh stop      derruba tudo
#   ./api.sh restart   stop + start
#   ./api.sh status    mostra o que está de pé
#   ./api.sh logs      acompanha os logs (Ctrl+C sai)
#
# Variáveis de ambiente:
#   PORT              porta da API (padrão: a do .env, fallback 3000)
#   START_SIMULATOR   "false" para subir só a API, sem os simuladores
#   SIM_FLEET         quantos veículos simular (padrão: todos os que têm device)
#   VTSIM_DEVICE_ID   fixa um único device (ignora a frota)
#   VTSIM_API_KEY     credencial do device fixado
# ─────────────────────────────────────────────────────────────────────────

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$DIR/.run"
API_PID="$RUN_DIR/api.pid"
SIM_PID="$RUN_DIR/sim.pid"
API_LOG="$RUN_DIR/api.log"
SIM_LOG="$RUN_DIR/sim.log"

mkdir -p "$RUN_DIR"
cd "$DIR"

# ── Auxiliários ──────────────────────────────────────────────────────────

# Lê uma chave do .env sem exportar o arquivo inteiro para o ambiente.
env_value() {
  local key="$1" fallback="${2:-}"
  local val
  val="$(grep -E "^${key}=" "$DIR/.env" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r"'"'"' ' || true)"
  [ -n "$val" ] && printf '%s' "$val" || printf '%s' "$fallback"
}

is_running() {
  local pidfile="$1"
  [ -f "$pidfile" ] || return 1
  local pid
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

# Primeiro token do cmdline, usado para identificar o processo com certeza.
# "comm" não serve: o Node 24 aparece como "MainThread" em ps/ss.
argv0() {
  tr '\0' '\n' <"/proc/$1/cmdline" 2>/dev/null | head -1
}

PORT="${PORT:-$(env_value PORT 3000)}"
# PORT inválido vindo do ambiente (ex.: PORT=0) faria o health check bater na
# porta errada. Mantém apenas portas TCP válidas (1..65535).
if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  PORT="$(env_value PORT 3000)"
fi
if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  PORT=3000
fi
API_URL="http://localhost:${PORT}"

# Espera o /health responder antes de devolver o controle.
wait_for_health() {
  local tries=30
  while [ "$tries" -gt 0 ]; do
    if curl -fsS "${API_URL}/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
    tries=$((tries - 1))
  done
  return 1
}

# ── start ────────────────────────────────────────────────────────────────

# PIDs dos processos "node <entry>" que já existiam, separados por espaço.
# O pgrep -f também casa com o shell que contém o mesmo texto, e o "comm"
# não serve porque o Node 24 se renomeia para "MainThread".
node_pids() {
  local p
  for p in $(pgrep -f "node ${1}" 2>/dev/null); do
    if [ "$(argv0 "$p")" = "node" ]; then printf '%s ' "$p"; fi
  done
}

# Lança um processo em sessão própria e devolve o PID do node recém-criado.
# O setsid evita que o processo receba o SIGTERM do shell. O PID do lançador
# não serve (o setsid pode bifurcar), então comparamos a lista de antes com a
# de depois - necessário porque todos os simuladores compartilham o mesmo
# cmdline e o pgrep devolveria sempre o mesmo PID.
launch_bg() {
  local logfile="$1" entry="$2"
  local before after p
  before="$(node_pids "$entry")"
  setsid nohup node "$entry" >>"$logfile" 2>&1 </dev/null &
  disown 2>/dev/null || true
  sleep 0.4
  after="$(node_pids "$entry")"
  for p in $after; do
    case " ${before} " in
      *" ${p} "*) continue ;;
      *) printf '%s' "$p"; return 0 ;;
    esac
  done
  return 1
}

start() {
  if [ "${START_SIMULATOR:-true}" = "false" ]; then
    export START_SIMULATOR=false
  else
    export START_SIMULATOR=true
  fi

  if is_running "$API_PID"; then
    echo "[api] já está no ar (pid $(cat "$API_PID")) em ${API_URL}"
  else
    : >"$API_LOG"
    local pid
    pid="$(launch_bg "$API_LOG" src/server.js)" || true
    [ -n "$pid" ] && echo "$pid" >"$API_PID"
    if [ -z "$pid" ] || ! is_running "$API_PID"; then
      echo "[api] FALHOU ao iniciar. Últimas linhas de $API_LOG:" >&2
      tail -n 15 "$API_LOG" >&2
      return 1
    fi
    if wait_for_health; then
      echo "[api] no ar (pid ${pid}) em ${API_URL}"
    else
      echo "[api] FALHOU ao subir. Últimas linhas de $API_LOG:" >&2
      tail -n 15 "$API_LOG" >&2
      return 1
    fi
  fi

  # A frota virtual (server-sim) agora é filha do processo da API: o painel
  # adiciona/remove veículos da simulação sem precisar de restart. START_SIMULATOR
  # ainda desliga isso (exportado para o Node). PIDs antigos de sim-*.pid são
  # só resíduos de versões anteriores — o stop abaixo continua limpando.
  if [ "${START_SIMULATOR:-true}" = "false" ]; then
    export START_SIMULATOR=false
    echo "[sim] pulado (START_SIMULATOR=false)"
  else
    export START_SIMULATOR=true
    echo "[sim] gerenciado pela API — no painel: Simular / Tirar da simulação"
  fi
}

# ── stop ─────────────────────────────────────────────────────────────────

stop_one() {
  local name="$1" pidfile="$2"
  if ! is_running "$pidfile"; then
    rm -f "$pidfile"
    echo "[$name] não estava rodando"
    return 0
  fi
  local pid
  pid="$(cat "$pidfile")"
  # Mata só o PID registrado. O node não tem filhos, e matar por grupo
  # seria arriscado se o PID estivesse recycled.
  kill -TERM "$pid" 2>/dev/null || true
  local tries=20
  while [ "$tries" -gt 0 ] && kill -0 "$pid" 2>/dev/null; do
    sleep 0.25
    tries=$((tries - 1))
  done
  kill -0 "$pid" 2>/dev/null && kill -KILL "$pid" 2>/dev/null || true
  rm -f "$pidfile"
  echo "[$name] parado (pid ${pid})"
}

stop() {
  # Derruba cada simulador da frota; sim.pid é cópia do sim-0.pid.
  local pidfile
  for pidfile in "$RUN_DIR"/sim-*.pid; do
    [ -e "$pidfile" ] || continue
    stop_one "sim $(basename "$pidfile" .pid | sed 's/sim-//')" "$pidfile"
  done
  rm -f "$SIM_PID"
  stop_one api "$API_PID"
}

# ── status / logs ────────────────────────────────────────────────────────

status() {
  if is_running "$API_PID"; then
    echo "[api] rodando (pid $(cat "$API_PID")) em ${API_URL}"
  else
    echo "[api] parado"
  fi
  local pidfile n=0
  for pidfile in "$RUN_DIR"/sim-*.pid; do
    [ -e "$pidfile" ] || continue
    if is_running "$pidfile"; then
      n=$((n + 1))
      echo "[sim] rodando (pid $(cat "$pidfile"))"
    fi
  done
  [ "$n" -eq 0 ] && echo "[sim] parado"
  if command -v curl >/dev/null 2>&1; then
    echo "[health] $(curl -fsS "${API_URL}/health" 2>/dev/null || echo 'sem resposta')"
  fi
}

logs() {
  touch "$API_LOG" "$SIM_LOG"
  tail -n 40 -F "$API_LOG" "$SIM_LOG"
}

case "${1:-start}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; echo; start ;;
  status)  status ;;
  logs)    logs ;;
  *)
    echo "Uso: $0 {start|stop|restart|status|logs}" >&2
    exit 1
    ;;
esac
