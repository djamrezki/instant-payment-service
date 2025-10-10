
# instant-payment-service

Instant Payment API built with **Spring Boot**, following **Hexagonal Architecture** principles.

This service enables internal CHF instant transfers, and is structured for future integration with external payment clearing systems.

---

## 🧭 Scope and Assumptions

This implementation targets **CHF instant payments** aligned with Alpian’s domestic operations.

- **Currency:** CHF only — no foreign exchange (FX) conversion.
- **Clearing:** Internal transfers between Alpian accounts only.  
  No external SIC, SEPA, or SWIFT connectivity in this version.
- **Limits:** No per-transaction or daily limits enforced for this technical assessment.
- **Identifiers:** Swiss IBANs (CH…) used for both debtor and creditor accounts.
- **Future readiness:** The architecture is structured to allow later integration with the SIC 5 clearing network via an external adapter.

---

## 🧩 Architecture Overview

The project follows **Hexagonal (Ports and Adapters)** architecture:

```
domain/            – core business logic (aggregates, entities, domain services)
application/       – use cases orchestrating domain logic
adapter/in/        – incoming interfaces (e.g., REST controllers)
adapter/out/       – outgoing interfaces (e.g., Kafka, persistence)
infrastructure/    – configuration and wiring (Spring Boot beans)
```

Event-driven behavior is achieved using **Spring Modulith** events and **Kafka** as the message broker.

---

## ⚙️ Local Development Setup

### Prerequisites

- **Java 21+**
- **Gradle** (or use the included wrapper `./gradlew`)
- **PostgreSQL 16+** (local or Docker)
- **Kafka** or **Redpanda** (for local testing)

### PostgreSQL Setup

If you already have PostgreSQL running locally:

```bash
createdb instantpay
# or
psql -U postgres -c "CREATE DATABASE instantpay;"
```

Alternatively, include PostgreSQL in Docker Compose using:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: instantpay
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
```

### Kafka Setup (Redpanda)

```bash
docker run -d --name redpanda   -p 9092:9092 -p 9644:9644   redpandadata/redpanda:latest   redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M     --node-id 0 --check=false     --kafka-addr PLAINTEXT://0.0.0.0:9092     --advertise-kafka-addr PLAINTEXT://localhost:9092
```

Topic (auto-created or manual):

```bash
docker exec -it redpanda rpk topic create payments.events -p 3 -r 1
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

The service starts on port **8080**.

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

You can run the unit and integration tests with:

```bash
./gradlew test
```

The project uses **Testcontainers** to start Kafka and PostgreSQL automatically for integration tests.

---

## 🧱 Build Configuration

The project uses Gradle with dependencies for:

- Spring Boot 3.5.x
- Spring Modulith
- Spring Data JPA (PostgreSQL)
- Kafka
- Testcontainers

---

## 🧠 Notes for Production Hardening

For a production-grade deployment, consider:

- Adding monitoring (Prometheus/Grafana or OpenTelemetry)
- Enabling centralized logging (ELK, Loki, or CloudWatch)
- Enforcing circuit breakers and retries around Kafka operations
- Running Kafka and PostgreSQL in HA mode
- Managing secrets via Vault or cloud provider service
- Implementing metrics and tracing for payment latency

---

## 📜 License

This project is provided for technical assessment purposes.
