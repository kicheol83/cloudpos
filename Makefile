COMPOSE := docker compose -f infra/compose/docker-compose.yml

.PHONY: up.core up.sales up.stock up.money up.analytics up.full down logs ps test build

up.core:      ; $(COMPOSE) --profile core up -d
up.sales:     ; $(COMPOSE) --profile sales up -d
up.stock:     ; $(COMPOSE) --profile stock up -d
up.money:     ; $(COMPOSE) --profile money up -d
up.analytics: ; $(COMPOSE) --profile analytics up -d
up.full:      ; $(COMPOSE) --profile full up -d

down: ; $(COMPOSE) --profile full down
ps:   ; $(COMPOSE) ps
logs: ; $(COMPOSE) logs -f $(SERVICE)

build: ; ./gradlew build
test:  ; ./gradlew test

run.identity: ; ./gradlew :services:identity:bootRun
run.gateway:  ; ./gradlew :services:gateway:bootRun
