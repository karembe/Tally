# Tally

> **A financial ledger service that stays provably correct under concurrency and crashes.**  
> *Does it tally? Prove it.*

Tally is a portfolio project focused on backend correctness, DevSecOps, cloud infrastructure, and SRE practices. The core goal is deliberately narrow: build a small financial ledger whose behavior can be **demonstrated and verified under concurrency, retries, crashes, and tampering**.

## Status

🚧 **In development — Phase 0: repository initialization.**

Nothing below is claimed as implemented until its checkpoint is green. The project will be built incrementally, keeping `main` demoable at every stage.

## Planned core

- Java 21 + Spring Boot 3
- PostgreSQL
- Idempotent, concurrency-safe, double-entry transfers
- Crash-safety + fault-injection harness
- Append-only hash-chained event log
- Standalone integrity verifier + continuous reconciliation
- Kubernetes + Helm + Argo CD
- Terraform + AWS / LocalStack
- Ansible host hardening
- GitLab CI/CD with DevSecOps gates
- Prometheus, Grafana, and OpenTelemetry

## Repository layout

```text
tally/
├── app/                   # Spring Boot service
├── verifier/              # tally-verify CLI
├── infra/
│   ├── terraform/         # cloud resources
│   └── ansible/           # host hardening
├── deploy/
│   ├── helm/tally/        # Helm chart
│   └── argocd/            # Argo CD application
├── monitoring/
│   ├── prometheus/
│   ├── grafana/
│   └── alerts/
├── load/                  # load / fault tests
└── docs/
    ├── ARCHITECTURE.md
    ├── SECURITY.md
    └── RUNBOOK.md
```

## Build philosophy

1. Keep a complete, demoable system at every checkpoint.
2. Put real depth into correctness and crash-safety before adding platform polish.
3. Treat security, reliability, and observability as design constraints from the start.

## License

License to be decided before the first public release.
