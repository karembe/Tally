# 0010 — Spring Boot 4.1 instead of 3.5

**Status:** accepted
**Date:** 2026-11-09

## Context
The stack named Spring Boot 3.5.x. Its open-source support ended in mid-2026, and start.spring.io only generates 4.x.

## Decision
Spring Boot 4.1.2 on Java 21.

## Consequences
Security patches keep coming, which matters for a project about a secure delivery chain.
Starters are split per feature (`spring-boot-starter-webmvc`, `-flyway`, plus `-test` variants).
JdbcClient, Flyway and Testcontainers usage is unchanged.
