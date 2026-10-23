# ⚙️ Team 262 — Backend Service

Backend API for the Team 262 project.

## 💻 Tech Stack
* **Language:** Java 21
* **Framework:** Spring Boot
* **Build Tool:** Maven
* **Database:** MySQL 8.0 (Dockerized)

## 📖 Getting Started
Please read our [CONTRIBUTING.md](./CONTRIBUTING.md) guide for Git Flow rules and local MySQL database setup instructions.

## Run locally

Requires Java 21, Maven 3.6.3+ and Docker Compose. The application uses Spring Boot 3.5.

1. Copy `.env.example` to `.env` and replace the password placeholders locally.
2. Start MySQL: `docker compose up -d --wait`.
3. Set `MYSQL_DATABASE`, `MYSQL_USER` and `MYSQL_PASSWORD` in your shell or IDE
   environment to the same values as `.env`. Optionally set `MYSQL_HOST` and
   `MYSQL_PORT` (defaults: `localhost` and `3306`). Docker Compose reads `.env`
   automatically; Spring Boot does not.
4. Run `mvn spring-boot:run`. The HTTP server listens on port 8080.

### Run Full Stack with Docker Compose (MySQL + App)
To run both MySQL and the containerized Backend service together:
```bash
# 1. Ensure .env exists with required credentials
cp -n .env.example .env

# 2. Build and start both services
docker compose --profile app up -d --build

# 3. View backend logs
docker compose logs -f backend

# 4. Stop containers
docker compose --profile app down
```

The current Spring Security configuration permits requests; authentication is
deferred to a later task. Liquibase owns schema changes; add migrations to
`src/main/resources/db/changelog/db.changelog-master.yaml`. Hibernate validates
the schema without creating tables.

Run `mvn clean verify` to build the executable JAR and run all tests. Docker must
be running: Testcontainers starts isolated MySQL 8.0.44 databases for the
context/security smoke test and the JPA integration tests. The first run downloads
the images; no local `.env` or running Compose database is needed for tests.
Liquibase creates the schema and Hibernate uses `ddl-auto=validate`; H2 is not used.
The JAR is written to `target/movie-match-0.0.1-SNAPSHOT.jar`.

Run only the persistence tests with `mvn -Dtest=RoomPersistenceTests test`.
They cover initial/repeated migration, persisted preferences and statuses,
case-insensitive code lookup, lazy participant relationships, MySQL constraints,
database cascade deletion and identity methods with Hibernate proxies.

## Sprint 1 data model

The initial schema is in `001-rooms-participants.yaml`, included from the master
changelog. IDs are Java `Long` values stored as MySQL `BIGINT AUTO_INCREMENT`.
MySQL generates the IDs; JPA uses `GenerationType.IDENTITY`. Room foreign keys
also use `BIGINT`.

| Table | Fields and constraints |
| --- | --- |
| `rooms` | `id` PK; `code` unique, required, six characters; `status` required, default `LOBBY`; `content_type` required, default `MOVIES`; nullable `max_duration_minutes`; required `created_at` with a current-time default |
| `participants` | `id` PK; required `room_id` FK; required `name` (up to 20 characters); required `is_host`, default `false` |
| `room_genres` | Required `room_id` FK and numeric external-catalog `genre_id`; composite PK `(room_id, genre_id)` prevents duplicates within a room |

Room statuses are `LOBBY`, `VOTING`, `FINISHED`. Content types are `MOVIES` and
`TV_SERIES`. Both enums are stored as text and checked by MySQL. A null duration
and an empty genre set mean no restriction. Genre IDs are external identifiers,
so they do not reference a local `genres` table.

Room codes use letters and digits excluding `O`, `0`, `I`, `L`, `1`. `Room`
normalizes codes with `Locale.ROOT`; `findByCode` accepts either case. The database
also enforces case-insensitive uniqueness using an ASCII case-insensitive
collation. The code's unique index supports lookup; `created_at` is indexed for
future expiry cleanup. `participants.room_id` is indexed; the genre composite PK
already indexes `room_id`.

Participants are guests belonging to one room. Different guests can have the same
name. Account links (`user_id`) are deferred until a later migration introduces
users. The future join/session service must identify returning guests using a
validated participant credential, assign the creator as host and ensure only one
host per room. This schema stores the host flag without enforcing host count.
Entity IDs and invitation codes alone do not implement authentication.

Deleting a room cascades to its participants and genres in MySQL, including when
deleted using SQL. No JPA entity cascade or orphan removal is configured.
Authentication, code generation, the five-person room limit and scheduled
24-hour cleanup belong to later service tasks; this change only supplies their
data model. Streaming services, accounts and swipe history are outside this
migration.

Never commit `.env` files or real credentials. `.env.example` contains placeholders only.
