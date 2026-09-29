#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
# VEICULOTRACKER - controle da API
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

  if [ "${START_SIMULATOR:-true}" = "false" ]; then
    echo "[sim] pulado (START_SIMULATOR=false)"
    return 0
  fi

  if is_running "$SIM_PID"; then
    echo "[sim] já está no ar (pid $(cat "$SIM_PID"))"
    return 0
  fi

  # Lista a frota: um device por veículo vinculado. SIM_FLEET limita a
  # quantidade, o que ajuda quando o banco tem muitos veículos de teste.
  local limit="${SIM_FLEET:-0}" rows
  rows="$(cd "$DIR" && node -e "
    const { DatabaseSync } = require('node:sqlite');
    const db = new DatabaseSync('./data/veiculotracker.db', { readOnly: true });
    const limit = ${limit:-0};
    const sql = \`SELECT d.device_id, d.api_key, v.plate
                 FROM devices d JOIN vehicles v ON v.id = d.vehicle_id
                 WHERE d.vehicle_id IS NOT NULL
                 ORDER BY d.id\` + (limit > 0 ? ' LIMIT ' + limit : '');
    for (const r of db.prepare(sql).all()) {
      console.log([r.device_id, r.api_key, r.plate].join('\t'));
    }
  " 2>/dev/null || true)"

  if [ -z "$rows" ]; then
    echo "[sim] ignorado: nenhum veículo com device. Rode 'npm run seed' antes." >&2
    return 0
  fi

  # VTSIM_DEVICE_ID fixa um device só e ignora a frota.
  local env_device env_key
  env_device="$(env_value VTSIM_DEVICE_ID)"
  env_key="$(env_value VTSIM_API_KEY)"
  if [ -n "$env_device" ] && [ -n "$env_key" ]; then
    rows="$(printf '%s\t%s\t(fixo)\n' "$env_device" "$env_key")"
  fi

  : >"$SIM_LOG"
  export VTSIM_API_URL="$API_URL"
  rm -f "$RUN_DIR"/sim-*.pid

  local count=0 line device_id api_key plate pid
  while IFS=$'\t' read -r device_id api_key plate; do
    [ -n "$device_id" ] || continue
    # Precisa ser export antes da chamada: um prefixo "VAR=x $(...)" só
    # valeria para a expansão da própria substituição, e o launch_bg roda
    # dentro dela - o node nasceria sem as credenciais.
    export VTSIM_DEVICE_ID="$device_id" VTSIM_API_KEY="$api_key" VTSIM_API_URL="$API_URL"
    pid="$(launch_bg "$SIM_LOG" src/hardware-simulator.js)" || true
    if [ -n "$pid" ]; then
      echo "$pid" >"$RUN_DIR/sim-${count}.pid"
      echo "[sim] ${count} no ar (pid ${pid}) - ${plate} / ${device_id}"
      count=$((count + 1))
    else
      echo "[sim] ${count} FALHOU ao subir - ${device_id}" >&2
    fi
  done <<<"$rows"

  if [ "$count" -eq 0 ]; then
    echo "[sim] nenhum simulador subiu. Log:" >&2
    tail -n 15 "$SIM_LOG" >&2
    return 1
  fi

  # O primeiro simulador fica no sim.pid, para stop/status antigos seguirem
  # funcionando sem mudança.
  cp "$RUN_DIR/sim-0.pid" "$SIM_PID"
  sleep 1
  echo "[sim] ${count} simulador(es) ativo(s)"
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
