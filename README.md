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

Spring Security protects requests using its default configuration until application
authentication is implemented. Liquibase owns schema changes; add migrations to
`src/main/resources/db/changelog/db.changelog-master.yaml`. Hibernate validates
the schema without creating tables.

Run `mvn clean verify` to build the executable JAR and run the context/security
smoke test using an isolated H2 database in MySQL compatibility mode. This test
does not replace integration testing against MySQL. The JAR is written to
`target/movie-match-0.0.1-SNAPSHOT.jar`.

Never commit `.env` files or real credentials. `.env.example` contains placeholders only.
