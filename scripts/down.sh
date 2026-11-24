#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
export DOCKER_CONTEXT=rancher-desktop
CLUSTER=${CLUSTER:-rd}
if [ "$CLUSTER" = k3d ]; then
  echo "deleting k3d cluster"
  make --no-print-directory cluster-down
  exit 0
fi
ctx=rancher-desktop
echo "removing Tally from Rancher Desktop"
helm --kube-context $ctx -n tally uninstall tally 2>/dev/null || true
kubectl --context $ctx get crd clusters.postgresql.cnpg.io >/dev/null 2>&1 \
  && kubectl --context $ctx -n tally delete cluster tally-db --ignore-not-found --wait=true
kubectl --context $ctx delete namespace tally --ignore-not-found --wait=true
helm --kube-context $ctx -n cnpg-system uninstall cnpg 2>/dev/null || true
kubectl --context $ctx delete namespace cnpg-system --ignore-not-found --wait=true
echo "down"
