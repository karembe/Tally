#!/usr/bin/env bash
set -euo pipefail
ns=${1:-tally}
kubectl get namespace "$ns" >/dev/null 2>&1 || kubectl create namespace "$ns" >/dev/null
rand() { head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24; }
if ! kubectl -n "$ns" get secret tally-db-owner >/dev/null 2>&1; then
  kubectl -n "$ns" create secret generic tally-db-owner --type=kubernetes.io/basic-auth \
    --from-literal=username=tally --from-literal=password="$(rand)" >/dev/null
fi
if ! kubectl -n "$ns" get secret tally-db-passwords >/dev/null 2>&1; then
  kubectl -n "$ns" create secret generic tally-db-passwords \
    --from-literal=app="$(rand)" --from-literal=verifier="$(rand)" >/dev/null
fi
if ! kubectl -n "$ns" get secret tally-admin >/dev/null 2>&1; then
  kubectl -n "$ns" create secret generic tally-admin --from-literal=token="$(rand)" >/dev/null
fi
echo "secrets ready in namespace $ns"
