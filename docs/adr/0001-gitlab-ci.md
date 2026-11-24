# 0001 — GitLab CI as the pipeline

**Status:** accepted
**Date:** 2026-11-09

## Context
The pipeline is a security boundary. It needs built-in OIDC tokens for keyless signing and cloud access, protected branches, and a registry next to the code. Banks and consultancies in France widely run GitLab.

## Decision
GitLab is the primary repository and CI. GitHub is a push mirror for visibility. Locally, `.gitlab-ci.yml` runs with gitlab-ci-local.

## Consequences
One pipeline definition for local and remote runs. GitHub shows code, not CI history. Pull mirroring is paid, so the mirror is push-only.
