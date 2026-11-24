# 0006 — Plain SQL with JdbcClient instead of JPA

**Status:** accepted
**Date:** 2026-11-09

## Context
Correctness depends on exact locking, statement order and transaction boundaries.

## Decision
Spring `JdbcClient` with explicit SQL. No ORM.

## Consequences
Every `SELECT ... FOR UPDATE` and insert is visible and reviewable. More mapping code to write.
