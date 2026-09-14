# Architecture

> Status: placeholder for Phase 0. This document will evolve with each green checkpoint.

## System intent

Tally is a deliberately small financial ledger designed to demonstrate correctness under retries, concurrency, crashes, and tampering.

## Planned boundaries

- `app/` — Spring Boot ledger service
- `verifier/` — standalone integrity verifier
- PostgreSQL — transactional state and event log
- Kubernetes + Helm + Argo CD — runtime and GitOps deployment
- AWS / LocalStack — S3, KMS, IAM, Secrets Manager, and later RDS
- Prometheus / Grafana / OpenTelemetry — metrics, dashboards, and traces

Detailed diagrams will be added as the walking skeleton becomes executable.
