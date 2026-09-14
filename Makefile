SHELL := /bin/bash

.PHONY: help check-tools up deploy demo

help:
	@echo "Tally"
	@echo "  make check-tools  Verify local prerequisites"
	@echo "  make up           Available after the walking skeleton is implemented"
	@echo "  make deploy       Available after Kubernetes/Helm setup"
	@echo "  make demo         Available after the demo harness exists"

check-tools:
	@set -e; \
	for cmd in java mvn docker kind kubectl helm terraform ansible git; do \
		printf "%-12s" "$$cmd"; \
		command -v $$cmd >/dev/null 2>&1 && echo "OK" || { echo "MISSING"; exit 1; }; \
	done

up:
	@echo "Not implemented yet — Phase 1."
	@exit 1

deploy:
	@echo "Not implemented yet — Phase 1."
	@exit 1

demo:
	@echo "Not implemented yet — later checkpoint."
	@exit 1
