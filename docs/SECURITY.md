# Security

Controls are listed as `planned` until their attack is demonstrated in the evidence report.

## Goal

No single compromised actor can change money or code without being **blocked** (the change never lands)
or **detected** (it lands, and the system names it within seconds).

## Assets

| Asset | Why it matters |
|---|---|
| Account balances | the money |
| Event history (`ledger_events`) | the source of truth balances are recomputed from |
| Checkpoint signing key (KMS) | whoever signs heads can bless a forged history |
| Anchored signed heads (S3 Object Lock) | the proof that lives outside the DBA's reach |
| Container images and their signatures | whatever runs in the cluster |
| Pipeline identity (OIDC) | what CI is allowed to sign and deploy |

## Threat model by actor

| # | Compromised actor | Attack | Control | Outcome | Phase |
|---|---|---|---|---|---|
| A01 | Developer | commits a secret | gitleaks in pre-commit and CI | blocked | 3 |
| A02 | Developer | pushes backdoored code | Semgrep rules, protected `main`, review | blocked | V2 |
| A03 | Dependency | critical CVE | Trivy fs and image scan, expiring allow-list | blocked | 3 |
| A04 | Developer (IaC) | opens `0.0.0.0/0` or a public bucket | Checkov, Conftest on plan JSON | blocked | V2 |
| A05 | CI / registry | deploys an unsigned image | Kyverno `verifyImages` (cosign) | blocked | 5 |
| A06 | CI | signed image built outside `main` | Kyverno checks SLSA provenance | blocked | V2 |
| A07 | Cluster admin | privileged or hostPath pod | Pod Security `restricted`, Kyverno | blocked | 5 |
| A08 | Cluster admin | `kubectl exec` into the ledger | Falco, audit logs off-cluster | detected | V2 |
| A09 | Compromised pod | exfiltrates to the internet | Cilium default-deny egress, Hubble | blocked + detected | V2 |
| A10 | DBA | edits a balance | verifier recomputes balances from events | detected | 4 |
| A11 | DBA | rewrites history and the hash chain | signed heads anchored in S3 Object Lock | detected | 4 |
| A12 | Anyone | uses leaked DB credentials | Vault dynamic credentials, 1 h TTL | blocked after TTL | V2 |
| A13 | Config-management operator | SSH change on a node | CI-only Ansible, short-lived SSH certs, drift job | blocked + detected | V3 |

## Trust boundaries

Each actor holds one kind of power. The controls make sure that power ends where the next actor's begins.

- **Developer → CI.** A developer controls source code, not what gets built. CI re-runs every check, so skipping a local hook gains nothing.
- **CI → cluster.** CI can sign images, but only for builds of `main` by the trusted builder. The cluster checks the signature and provenance itself, at admission.
- **Cluster admin → workload.** Admins can change cluster objects, but admission policy still refuses unsigned or privileged workloads, and runtime detection reports interactive access.
- **App → database.** The app's database role can insert events but cannot update or delete them.
- **DBA → history.** A DBA can rewrite any table, but cannot rewrite heads already signed by KMS and locked in S3. The verifier trusts only those.
- **Verifier → everything.** The verifier is written in a different language, has its own read-only database role and its own read-only IAM role, and shares no code with the app.
- **Cloud admin → anchors.** Object Lock in compliance mode cannot be shortened or deleted, even by the account admin, until retention expires.

## Out of scope

- Compromise of the Sigstore root or the AWS account root
- Kernel or hypervisor exploits
- Collusion between a DBA, a cloud admin who controls the signing key, and CI
- Denial of service

## Reporting

Open a confidential issue on the GitLab project.
