#!/usr/bin/env bash
set -euo pipefail
ns=tally; port=18383; base=http://127.0.0.1:$port; run=$RANDOM$RANDOM
fail() { echo "FAIL: $*"; exit 1; }
json() { python3 -I -c "import sys,json; print(json.load(sys.stdin)$1)"; }

kubectl -n $ns port-forward svc/tally $port:8181 >/dev/null 2>&1 &
pf=$!; trap 'kill $pf 2>/dev/null' EXIT
for _ in $(seq 1 30); do curl -fs "$base/actuator/health" >/dev/null 2>&1 && break; sleep 1; done
curl -fs "$base/actuator/health" >/dev/null || fail "app not reachable"
admin=$(kubectl -n $ns get secret tally-admin -o jsonpath='{.data.token}' | base64 -d)

post() { curl -s -w '\n%{http_code}' -X POST "$base$1" -H 'Content-Type: application/json' "${@:3}" -d "$2"; }
body() { sed '$d'; }
code() { tail -n1; }

echo "[1] open accounts"
alice=$(post /accounts '{"ownerName":"Alice","currency":"EUR"}' | body | json "['id']")
bob=$(post /accounts '{"ownerName":"Bob","currency":"EUR"}' | body | json "['id']")

echo "[2] fund alice 50000"
r=$(post /funding "{\"toAccountId\":\"$alice\",\"amount\":50000}" -H "X-Admin-Token: $admin" -H "Idempotency-Key: smoke-fund-$run")
[[ $(echo "$r" | code) == 2* ]] || fail "funding returned $(echo "$r" | code)"

echo "[3] transfer 1000 alice -> bob"
t="{\"fromAccountId\":\"$alice\",\"toAccountId\":\"$bob\",\"amount\":1000,\"currency\":\"EUR\"}"
r1=$(post /transfers "$t" -H "Idempotency-Key: smoke-tr-$run")
[[ $(echo "$r1" | code) == 201 ]] || fail "transfer returned $(echo "$r1" | code)"
id1=$(echo "$r1" | body | json "['id']")

echo "[4] balances"
ba=$(curl -fs "$base/accounts/$alice" | json "['balance']")
bb=$(curl -fs "$base/accounts/$bob" | json "['balance']")
[ "$ba" = 49000 ] || fail "alice balance $ba, expected 49000"
[ "$bb" = 1000 ] || fail "bob balance $bb, expected 1000"

echo "[5] replay same key: same transfer, no new money movement"
r2=$(post /transfers "$t" -H "Idempotency-Key: smoke-tr-$run")
[ "$(echo "$r2" | code)" = 200 ] || fail "replay returned $(echo "$r2" | code), expected 200"
[ "$(echo "$r2" | body | json "['id']")" = "$id1" ] || fail "replay returned a different transfer"
[ "$(curl -fs "$base/accounts/$bob" | json "['balance']")" = 1000 ] || fail "replay moved money"

echo "[6] same key, different body is rejected"
t2="{\"fromAccountId\":\"$alice\",\"toAccountId\":\"$bob\",\"amount\":2000,\"currency\":\"EUR\"}"
[ "$(post /transfers "$t2" -H "Idempotency-Key: smoke-tr-$run" | code)" = 409 ] || fail "expected 409"

echo "[7] ledger integrity"
i=$(curl -fs "$base/integrity")
[ "$(echo "$i" | json "['chainValid']")" = True ] || fail "chain invalid: $i"
[ "$(echo "$i" | json "['sumOfBalances']")" = 0 ] || fail "balances do not sum to zero: $i"

echo "smoke OK"
