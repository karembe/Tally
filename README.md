<div align="center">

# Tally

### A ledger you can give root to and still not lose a cent.

![Status](https://img.shields.io/badge/status-in%20development-D97706)

</div>

## What it is

Tally is a small double-entry ledger (Java 21, Spring Boot, PostgreSQL) wrapped in a delivery chain
where no single insider can change money or code without being blocked or detected.

Developer, dependency, CI pipeline, cluster admin, DBA: assume any one of them is compromised.
Every change to code or money must also carry a proof that actor can't forge: a signature, an admission
policy, or a signed hash anchored outside their reach. An independent verifier checks the proofs continuously.

The demo: hand over cluster-admin and the database password, ask someone to steal one euro, and watch
the attempt get blocked or named within seconds.

## Status

Nothing below is claimed until its checkpoint is green. See [ROADMAP](docs/ROADMAP.md).

| Capability | Status |
|---|---|
| Idempotent, concurrency-safe double-entry transfers | planned |
| Append-only, hash-chained event log | planned |
| Signed Merkle checkpoints anchored in S3 Object Lock | planned |
| Independent Go verifier and `tally` CLI | planned |
| Secure pipeline: secrets, SAST, deps, SBOM, image scan, cosign | secret scanning only |
| Signed-image admission (Kyverno) and Pod Security `restricted` | planned |
| Backup, restore and verified recovery drill | planned |
| Scripted attack suite with evidence report | planned |

## Repository

```
app/          Spring Boot ledger
verifier/     Go verifier and tally CLI
deploy/       Helm, Argo CD, Kyverno policies
infra/        Terraform (cloud), Ansible (node bootstrap)
monitoring/   Prometheus, Grafana, alerts
attacks/      scripted attacks and expected outcomes
load/         k6 load tests
docs/         roadmap, architecture, security, runbook, ADRs
```

## Docs

- [Roadmap](docs/ROADMAP.md)
- [Security and threat model](docs/SECURITY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Decisions](docs/adr/)
- [Runbook](docs/RUNBOOK.md)
