# Container compose command detection (supports both Podman and Docker)
# Can be overridden via CLI: make infra ENGINE=docker or make infra COMPOSE="docker compose"
ifndef COMPOSE
  ifeq ($(ENGINE),podman)
    COMPOSE := $(shell if podman compose version >/dev/null 2>&1; then echo "podman compose"; elif command -v podman-compose >/dev/null 2>&1; then echo "podman-compose"; else echo "podman compose"; fi)
  else ifeq ($(ENGINE),docker)
    COMPOSE := $(shell if docker compose version >/dev/null 2>&1; then echo "docker compose"; elif command -v docker-compose >/dev/null 2>&1; then echo "docker-compose"; else echo "docker compose"; fi)
  else
    COMPOSE := $(shell \
      if command -v podman >/dev/null 2>&1 && (podman compose version >/dev/null 2>&1 || command -v podman-compose >/dev/null 2>&1); then \
        if podman compose version >/dev/null 2>&1; then echo "podman compose"; else echo "podman-compose"; fi; \
      elif command -v docker >/dev/null 2>&1; then \
        if docker compose version >/dev/null 2>&1; then echo "docker compose"; elif command -v docker-compose >/dev/null 2>&1; then echo "docker-compose"; else echo "docker compose"; fi; \
      elif command -v podman >/dev/null 2>&1; then \
        echo "podman compose"; \
      else \
        echo "docker compose"; \
      fi)
  endif
endif

.PHONY: help up infra down

help:
	@echo "Available commands:"
	@echo "  make infra  - Start database/infrastructure via $(COMPOSE)"
	@echo "  make up     - Run Spring Boot application locally (dev profile)"
	@echo "  make down   - Stop database/infrastructure via $(COMPOSE)"
	@echo ""
	@echo "Current compose tool: $(COMPOSE)"
	@echo "To override: make <target> ENGINE=docker|podman or COMPOSE=\"docker compose\""

infra: 
	@echo "Starting infra using $(COMPOSE)..."
	@$(COMPOSE) up -d

up: 
	@./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

down: 
	@echo "Stopping infra using $(COMPOSE)..."
	@$(COMPOSE) down
