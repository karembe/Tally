# 0009 — Ansible limited to node bootstrap and hardening

**Status:** accepted
**Date:** 2026-11-09

## Context
The AWS run uses k3s on EC2, so nodes need installing and hardening. A config-management tool with root SSH on every node is itself a powerful insider.

## Decision
Terraform owns cloud resources, Ansible owns the inside of the VMs, Argo CD owns the cluster. Ansible runs only from CI with short-lived SSH certificates, and a nightly check-mode run reports drift.

## Consequences
No overlap between tools. The Ansible operator becomes actor A13 in the threat model.
