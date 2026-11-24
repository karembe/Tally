# 0003 — Verifier in Go, separate from the Java app

**Status:** accepted
**Date:** 2026-11-09

## Context
The verifier is the control against a compromised app or DBA. If it shares code with the app, a bug in that code hides itself.

## Decision
The verifier and the `tally` CLI are a separate Go module with their own database role and IAM role. Java and Go share only golden test vectors for the canonical encoding.

## Consequences
Two implementations of the encoding and Merkle tree must agree, which the vectors enforce. One static binary for CLI and CronJob. Two languages to maintain.
