# instant-payment-service

Instant Payment API built with **Spring Boot**, following **Hexagonal Architecture** principles.

This service enables internal CHF instant transfers and is structured for future integration with external payment clearing systems.

---

## 🧭 Scope and Assumptions

This implementation targets **CHF instant payments** aligned with Alpian’s domestic operations.

- **Currency:** CHF only — no FX conversion.  
- **Clearing:** Internal transfers between Alpian accounts only (no SIC, SEPA, or SWIFT).  
- **Limits:** No per-transaction or daily limits enforced.  
- **Identifiers:** Swiss IBANs (CH…) for debtor and creditor accounts.  
- **Future readiness:** Architecture structured for later integration with the **SIC 5** clearing network via an external adapter.

---

## 🧩 Architecture Overview

The project follows **Hexagonal (Ports and Adapters)** architecture:

```
domain/            – core business logic and use cases orchestrating domain logic (aggregates, entities, domain services)
adapter/in/        – incoming interfaces (REST controllers)
adapter/out/       – outgoing interfaces (Kafka, persistence)
config/            – configuration and wiring (Spring Boot beans)
ops/               – operational safeguards and housekeeping tasks (e.g., nightly ledger reconciliation to detect inconsistencies between payments, transactions, and event outbox)
```

Event-driven behavior is achieved using **Spring Modulith** and **Kafka**.

---

### 📦 Core Features

- **Instant Transfer API (`/api/payments`)**  
  Processes real-time CHF transfers between internal accounts.
   - Balance validation and transactional consistency via `PaymentService`.
   - Idempotent behavior preventing double-spend through database locking.
   - Full traceability with a `TraceIdFilter` adding correlation IDs to logs.

- **Event-driven architecture (Spring Modulith + Kafka)**
   - Internal domain events (e.g., `PaymentCreatedEvent`, `PaymentCompletedEvent`, `PaymentFailedEvent`)  
     are published by the domain layer and relayed to Kafka via the `PaymentKafkaRelay`.
   - JSON serialization handled manually with Jackson and a `KafkaTemplate<String, byte[]>`,  
     ensuring schema independence and safe cross-system integration.

- **Resilient Error Handling**
   - Centralized through `GlobalExceptionHandler` for consistent API responses.
   - Differentiates client vs. server errors (HTTP 4xx vs. 5xx).
   - Includes contextual trace IDs for easier correlation in logs.

- **Operational Guardrails (`ops/`)**
   - `LedgerReconciler` runs nightly (ShedLock-protected) to validate:
      - Completed payments have exactly two balanced ledger entries.
      - Failed payments have none.
      - Outbox events aren’t stuck beyond threshold.

- **Observability Built-In**
   - `TraceIdFilter` injects `X-Trace-Id` header into each request for distributed tracing.
   - Spring Boot Actuator exposes readiness/liveness probes for container orchestration.
   - Structured logging and consistent trace correlation across HTTP and Kafka layers.


---

## 📖 API Reference

- OpenAPI spec: [`docs/openapi/instant-payment-api.yaml`](docs/openapi/instant-payment-api.yaml)
- Base URL (local): `http://localhost:8080`
- Key endpoints:
    - `POST /api/payments` — create an instant payment (idempotent via `Idempotency-Key`)
    - `GET /api/payments/health` — service health

---

### 🧩 Special Note — Event Publication with Spring Modulith & Kafka

This project uses **Spring Modulith** for internal domain event publication and **Kafka** as an external event bus.

To maintain **strong modular boundaries** and **schema independence**, the outbound Kafka adapter (`PaymentKafkaRelay`) listens to Modulith domain events and transforms them into JSON payloads using **Jackson**.  
Events are sent through a typed `KafkaTemplate<String, byte[]>` configured with the `ByteArraySerializer`.

```java
kafka.send(topic, id.toString(), objectMapper.writeValueAsBytes(payload)).join();
```

This design offers several advantages:

- ✅ **Decoupling:** Domain models remain internal; only well-defined JSON payloads leave the boundary.
- ✅ **Type safety:** `KafkaTemplate<String, byte[]>` ensures compile-time serializer alignment.
- ✅ **Portability:** JSON bytes are language-neutral and suitable for non-Java consumers.
- ✅ **Resilience:** Modulith retries incomplete event publications on restart, guaranteeing eventual delivery.
- ✅ **Schema control:** Using Jackson manually avoids implicit classpath coupling and enables future schema versioning.

