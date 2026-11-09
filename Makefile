SHELL := /bin/bash

TOOLS := java mvn go docker kubectl helm k3d terraform ansible gitleaks semgrep trivy syft cosign \
         kyverno argocd k6 pre-commit gitlab-ci-local awslocal

.PHONY: help check-tools ci hooks up deploy demo

help:
	@echo "Tally"
	@echo "  make check-tools  Verify local prerequisites"
	@echo "  make hooks        Install pre-commit hooks"
	@echo "  make ci           Run the GitLab pipeline locally"
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

up deploy demo:
	@echo "Not implemented yet."
	@exit 1
