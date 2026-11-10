# 0007 — One locked chain head row for the event chain

**Status:** accepted
**Date:** 2026-11-10

## Context
Every transfer appends an event whose hash covers the previous hash. Two transfers reading the same
previous hash would fork the chain; a sequence with gaps would hide deleted events.

## Decision
A single row `chain_head(id = 1, seq, hash)` locked with `SELECT ... FOR UPDATE` inside the transfer
transaction, after the account locks. The event, the entries and the balances commit together.

## Consequences
No forks and no gaps, enforced by Postgres, with a few lines of code.
All transfers are serialized on that row: throughput is bounded by one commit at a time (measured in phase 2).
Lifting it later means batching events or sharding chains, both of which complicate the verifier.
