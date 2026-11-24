# 0004 — LocalStack first, real AWS for validation

**Status:** accepted
**Date:** 2026-11-09

## Context
KMS, S3 Object Lock and IAM are needed from phase 4. Running them on AWS during development costs money and needs credentials on a laptop.

## Decision
Develop against LocalStack. Validate on real AWS in V3 with a one-command environment that is torn down after each run.

## Consequences
Free, offline development. LocalStack may not enforce Object Lock exactly like AWS, so enforcement is only claimed after the AWS run.
