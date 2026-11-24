#!/usr/bin/env bash
set -euo pipefail
ns=tally; port=18484
kubectl -n $ns port-forward svc/tally $port:8181 >/dev/null 2>&1 &
pf=$!; trap 'kill $pf 2>/dev/null' EXIT
for _ in $(seq 1 30); do curl -fs "http://127.0.0.1:$port/actuator/health" >/dev/null 2>&1 && break; sleep 1; done
export ADMIN_TOKEN=$(kubectl -n $ns get secret tally-admin -o jsonpath='{.data.token}' | base64 -d)
export BASE_URL=http://127.0.0.1:$port
k6 run --summary-trend-stats='avg,med,p(95),p(99),max' load/transfers.js
curl -fs "$BASE_URL/integrity"; echo
