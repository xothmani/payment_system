# Payment System — Claude Code Rules

## Project Overview

A payment system built with 4 Spring Boot microservices. Each service is a separate Maven module. Services communicate via REST only — no direct gateway calls from the Subscription Service.

## Architecture

```
Subscription Management Service (port 8081)
        |
        v
Payment Router Service (port 8082)
        /                    \
AN_API (port 8083)     P_API (port 8084)
        |                    |
 Authorize.net             Paddle
```

## Stack

- Java 17
- Spring Boot 3
- Maven (multi-module project)
- PostgreSQL (persistent storage)
- Redis (caching, locking, idempotency)
- REST APIs between services (RestClient / WebClient)

## Service Responsibilities

| Service | Port | Responsibility |
|---|---|---|
| subscription-service | 8081 | Full subscription lifecycle. Entry point for all payment flows. |
| payment-router | 8082 | Receives confirmed country, routes to AN_API or P_API. |
| authorize-net-service | 8083 | All Authorize.net payment operations (6 features). US users only. |
| paddle-service | 8084 | All Paddle payment operations. Non-US users only. |

## Routing Rule

- Country = US → Authorize.net (AN_API)
- Country = anything else → Paddle (P_API)

---

## Redis Architecture

Dependency: `spring-boot-starter-data-redis` in all services that use Redis.
Connection config via `application.yml` — never hardcode host/port.

### Key Schema

| Key | Service | TTL | Purpose |
|---|---|---|---|
| `tokens:{userId}` | subscription-service | none | AI token balance. Use atomic DECRBY. |
| `idem:{transactionId}` | authorize-net-service, paddle-service | 24h | Idempotency — prevent double charges. |
| `plan:{planId}` | subscription-service | 1h | Cached plan definitions. Invalidate on update. |
| `lock:charge:{userId}` | subscription-service | 60s | Distributed lock for freemium auto-charge. |

### Rules

- Token deduction: always use `redisTemplate.opsForValue().decrement(key, amount)` — never read-then-write.
- Idempotency check: before ANY charge, check Redis for the key. If exists, return cached result. Do not call gateway.
- Distributed lock: use `SET NX EX` pattern (via `redisTemplate.opsForValue().setIfAbsent(key, value, duration)`). If lock not acquired, skip — another instance is processing.
- Plan cache: write-through on admin update. Read from Redis first, fall back to DB on miss, then repopulate Redis.

---

## Threading Architecture

### Async Webhook Processor

- Used in: `authorize-net-service`, `paddle-service`
- Pattern: webhook endpoint returns `200 OK` immediately, hands event to `@Async` worker
- Bean: `webhookTaskExecutor` — `ThreadPoolTaskExecutor`, core=5, max=10, queue=50
- Annotate handler methods with `@Async("webhookTaskExecutor")`
- Never block on DB writes or downstream REST calls inside the webhook HTTP thread

### Freemium Trial Scheduler

- Used in: `subscription-service`
- Pattern: `@Scheduled(cron = "0 0 8 * * *")` — runs daily at 08:00
- Bean: `trialSchedulerTaskScheduler` — `ThreadPoolTaskScheduler`, pool=2
- Flow: find all subscriptions expiring today → acquire Redis lock → call Payment Router → release lock
- If Redis lock not acquired for a user → skip (another instance handling it)

### Concurrent Token Deduction

- Used in: `subscription-service`
- Pattern: Redis atomic `DECRBY` handles concurrency — no Java-level locking needed
- After deduction: async DB write via `@Async("tokenLedgerExecutor")` — eventual consistency is acceptable
- Bean: `tokenLedgerExecutor` — `ThreadPoolTaskExecutor`, core=3, max=8, queue=100

### Thread Pool Config

Define all thread pools in a single `AsyncConfig.java` per service. Never use the default Spring async executor for payment operations — always name the executor explicitly.

```java
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("webhookTaskExecutor")
    public TaskExecutor webhookTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("webhook-");
        exec.initialize();
        return exec;
    }

    @Bean("tokenLedgerExecutor")
    public TaskExecutor tokenLedgerExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(3);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("token-ledger-");
        exec.initialize();
        return exec;
    }

    @Bean("trialSchedulerTaskScheduler")
    public TaskScheduler trialScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("trial-scheduler-");
        return scheduler;
    }
}
```

---

## Coding Rules

1. One service at a time — finish all features and tests before starting the next.
2. No gateway calls directly from `subscription-service` — always go through `payment-router`.
3. All inter-service calls use `RestClient` (Spring Boot 3) — no Feign, no WebFlux unless discussed.
4. Every charge request must carry an idempotency key — generated by the caller, checked in Redis before processing.
5. Never store raw card data — use Authorize.net CIM tokens only.
6. All secrets (API keys, DB password, Redis password) in `.env` / environment variables — never in source code.
7. Every `@Async` method must have a try/catch — unhandled exceptions in async threads are silent.
8. Redis operations must have a fallback — if Redis is down, fall back to DB (except distributed lock: if lock unavailable, abort the charge attempt).

---

## Environment Variables

```
# Authorize.net
AUTHNET_API_LOGIN_ID=
AUTHNET_TRANSACTION_KEY=
AUTHNET_ENV=sandbox   # or production

# Paddle
PADDLE_API_KEY=
PADDLE_WEBHOOK_SECRET=

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=payment_db
DB_USER=
DB_PASSWORD=

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Service URLs
PAYMENT_ROUTER_URL=http://localhost:8082
AUTHORIZE_NET_SERVICE_URL=http://localhost:8083
PADDLE_SERVICE_URL=http://localhost:8084
```

---

## Maven Module Structure

```
payment-system/
├── CLAUDE.md
├── .env
├── docker-compose.yml
├── pom.xml                        (parent pom)
├── subscription-service/
│   └── pom.xml
├── payment-router/
│   └── pom.xml
├── authorize-net-service/
│   └── pom.xml
└── paddle-service/
    └── pom.xml
```

---

## docker-compose.yml Services

When generating docker-compose, include:
- `postgres` — port 5432
- `redis` — port 6379
- `subscription-service` — port 8081, depends on postgres + redis
- `payment-router` — port 8082
- `authorize-net-service` — port 8083, depends on redis
- `paddle-service` — port 8084, depends on redis

---

*Prepared by Rayen Othmani — April 2026*
