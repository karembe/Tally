# Roadmap

Each phase ends on a green checkpoint: exit criteria met, verified, evidenced in the repo.
Nothing is claimed in the README before its checkpoint is green.

## Identity

**A ledger you can give root to and still not lose a cent.**

The ledger is deliberately small. It is the asset under protection. The project is the delivery chain
around it and the evidence that every control works. Threats are modelled by compromised actor, not by
component (see [SECURITY](SECURITY.md)).

**MVP claim (target):** 6 insider and supply-chain attacks, run on demand and in CI, 6/6 blocked or
detected, median time-to-detect under 10 s, evidence report generated automatically.
**Full claim (target):** 13/13.

## Phases

| # | Phase | Scope | Checkpoint |
|---|---|---|---|
| 0 | Foundations | tools, docs, threat model, ADRs, pre-commit, local CI | project understood in 5 min; secret blocked locally |
| 1 | Ledger core | accounts, idempotent double-entry transfers, row locking, append-only hash-chained events | concurrency, replay and crash tests green |
| 2 | Run it | distroless image, k3s, Helm, CloudNativePG, `make up` | `make up && make smoke` from clean |
| 3 | Secure pipeline | gitleaks, Semgrep, Trivy, SBOM, cosign sign and attest | A01, A03 blocked |
| 4 | Tamper evidence | Merkle checkpoints, KMS-signed heads, Object Lock anchor, Go verifier | A10, A11 detected, exact event named |
| 5 | MVP | Argo CD, Kyverno, Pod Security, backup and restore drill, attack runner, evidence report, Grafana | `make attack` 6/6 |
| 6 | Public | README numbers, demo video, GitLab with GitHub mirror, keyless signing | pipeline green on GitLab |
| V2 | Hardening | Vault, Cilium, Falco, provenance, IaC policy, canary, migrations, key rotation, Tally Live page | 12/12 |
| V3 | Real cloud | AWS run via Terraform and Ansible, public challenge, blog post | 13/13 on AWS, < €20 per run |

## Operations

| Topic | Evidence |
|---|---|
| Backup and restore | scheduled backups, PITR drill, RTO and RPO measured, restored ledger verified against anchored heads |
| Deploys | GitOps only; canary with automatic rollback (V2) |
| Schema changes | expand and contract, zero downtime under load (V2) |
| Key rotation | new signing key, old heads still verify (V2) |
| SLOs | verifier freshness, zero reconciliation drift |
| Incidents | runbook section per alert, written postmortems |

## Interfaces

- REST API: accounts, transfers, checkpoints, inclusion proofs
- `tally` CLI: `verify`, `proof`, `transfer`, `attack`, `evidence`
- Grafana security posture dashboard
- Tally Live, a read-only status page (V2)

## Metrics to publish

| Metric | Target |
|---|---|
| Attacks blocked or detected | 6/6 (MVP), 13/13 (full) |
| Median / max time-to-detect | < 10 s / < 60 s |
| MR pipeline duration | < 10 min |
| Critical CVEs in shipped image | 0 |
| Static credentials in repo or cluster | 0 (after Vault) |
| Restore drill RTO | measured and published |
| Cost per AWS validation run | < €20 |

## Demo (2 minutes)

1. Dashboard green, reconciliation drift 0.
2. As DBA, edit a balance: the verifier names the account.
3. Rewrite history and recompute the chain: caught by the anchored signed head.
4. Deploy an unsigned image: admission denied.
5. Evidence report: 6/6.

## Risks

| Risk | Mitigation |
|---|---|
| Scope creep | attack list is fixed; new ideas go to `docs/IDEAS.md` |
| LocalStack gaps (Object Lock, KMS sign) | spike at the start of phase 4; validate on real AWS in V3 |
| Laptop resources | k3s, small requests and limits, k3d for throwaway clusters |
| Glue-only perception | original parts stated: verifier, Merkle anchoring, policies with tests, attack runner, evidence generator |
