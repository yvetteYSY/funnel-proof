#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="$project_root/.funnel-proof/demo"
stopped=0

for name in collector dashboard; do
  pid_file="$runtime_dir/$name.pid"
  if [[ ! -f "$pid_file" ]]; then
    continue
  fi
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    stopped=1
  fi
  rm "$pid_file"
done

if [[ "$stopped" == "1" ]]; then
  echo "Stopped FunnelProof local demo services. Local synthetic data remains in $runtime_dir."
else
  echo "No running FunnelProof demo services found."
fi
