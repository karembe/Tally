# 0002 — Kyverno over OPA Gatekeeper

**Status:** accepted
**Date:** 2026-11-09

## Context
Admission must verify cosign signatures and attestations, and enforce workload hardening.

## Decision
Kyverno. `verifyImages` checks signatures and attestations natively; policies are YAML and testable with the Kyverno CLI.

## Consequences
No Rego for cluster policy. Rego still appears for Terraform plans (Conftest, V2), so both styles are shown where each fits.
