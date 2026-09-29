# Roadmap

> Flagship plan. Each phase ends on a **green checkpoint**: exit criteria are met, verified, and evidenced in the repo. Nothing is claimed in the README before its checkpoint is green.

## 1. Identity

**Tally is a financial ledger whose entire delivery chain is built so that no single compromised actor — developer, dependency, CI pipeline, cluster admin, DBA, or cloud admin — can change money or code without being blocked or detected.**

The ledger itself is deliberately small. It is the *asset under protection*. The project is the secure delivery chain, the platform, and the **evidence** that every control works.

**Headline claim (target):**
> 12 scripted insider and supply-chain attacks, run on every pipeline. 12/12 blocked or detected. Median time-to-detect under 10 s. Audit evidence generated automatically.

**Target roles:** DevSecOps, cloud security, platform security. Target companies: SG, BNP, Servier, AWS, Datadog, Doctolib, Google, plus consulting (Deloitte, Capgemini).

## 2. Threat model by actor

| # | Compromised actor | Attack | Primary control | Outcome |
|---|---|---|---|---|
| A01 | Developer | Commits a secret | gitleaks in pre-commit + CI | Blocked |
| A02 | Developer | Pushes injectable/backdoored code | Semgrep SAST, protected `main`, required review | Blocked |
| A03 | Dependency | Adds a package with a known critical CVE | Trivy/Grype dependency scan, fail on CRITICAL | Blocked |
| A04 | Developer (IaC) | Terraform opens `0.0.0.0/0` or a public bucket | Checkov + OPA/Conftest on `terraform plan` | Blocked |
| A05 | CI / registry | Deploys an unsigned image | Kyverno `verifyImages` (cosign signature) | Blocked |
| A06 | CI | Deploys a signed image built outside `main` | Kyverno checks SLSA provenance attestation (ref, builder) | Blocked |
| A07 | Cluster admin | Runs a privileged / hostPath pod | Pod Security Admission `restricted` + Kyverno | Blocked |
| A08 | Cluster admin | `kubectl exec` into the ledger pod | Falco rule + K8s audit log shipped off-cluster | Detected |
| A09 | Compromised pod | Exfiltrates to the internet | Cilium default-deny egress, Hubble drop alert | Blocked + detected |
| A10 | DBA | `UPDATE` a balance row directly | Reconciliation job + verifier recomputes from event log | Detected |
| A11 | DBA | Rewrites event history and recomputes the hash chain | Signed tree heads (KMS) anchored in S3 Object Lock | Detected |
| A12 | Anyone | Uses leaked DB credentials | Vault dynamic credentials, short TTL, no static secrets | Blocked after TTL |

Out of scope (stated in `SECURITY.md`): compromise of the Sigstore root, of the AWS account root, kernel exploits, and a colluding DBA + cloud admin + CI.

## 3. Architecture

```
 Developer ──► GitLab ──► CI pipeline ─────────────────────────────► Registry
                          secrets · SAST · deps · SBOM · IaC policy     │
                          build (rootless) · image scan                 │ signed image
                          cosign sign + SLSA provenance                 │ + attestations
                                                                        ▼
 Git (deploy/) ◄── Argo CD ──► Kubernetes (kind + Cilium, later EKS)
                                 │ admission: Kyverno + PSA restricted
                                 │
                   ┌─────────────┼───────────────────┐
                   ▼             ▼                   ▼
             ledger (app/)   verifier (verifier/)  Falco / Tetragon
                   │             │ read-only role        │
                   ▼             ▼                        ▼
               PostgreSQL ◄── Vault (dynamic creds)   Loki / Prometheus / Grafana
                   │
                   └─► signed tree heads ──► KMS sign ──► S3 Object Lock (compliance)
```

**Components**

| Component | Tech | Security role |
|---|---|---|
| `app/` ledger | Java 21, Spring Boot 3, PostgreSQL, Flyway | Double-entry transfers, idempotency keys, append-only event log with hash chain |
| `verifier/` | Small Go or Java CLI/CronJob | Independent: recomputes balances and Merkle roots, checks against anchored signed heads |
| CI | GitLab CI | Security gates, signing, provenance, attack suite, evidence report |
| Registry | GitLab Container Registry | Stores images, signatures, SBOM and provenance attestations |
| GitOps | Argo CD + Helm | Git is the only way to change the cluster |
| Admission | Kyverno, Pod Security Admission | Only signed, `main`-built, hardened workloads run |
| Network | Cilium + Hubble | Default-deny ingress/egress, observable drops |
| Secrets | Vault (database secrets engine) + External Secrets | No static DB credentials; per-pod short-lived users |
| Cloud | Terraform, LocalStack (dev), AWS (validation) | KMS signing key, S3 Object Lock bucket, IAM via GitLab OIDC |
| Runtime | Falco (or Tetragon) | Detects exec, unexpected writes, unexpected processes |
| Observability | Prometheus, Grafana, Loki, Alertmanager | Security dashboard, detection latency, reconciliation drift |

