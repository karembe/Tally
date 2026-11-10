SHELL := /bin/bash

TOOLS := java mvn go docker kubectl helm k3d terraform ansible gitleaks semgrep trivy syft cosign \
         kyverno argocd k6 pre-commit gitlab-ci-local awslocal

.PHONY: help check-tools ci hooks db run test up deploy demo

help:
	@echo "Tally"
	@echo "  make check-tools  Verify local prerequisites"
	@echo "  make hooks        Install pre-commit hooks"
	@echo "  make ci           Run the GitLab pipeline locally"
	@echo "  make db           Start local Postgres (127.0.0.1:5433)"
	@echo "  make run          Run the app with demo data on :8181"
	@echo "  make test         Run all app tests (Testcontainers)"
	@echo "  make up           Available after phase 2"
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

up deploy demo:
	@echo "Not implemented yet."
	@exit 1
