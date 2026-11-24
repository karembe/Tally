#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DOCKER_CONTEXT=rancher-desktop
CLUSTER=${CLUSTER:-rd}
step() { printf '\n[%s/6] %s\n' "$1" "$2"; }

step 1 "cluster ($CLUSTER) and registry"
make --no-print-directory cluster CLUSTER="$CLUSTER"

step 2 "build and push image"
docker build -q -t localhost:5000/tally:dev app >/dev/null
docker push -q localhost:5000/tally:dev >/dev/null
digest=$(docker image inspect localhost:5000/tally:dev --format '{{index .RepoDigests 0}}' | sed 's/.*@//')
echo "image digest $digest"

step 3 "CloudNativePG operator"
helm repo add cnpg https://cloudnative-pg.github.io/charts >/dev/null 2>&1 || true
helm upgrade --install cnpg cnpg/cloudnative-pg --version 0.29.2 -n cnpg-system --create-namespace --wait --timeout 300s >/dev/null
echo "operator ready"

step 4 "secrets and database"
./scripts/secrets.sh tally
kubectl apply -f deploy/cnpg/cluster.yaml >/dev/null
kubectl -n tally wait --for=condition=Ready cluster/tally-db --timeout=900s
echo "database ready"

step 5 "deploy Tally"
helm upgrade --install tally deploy/helm/tally -n tally --set image.digest="$digest" --wait --timeout 300s >/dev/null

step 6 "ready"
kubectl -n tally get pods