## 4. Phases

Estimates assume ~15 h/week. Total: ~14 weeks.

### Phase 0 — Foundations (week 1)
- [ ] Rewrite README, `ARCHITECTURE.md`, `SECURITY.md` around the DevSecOps identity
- [ ] STRIDE threat model per actor in `SECURITY.md`
- [ ] ADRs in `docs/adr/`: GitLab CI, Kyverno over OPA Gatekeeper, Cilium, Vault, LocalStack-first
- [ ] Branch protection on `main`, signed commits, pre-commit with gitleaks

**Checkpoint:** a reader understands the project, threat model, and scope in 5 minutes.

### Phase 1 — Walking skeleton (weeks 1–2)
- [ ] Spring Boot service: `POST /accounts`, `POST /transfers` (with `Idempotency-Key`), `GET /accounts/{id}`
- [ ] Double-entry schema via Flyway; transfers in one serializable/row-locked transaction
- [ ] Append-only `ledger_events` table with `prev_hash`, `hash`; DB role cannot `UPDATE`/`DELETE` it
- [ ] Testcontainers integration tests (including concurrent transfers and replayed idempotency keys)
- [ ] Distroless, non-root, read-only-rootfs image
- [ ] kind cluster with Cilium, Helm chart, `make up` brings everything up

**Checkpoint:** `make up && make smoke` passes from a clean machine.

### Phase 2 — Secure pipeline (weeks 3–4)
- [ ] Stages: `secrets → sast → deps → test → build → sbom → image-scan → sign → publish`
- [ ] gitleaks, Semgrep, Trivy (fs + image), syft SBOM (CycloneDX)
- [ ] Rootless build (Kaniko or BuildKit), image pinned by digest
- [ ] cosign keyless signing with GitLab OIDC; attach SBOM + SLSA provenance attestations
- [ ] Explicit gate policy (e.g. fail on CRITICAL, allow-list with expiry in-repo)
- [ ] Renovate for dependency updates

**Checkpoint:** A01–A03 each demonstrated by a deliberately bad MR that the pipeline rejects.

### Phase 3 — GitOps and admission (week 5)
- [ ] Argo CD manages everything under `deploy/`; no `kubectl apply` by humans
- [ ] Kyverno: verify cosign signature + provenance (`main` ref, trusted builder)
- [ ] Pod Security Admission `restricted` on the ledger namespace
- [ ] Cilium default-deny; explicit allow `ledger → postgres`, `verifier → postgres (read-only)`
- [ ] Resource limits, PodDisruptionBudget, no service-account token automount

**Checkpoint:** A05, A06, A07, A09 blocked, each with a recorded denial event.

### Phase 4 — Secrets, identity, and cloud (weeks 6–7)
- [ ] Vault in-cluster (dev mode off, auto-unseal via KMS in LocalStack/AWS)
- [ ] Vault database secrets engine: dynamic Postgres users, TTL 1 h
- [ ] Kubernetes auth for Vault; per-service policies
- [ ] Terraform modules: KMS signing key, S3 Object Lock bucket, IAM roles
- [ ] GitLab OIDC → AWS role for Terraform; zero long-lived AWS keys
- [ ] Checkov + Conftest on the JSON plan, policies with unit tests

**Checkpoint:** A04 and A12 demonstrated. `grep` for static credentials across repo and cluster returns nothing.

### Phase 5 — Tamper-evident audit trail (weeks 8–9)
- [ ] Merkle tree over `ledger_events`; checkpoint every N events or T seconds
- [ ] Signed tree head = `{size, root, timestamp}` signed with KMS (asymmetric)
- [ ] Anchor signed heads to S3 Object Lock in compliance mode
- [ ] Verifier: recompute chain + Merkle root, check consistency with every anchored head, recompute balances from events
- [ ] Verifier runs as a CronJob with its own read-only DB role and its own IAM role (read-only on the bucket)
- [ ] Inclusion proofs in transfer receipts (`GET /transfers/{id}/proof`)

