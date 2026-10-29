ENV_FILE ?= $(shell if [ -f payment.env ]; then echo "--env-file payment.env"; elif [ -f .env ]; then echo "--env-file .env"; fi)
COMPOSE ?= $(shell if command -v docker >/dev/null 2>&1; then echo "docker compose"; else echo "podman compose"; fi)

.PHONY: help up infra down

infra: 
	@$(COMPOSE) $(ENV_FILE) up -d

up: infra
	@./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

down: 
	@$(COMPOSE) $(ENV_FILE) down
