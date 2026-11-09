# 0005 — Rancher Desktop and k3d instead of kind

**Status:** accepted
**Date:** 2026-11-09

## Context
The project needs a local cluster for daily work and throwaway clusters for the attack suite and Cilium.

## Decision
Rancher Desktop (k3s) for daily work, k3d (k3s in Docker) for throwaway clusters, k3s on EC2 for the AWS run.

## Consequences
The same Kubernetes distribution everywhere. Rancher Desktop needs KVM. Cilium runs only on k3d, where flannel can be disabled.
