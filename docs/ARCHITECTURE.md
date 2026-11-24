# Architecture

## Overview

```
 Developer ──► Git ──► CI ─────────────────────────────────────────► Registry
                       secrets · SAST · deps · test · build · SBOM       │ signed image
                       image scan · cosign sign + attest                 │ + attestations
                                                                         ▼
 Git (deploy/) ◄── Argo CD ──► Kubernetes (k3s)
                                 │ admission: Kyverno + Pod Security restricted
                   ┌─────────────┼────────────────────┐
                   ▼             ▼                    ▼
              ledger (app/)   verifier (verifier/)   Prometheus / Grafana
                   │             │ read-only
                   ▼             ▼
                PostgreSQL (CloudNativePG) ──► backups to S3
                   │
                   └─► signed tree heads ──► KMS sign ──► S3 Object Lock
```

## Components

| Component | Tech | Role |
|---|---|---|
| `app/` | Java 21, Spring Boot, JDBC, Flyway | accounts, transfers, hash-chained events, checkpoints, proofs |
| `verifier/` | Go | `tally` CLI: verify chain, roots, anchored heads, balances; attack runner; evidence report |
| PostgreSQL | CloudNativePG | state, append-only event log, backups and PITR |
| KMS | AWS KMS (LocalStack locally) | ECDSA P-256 signing of tree heads |
| Anchor bucket | S3 Object Lock, compliance mode | write-once copies of signed heads |
| CI | GitLab CI | security gates, signing, attestations |
| GitOps | Argo CD, Helm | Git is the only way to change the cluster |
| Admission | Kyverno, Pod Security Admission | only signed, hardened workloads run |
| Observability | Prometheus, Grafana, Alertmanager | security posture, verifier freshness, drift |

## A transfer

1. Client sends `POST /transfers` with an `Idempotency-Key`.
2. One database transaction:
   - claim the idempotency key; on a replay, return the stored response
   - lock both accounts in id order, check currency and funds
   - update balances, insert the transfer and its debit and credit entries
   - lock the chain head, append the event with `hash = SHA-256(prev_hash || canonical(event))`, move the head
3. Commit. Either everything is written or nothing is.

## A checkpoint

1. Every 10 s or 100 events, the app computes the RFC 6962 Merkle root over event hashes.
2. KMS signs `{tree_size, root, timestamp}`. The head is stored with an outbox row in the same transaction.
3. An outbox worker writes the head to the Object Lock bucket.

## Verification

The verifier, with its own read-only credentials:

1. recomputes the hash chain from genesis
2. recomputes Merkle roots and checks each anchored head's signature and root
3. checks consistency between consecutive heads
4. recomputes balances from events and compares them with `accounts`

Any mismatch names the exact event or account, exits non-zero and raises an alert.
