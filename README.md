### Менеджер Сигнализаций (MVP)

Промышленный модульный монолит для on‑prem Alarm Management по стандартам IEC 62443, ISA‑18.2 и EEMUA‑191.

- Kotlin + Spring Boot 3 (Java 21)
- Gradle multi‑module
- PostgreSQL 16 + TimescaleDB
- Redis, Keycloak (OIDC/MFA), Camunda 7, опционально ClickHouse
- OPA/Rego авторизация, RLS в Postgres
- API Gateway с REST + SSE/WebSocket
- React + TypeScript Web UI

#### Быстрый старт

1) Предусловия: Docker, Docker Compose.

2) Запуск стека:

```bash
docker compose up -d --build
```

3) Инициализация демо‑данных и смоук‑тест:

```bash
docker compose exec api-gateway java -jar /app/app.jar --init-demo=true
```

4) Открыть UI: `http://localhost:5173`

- Keycloak: `http://localhost:8444` (realm: `alarm`, демо‑пользователи в `infra/keycloak/realm-alarm.json`).

Подробнее: `docs/ARCHITECTURE.md`, `docs/OPERATIONS.md`.