**Checkpoint:** A10 and A11 detected; verifier output names the exact tampered event.

### Phase 6 — Runtime detection and observability (week 10)
- [ ] Falco with custom rules: exec into ledger, shell spawn, write outside `/tmp`, unexpected outbound connect
- [ ] Kubernetes audit policy; logs shipped to Loki
- [ ] Metrics: `tally_verifier_last_success`, `tally_reconciliation_drift`, `tally_admission_denials_total`, detection latency
- [ ] Grafana "Security posture" dashboard; Alertmanager routes
- [ ] SLOs: verifier freshness (99% of runs < 5 min apart), 0 reconciliation drift

**Checkpoint:** A08 detected and alerted; every alert has a runbook section.

### Phase 7 — Attack suite and evidence (weeks 11–12)
- [ ] `attacks/Axx-*/` each with `run.sh`, `expected.yaml` (blocked | detected, max latency), cleanup
- [ ] Runner executes all attacks against an ephemeral kind environment, measures time-to-block/detect
- [ ] Nightly scheduled pipeline + on-demand job
- [ ] Evidence report (JSON + HTML): attack → control → result → latency → log/event links
- [ ] Control mapping: CIS Kubernetes Benchmark, SLSA, DORA ICT risk articles, PCI DSS 4.0 req. 6/10

**Checkpoint:** 12/12 attacks pass in CI; report published as a pipeline artifact.

### Phase 8 — Real AWS validation and polish (weeks 13–14)
- [ ] One-command AWS validation environment (small EKS or k3s on EC2), run the attack suite, tear down
- [ ] Cost report (target < €20 per validation run)
- [ ] README: 2-minute demo GIF, architecture diagram, evidence report screenshot, "what is not covered"
- [ ] `RUNBOOK.md` complete; incident drill for A10/A11
- [ ] Two blog posts (see §7)

**Checkpoint:** a stranger can clone, run `make up && make attack`, and see the evidence report.

## 5. Metrics to publish

| Metric | Target |
|---|---|
| Attacks blocked or detected | 12/12 |
| Median / max time-to-detect (detected class) | < 10 s / < 60 s |
| Pipeline duration (MR) | < 10 min |
| Critical CVEs in shipped image | 0 |
| Static credentials in repo or cluster | 0 |
| SLSA build level | L2, honestly stated (L3 needs a hardened, isolated builder) |
| Cost per AWS validation run | < €20 |

## 6. Demo (2 minutes)

1. Dashboard: all controls green, money-conservation check at 0 drift.
2. Push an MR with a leaked token and a CVE-laden dependency → pipeline red, with reasons.
3. Try to deploy an unsigned image → Kyverno denial.
4. `kubectl exec` into the ledger → Falco alert in Grafana within seconds.
5. As "DBA", edit a balance and then rewrite history → verifier flags the exact event; the S3-anchored signed head proves it.
6. Show the evidence report: 12/12.

## 7. Content

- Blog 1: "Threat-modeling a ledger by compromised actor, not by component"
- Blog 2: "Why a hash chain is not enough: anchoring signed tree heads outside the DBA's reach"
- Optional talk: 10-minute version of the demo for a local meetup

## 8. Risks

| Risk | Mitigation |
|---|---|
| Scope creep (more tools, more attacks) | 12 attacks fixed; new ideas go to `docs/IDEAS.md` |
| Full attack suite too heavy for shared CI runners | Nightly on a self-hosted runner; MR pipeline runs pipeline-level attacks only |
| LocalStack gaps (Object Lock, KMS sign) | Validate those two on real AWS early, in week 6 |
| Falco on kind/kernel issues | Fall back to Tetragon; decide by end of week 10 day 1 |
| Glue-only perception | Original parts are explicit: Merkle audit + verifier, Kyverno/Falco/OPA policies with tests, attack runner, evidence generator |

## 9. Interview questions this project answers

- How do you stop a compromised CI pipeline from shipping to production?
- What does SLSA provenance actually prove, and what does it not?
- How do you detect a DBA who edits data and then fixes up the audit trail?
- Why dynamic database credentials, and what breaks when they rotate?
- How do you prove to an auditor that a control works, not just that it is configured?
- What is out of scope in your threat model, and why?
