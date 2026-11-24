SHELL := /bin/bash

CLUSTER ?= rd
DOCKER_CONTEXT := rancher-desktop
export DOCKER_CONTEXT

REGISTRY_IMAGE := registry:2.8.3@sha256:a3d8aaa63ed8681a604f1dea0aa03f100d5895b6a58ace528858a7b332415373

TOOLS := java mvn go docker kubectl helm k3d terraform ansible gitleaks semgrep trivy syft cosign \
         kyverno argocd k6 pre-commit gitlab-ci-local awslocal

.PHONY: help check-tools ci hooks db run test registry cluster cluster-down up down smoke deploy demo

help:
	@echo "Tally"
	@echo "  make check-tools  Verify local prerequisites"
	@echo "  make hooks        Install pre-commit hooks"
	@echo "  make ci           Run the GitLab pipeline locally"
	@echo "  make db           Start local Postgres (127.0.0.1:5433)"
	@echo "  make run          Run the app with demo data on :8181"
	@echo "  make test         Run all app tests (Testcontainers)"
	@echo "  make registry     Start the local image registry (localhost:5000)"
	@echo "  make cluster      Create the cluster (CLUSTER=rd|k3d, default rd)"
	@echo "  make cluster-down Delete the k3d cluster"
	@echo "  make up           Build, push and deploy everything (CLUSTER=rd|k3d)"
	@echo "  make down         Remove everything (CLUSTER=rd|k3d)"
	@echo "  make smoke        End-to-end check against the cluster"
	@echo "  make demo         Available after phase 5"

check-tools:
	@missing=0; for cmd in $(TOOLS); do \
		if command -v $$cmd >/dev/null 2>&1; then printf "  %-16s OK\n" $$cmd; \
		else printf "  %-16s MISSING\n" $$cmd; missing=1; fi; \
	done; exit $$missing

hooks:
	pre-commit install

ci:
	gitlab-ci-local --privileged

db:
	docker compose up -d --wait db

run: db
	./app/mvnw -f app spring-boot:run -Dspring-boot.run.profiles=dev

test:
	./app/mvnw -f app verify

registry:
	@docker inspect tally-registry >/dev/null 2>&1 || docker run -d --name tally-registry --restart unless-stopped \
		-p 127.0.0.1:5000:5000 -v tally-registry:/var/lib/registry $(REGISTRY_IMAGE) >/dev/null
	@docker start tally-registry >/dev/null
	@curl -fsS localhost:5000/v2/ >/dev/null && echo "registry up on localhost:5000"

cluster: registry
ifeq ($(CLUSTER),k3d)
	@docker network inspect tally-net >/dev/null 2>&1 || docker network create tally-net >/dev/null
	@docker network connect tally-net tally-registry 2>/dev/null || true
	@k3d cluster list tally >/dev/null 2>&1 || k3d cluster create --config deploy/k3d/cluster.yaml --wait
	@kubectl config use-context k3d-tally >/dev/null
else
	@kubectl config use-context rancher-desktop >/dev/null
endif
	@kubectl cluster-info | head -1

cluster-down:
	@k3d cluster delete tally 2>&1 | tail -1
	@docker network rm tally-net >/dev/null 2>&1 || true
	@kubectl config use-context rancher-desktop >/dev/null

up:
	@CLUSTER=$(CLUSTER) ./scripts/up.sh

down:
	@CLUSTER=$(CLUSTER) ./scripts/down.sh

smoke:
	@./scripts/smoke.sh

deploy demo:
	@echo "Not implemented yet."
	@exit 1
