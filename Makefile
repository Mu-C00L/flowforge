.PHONY: up down test core-check demo observability

up:
	docker compose up --build -d

down:
	docker compose down

test:
	mvn verify

core-check:
	./scripts/check-core.sh

demo:
	./scripts/run-demo.sh

observability:
	docker compose --profile observability up --build -d
