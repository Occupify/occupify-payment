COMPOSE ?= podman compose

.PHONY: help up infra down

infra: 
	@$(COMPOSE) up -d

up: 
	@./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

down: 
	@$(COMPOSE) down