> 💡 *In short: Modulith handles reliable internal publication, while `PaymentKafkaRelay` ensures external interoperability and schema stability.*

---

## ⚙️ Local Development Setup

### Prerequisites

- **Java 21+**
- **Gradle** (or use the included wrapper `./gradlew`)
- **Docker Compose** (for local infra: Redpanda + optional PostgreSQL)

---

### 🧰 One-Command Infrastructure (Kafka + DB Seeding)

To bring up local infrastructure and seed demo accounts:

```bash
./gradlew infraUp -PuseLocalPostgres=true
```

This will:

1. Start **Redpanda** (Kafka-compatible broker)  
2. Create the Kafka topic `payments.events`  
3. Seed the database with two demo accounts:

   | IBAN (Debtor)                | Initial Balance |
   |------------------------------|----------------:|
   | `CH9300762011623852957`      | 100,000.00 CHF  |
   | `CH2801234000123456789`      | 0.00 CHF        |

If you don’t have a local Postgres, omit the flag to use Docker’s database profile:
```bash
./gradlew infraUp
```

To tear everything down:
```bash
./gradlew infraDown
```

---

### 🐾 Demo: End-to-End Instant Payment

Once the app is running on port 8080 (`./gradlew bootRun`), execute:

```bash
./gradlew demo
```

The **demo task** will:

1. Check the app’s health endpoint `/api/payments/health`.  
2. Send a real **payment POST** request between the seeded demo accounts.  
3. Print the API response and a tip to watch the event stream.

Example output:
```
Health: 200 OK
POST /api/payments -> 201
{ "paymentId": "c43f..." }

Tip: tail events -> ./gradlew consumeEvents
```

To tail Kafka events in real time:
```bash
./gradlew consumeEvents
```

---

## 🚀 Running the Application

### Using Gradle

```bash
./gradlew bootRun
```

### Using JAR

```bash
./gradlew clean build
java -jar build/libs/instant-payment-service-*.jar
```

The service starts on **http://localhost:8080**.

---

## ✅ Environment Variables

You can override defaults via environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/instantpay
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

---

## 🧪 Testing

Run unit and integration tests:

```bash
./gradlew test
```

Tests automatically spin up PostgreSQL and Kafka using **Testcontainers**.

---

## 🐳 Containerization

### Build the Docker image

Run this in the project root (where the `Dockerfile` is located):

```bash
docker build -t instant-payment-service:latest .
```

This uses a **multi-stage Docker build**: Gradle compiles the project, and a slim JRE image runs it.

---

### Run the container (with default PostgreSQL user)

If PostgreSQL is running locally on port 5432:

```bash
docker run --rm -p 8080:8080   -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/postgres   -e SPRING_DATASOURCE_USERNAME=postgres   -e SPRING_DATASOURCE_PASSWORD=postgres   -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092   instant-payment-service:latest
```

**Notes:**
- The app will start on [http://localhost:8080](http://localhost:8080).
- `host.docker.internal` allows the container to reach your local DB/Kafka.
- On Linux, add `--add-host=host.docker.internal:host-gateway` if needed.

Check the health endpoint:

```bash
curl http://localhost:8080/api/payments/health
```

Expected output:

```
OK
```

---

### Run PostgreSQL in Docker (if you don’t have it locally)

```bash
docker run --name postgres   -e POSTGRES_PASSWORD=postgres   -p 5432:5432 -d postgres:16-alpine
```

Then rerun the app container with the same environment variables as above.

---

## 🧠 Notes for Production Hardening

For a production-grade deployment, consider:

- Monitoring (Prometheus / Grafana or OpenTelemetry)
- Centralized logging (ELK, Loki, or CloudWatch)
- Circuit breakers and retries around Kafka operations
- HA setup for Kafka and PostgreSQL
- Secret management (Vault or cloud provider)
- Metrics and tracing for payment latency

---

## 📜 License

This project is provided for technical assessment purposes.
