#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="$project_root/.funnel-proof/demo"
log_dir="$runtime_dir/logs"
collector_pid_file="$runtime_dir/collector.pid"
dashboard_pid_file="$runtime_dir/dashboard.pid"

mkdir -p "$log_dir"

if [[ -d "$project_root/node_modules" ]] || [[ -d "$project_root/apps/demo-saas/node_modules" ]]; then
  :
else
  echo "Missing Node dependencies. Run npm install once, then retry." >&2
  exit 1
fi

if [[ -f "$collector_pid_file" ]] || [[ -f "$dashboard_pid_file" ]]; then
  echo "A FunnelProof demo PID file already exists. Run make demo-stop before starting again." >&2
  exit 1
fi

if curl --silent --fail --max-time 1 -H 'x-funnel-proof-workspace-key: fp_public_local_demo' http://127.0.0.1:8080/fp/insights/funnel >/dev/null 2>&1; then
  echo "Port 8080 already serves a collector. Stop it or use that collector manually; demo-start will not reuse unknown local state." >&2
  exit 1
fi

if curl --silent --fail --max-time 1 http://127.0.0.1:5173/ >/dev/null 2>&1; then
  echo "Port 5173 already serves a dashboard. Stop it before running demo-start." >&2
  exit 1
fi

nohup bash -c '
  cd "$1/services/collector"
  exec env \
    FUNNEL_PROOF_DATA_DIR="$2/events" \
    FUNNEL_PROOF_EVENT_LOG_DIR="$2/event-log" \
    mvn -Dmaven.repo.local=../../.m2 compile exec:java -Dexec.mainClass=dev.funnelproof.collector.CollectorApplication
' bash "$project_root" "$runtime_dir" >"$log_dir/collector.log" 2>&1 < /dev/null &
echo $! >"$collector_pid_file"

nohup bash -c '
  cd "$1"
  exec npm run demo -- --host 127.0.0.1
' bash "$project_root" >"$log_dir/dashboard.log" 2>&1 < /dev/null &
echo $! >"$dashboard_pid_file"

for _ in $(seq 1 50); do
  if curl --silent --fail --max-time 1 -H 'x-funnel-proof-workspace-key: fp_public_local_demo' http://127.0.0.1:8080/fp/insights/funnel >/dev/null 2>&1 \
    && curl --silent --fail --max-time 1 http://127.0.0.1:5173/ >/dev/null 2>&1; then
    echo "FunnelProof local demo is ready: http://127.0.0.1:5173"
    echo "Next: make demo-data SCENARIO=healthy && make demo-check"
    exit 0
  fi
  sleep 0.2
done

echo "The local demo did not become ready. See $log_dir/collector.log and $log_dir/dashboard.log" >&2
bash "$project_root/scripts/demo-stop.sh" >/dev/null 2>&1 || true
exit 1
