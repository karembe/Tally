# Benchmarks

## Transfer load, Phase 2 baseline

`make load`: k6, 50 virtual users, 2 minutes, random transfers between 50 funded EUR accounts, each with a unique `Idempotency-Key`.

| Metric | Result |
|---|---|
| Transfers completed | 53,509 |
| Throughput | 441 transfers/s |
| Latency avg / median | 111.8 ms / 105.8 ms |
| Latency p95 / p99 / max | 198 ms / 264 ms / 502 ms |
| Failed requests | 0 of 53,609 |

Ledger state read from `/integrity` right after the run:

| Check | Result |
|---|---|
| Sum of all balances | 0 |
| Entries per transfer | 2 (107,122 entries for 53,561 transfers) |
| Negative user accounts | 0 |
| Hash chain | valid, 53,561 events checked |

### Setup

- Rancher Desktop k3s on a laptop (VM 10 GB, 6 CPU), shared with other workloads.
- 2 Tally replicas (request 100m CPU and 384 Mi, memory limit 512 Mi, no CPU limit).
- 1 PostgreSQL 17.11 instance on CloudNativePG, 2 Gi local-path volume.
- k6 ran on the host and reached the service through `kubectl port-forward`, which adds overhead.

Single run on a laptop: read these as a baseline for later phases, not as capacity numbers.
