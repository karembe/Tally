# Security

> Status: design placeholder. Controls are not claimed as implemented until their checkpoints are green.

## Planned security principles

- No secrets committed to Git.
- Least-privilege IAM.
- Non-root containers and read-only filesystems where possible.
- Kubernetes default-deny network policy.
- Dependency, image, IaC, and secret scanning in CI.
- Signed images before deployment.
- Tamper-evident event history with independent verification.

A threat model and concrete controls/evidence will be added as implementation progresses.
