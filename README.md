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

The current Spring Security filter permits requests; room lobby authentication
uses `X-Participant-Token` through `@CurrentParticipant`. Liquibase owns schema changes; add migrations to
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

## Swagger and frontend API

After starting the backend, open [Swagger UI](http://localhost:8080/swagger-ui/index.html).
The generated contracts are available as [OpenAPI JSON](http://localhost:8080/v3/api-docs)
and [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml).

All three Sprint 1 operations include Russian descriptions, request/response
examples, DTO constraints and operation-specific HTTP errors. For GET lobby,
click **Authorize** and paste the participant token without `Bearer`.
Create and join do not require authorization. Their token fields differ:
`participant.token` for create, `currentParticipant.token` for join.

See [the frontend guide and error table](docs/frontend-api.md) for the complete
create/join/lobby workflow and handling of business versus HTTP/JSON errors.
Run `mvn -Dtest=RoomOpenApiIntegrationTest test` to verify generated documentation,
Swagger UI and the real HTTP workflow against MySQL.

## Room creation and lobby

`POST /rooms` accepts the existing `CreateRoomRequest`: `displayName` (3–20
characters, not blank) and optional `preferences`. It returns `201 Created`, a
`Location: /rooms/{code}` header and `CreateRoomResponse`. The host and its token
remain in the `participant` field. Missing or null preferences retain the model's
`MOVIES` default, empty genres and no duration limit. A supplied preferences
object requires `contentType`; missing or null `genreIds` become an empty set.
`maxDurationMinutes` accepts any integer from 30 through 150, or null.

Room creation, host persistence and token issuance commit together. Only a room
code unique constraint violation triggers a retry, with a fresh transaction for
each of at most five attempts. Exhaustion returns `503 Service Unavailable` so the
caller can retry; other persistence failures propagate without collision retries.

`GET /rooms/{code}` requires `X-Participant-Token` for a participant in that room.
It returns `RoomDetailsResponse` with current status, preferences, participant
count, configured `maxParticipants` and participants ordered by ID. The shared
participant list contains only ID, display name and host flag. Tokens are never
included. The temporary limit is **5**, configurable with `ROOM_MAX_PARTICIPANTS`
(`room.max-participants`). The current join implementation checks a fixed limit of 5;
changing that configuration alone does not change join capacity.

Validation failures return `400`; missing, unknown or expired tokens return
`401 INVALID_TOKEN`; a valid token belonging to another room returns
`403 WRONG_ROOM`; an unknown room with a valid token returns `404 ROOM_NOT_FOUND`.
`ForbiddenException` now maps to `403` instead of the previous incorrect `401`.
`POST /rooms/{code}/join` creates a participant with `host=false` and returns
`201` with `currentParticipant.token`. It requires a room in `LOBBY`, fewer than
5 current participants, and a free display name (case-insensitive). Conflicts
return `409 ROOM_NOT_JOINABLE`, `ROOM_FULL`, or `NAME_TAKEN`. Joining does not
restore an earlier participant session. Random film selection remains outside Sprint 1.

DTO PRs #11/#13 and token resolver/service PR #12 are already merged into the
`develop` base (`ed7fe66`); there are no remaining dependencies on those PRs.

Run the API, transaction and authorization checks on MySQL with
`mvn -Dtest=RoomControllerIntegrationTest test` (Docker required).
Run the service checks without Docker with `mvn -Dtest=RoomServiceTest test`.
The standalone HTTP checks run with `mvn -Dtest=RoomControllerTest test` and cover
the existing DTO contract, request validation and participant token resolution.

`RoomMapper` uses MapStruct 1.6.3 with the Spring component model. Maven generates
its implementation during compilation; generated sources stay under `target/`.
The annotation processor configuration includes Lombok and `lombok-mapstruct-binding`.
Room construction retains model defaults, and response genres and participant
lists remain immutable snapshots. Mapper regression tests run with
`mvn -Dtest=RoomMapperTest test`.

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
users. Room creation assigns the creator as host. The future join service must
identify returning guests using a validated participant token and preserve the
single host. This schema stores the host flag without enforcing host count.
Entity IDs and invitation codes alone do not implement authentication.

Deleting a room cascades to its participants and genres in MySQL, including when
deleted using SQL. No JPA entity cascade or orphan removal is configured.
The five-person room limit enforcement and scheduled 24-hour cleanup belong to
later service tasks. Streaming services, accounts and swipe history are outside
this migration.

Never commit `.env` files or real credentials. `.env.example` contains placeholders only.
