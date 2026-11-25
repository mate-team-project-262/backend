# 🎬 W2W — What to Watch

<!-- =========================================================================
     SECTION 1: UNIFIED PROJECT OVERVIEW (KEEP IDENTICAL ACROSS ALL 4 REPOS)
     ========================================================================= -->

[![Infra CI](https://img.shields.io/github/actions/workflow/status/mate-team-project-262/infra/infra-ci.yml?branch=main&label=Infra%20CI&logo=githubactions)](https://github.com/mate-team-project-262/infra/actions/workflows/infra-ci.yml)
[![Infra CD](https://img.shields.io/github/actions/workflow/status/mate-team-project-262/infra/infra-cd.yml?branch=main&label=Infra%20CD&logo=amazonaws)](https://github.com/mate-team-project-262/infra/actions/workflows/infra-cd.yml)
[![Backend CI](https://img.shields.io/github/actions/workflow/status/mate-team-project-262/backend/ci.yml?branch=main&label=Backend%20CI&logo=githubactions)](https://github.com/mate-team-project-262/backend/actions)
[![Frontend CI](https://img.shields.io/github/actions/workflow/status/mate-team-project-262/frontend/ci.yml?branch=main&label=Frontend%20CI&logo=githubactions)](https://github.com/mate-team-project-262/frontend/actions)

**Stop arguing about what to watch.** W2W (What to Watch) is a real-time collaborative movie picker: create a room, invite friends via link, 6-character code, or QR code, swipe through the same 20 curated TMDB movies, and let the interactive match roulette pick the film your whole group agrees on — no account required.

## 🌐 Live Demo & Endpoints

* **Live Application:** [https://what2watch.duckdns.org](https://what2watch.duckdns.org)
* **OpenAPI / Swagger UI:** [https://what2watch.duckdns.org/swagger-ui/index.html](https://what2watch.duckdns.org/swagger-ui/index.html)
* **Edge Gateway Health:** [https://what2watch.duckdns.org/gateway-health](https://what2watch.duckdns.org/gateway-health)

> **Availability note:** To optimize AWS Free Tier costs, an automated FinOps schedule pauses the cloud servers overnight (**22:00–08:00 EET, Kyiv time**). If the live demo is sleeping, see the screenshots below.

![W2W Landing Page](https://raw.githubusercontent.com/mate-team-project-262/frontend/main/docs/screenshots/landing.jpg)

| Swipe Session | Match Results |
| :---: | :---: |
| ![W2W Swipe Session](https://raw.githubusercontent.com/mate-team-project-262/frontend/main/docs/screenshots/swipe.jpg) | ![W2W Match Results](https://raw.githubusercontent.com/mate-team-project-262/frontend/main/docs/screenshots/results.jpg) |

### Try it in 60 seconds
1. Open the Live Application and click **Create a room**, then choose content type, genres, and duration.
2. Open the invite link in a second browser or incognito window and join under another name.
3. The host clicks **Start session**; everyone swipes through the same 20 movies (like or skip).
4. When everyone finishes, view the matched winner — or spin the fair roulette to break a tie.

## 🧩 Repository Ecosystem

The platform is organized into four repositories connected by a unified GitHub Actions + GHCR + AWS EC2 delivery pipeline:

```text
Browser (HTTPS) ─▶ Nginx Gateway (TLS, CSP/HSTS, Rate Limiting)
                     ├─ /       ─▶ React SPA (frontend)
                     └─ /api/*  ─▶ Spring Boot API (backend) ─▶ AWS RDS MySQL + TMDB / Gmail SMTP
```

| Repository                                                            | Responsibility | Core Tech Stack |
|:----------------------------------------------------------------------| :--- | :--- |
| **[`frontend`](https://github.com/mate-team-project-262/frontend)**   | SPA UI: room lobby, swipe deck, profiles, match roulette | React 19, TypeScript, TanStack Query, Vite 8, Nginx |
| **[`backend`](https://github.com/mate-team-project-262/backend)**     | REST API, room state machine, voting engine, TMDB & SMTP | Java 21, Spring Boot 3.5, Liquibase, MySQL 8.0, Testcontainers |
| **[`infra`](https://github.com/mate-team-project-262/infra)**         | AWS Terraform IaC, Docker Compose, SSL Gateway, Observability | Terraform, AWS (EC2/RDS/VPC/S3), Nginx, Prometheus, Grafana |
| **[`analytics`](https://github.com/mate-team-project-262/analytics)** | Data cleaning, EDA, survey & product metrics visualization | Python 3, Jupyter Notebook, Pandas, NumPy, Matplotlib, Seaborn |

## 🚀 Run the Entire Stack Locally (3 Steps)

Spin up the full platform locally (Nginx Gateway, React Frontend, Spring Boot Backend, and MySQL 8.0) via the `infra` repository — no external TMDB API key required (`TITLE_SOURCE=stub` is enabled by default):

```bash
git clone https://github.com/mate-team-project-262/infra.git && cd infra/compose
cp .env.example .env
docker compose up -d
```

* **Local App:** `http://localhost` · **Swagger UI:** `http://localhost/swagger-ui/index.html` · **Gateway Health:** `http://localhost/gateway-health`

---

<!-- =========================================================================
     SECTION 2: BACKEND REPOSITORY DETAILS
     ========================================================================= -->

## ⚙️ Backend Repository Details

The REST API powering W2W: rooms, swipe voting, match results, user accounts, and analytics. In production, the frontend communicates with the API through the Nginx gateway under `/api`.

### Features

- **Guest Rooms:** Support for up to 5 participants per room (default, configurable via `ROOM_MAX_PARTICIPANTS`), joined via a room code using a 24-hour participant token.
- **Curated Movie Decks:** 20 movies or series per game fetched from TMDB, served in the exact same order to all participants. Includes an offline stub mode that works without a TMDB API key.
- **Safe Game Start:** Restricted exclusively to the room host, with built-in safeguards preventing double-clicks or TMDB errors from corrupting the room state.
- **Swipe Voting & Match Engine:** Real-time group progress tracking and result calculation with tie-breaking and "no match" handling.
- **User Accounts:** Sign up and sign in by display name using CSRF-protected session cookies. Users can update their name, email, and password, as well as link a guest session to their account.
- **Session History & "Movie Identity":** Tracks favorite and least-liked genres, decades, and actors. Finished games are archived so history persists even after a room is deleted.
- **Privacy-Friendly Analytics:** Single `POST /event` endpoint (plus server-side event logging) that stores anonymized metrics for 400 days—no names, emails, or room codes are retained.
- **Standardized Error Handling:** Consistent error response structure (`code`, `message`, `fieldErrors`) across all endpoints, fully documented in Swagger.

### Tech Stack

- **Backend:** Java 21, Spring Boot 3.5 (Web, Security, Validation, Data JPA)
- **Database:** MySQL 8.0, Liquibase
- **Documentation:** SpringDoc OpenAPI, Swagger UI
- **Build & Tooling:** Maven, Lombok, MapStruct
- **Testing:** JUnit 5, Mockito, MockMvc, Testcontainers, H2
- **External APIs:** TMDB API

### Quick Start (Backend Only)

Requires **Java 21** and a running **MySQL** instance with an empty database (`CREATE DATABASE moviematch;`).

```bash
export MYSQL_DATABASE=moviematch MYSQL_USER=root MYSQL_PASSWORD=secret
mvn spring-boot:run    # Runs on http://localhost:8080; Liquibase creates the schema automatically
```

By default the app uses the offline stub, so you do not need a TMDB key.
Real movies: set `TITLE_SOURCE=tmdb` and `TMDB_API_KEY` (the TMDB "API Read Access Token").

API docs: `http://localhost:8080/swagger-ui/index.html` (JSON: `/v3/api-docs`).
Health: `/actuator/health`.

Before a PR run `mvn test`. Room tests use Testcontainers, so Docker must be running;
account tests use H2.

### Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `MYSQL_HOST`, `MYSQL_PORT` | `localhost`, `3306` | database address |
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | – (required) | database name and login |
| `TITLE_SOURCE` | `stub` | `stub` (offline) or `tmdb` |
| `TMDB_API_KEY` | – | needed when `TITLE_SOURCE=tmdb` |
| `FRONTEND_ORIGINS` | `http://localhost:5173` | allowed browser origins (CORS), comma separated |
| `SESSION_COOKIE_SECURE` | `false` | set `true` when the site runs on HTTPS |
| `ROOM_MAX_PARTICIPANTS` | `5` | people per room |

### Project layout

`controller` (HTTP) → `service` (rules, transactions) → `repository` (database), with `model` (entities),
`dto` (requests and responses), `mapper`, `config`, `security`, `exception` and `source` (TMDB and stub).
Database changes live in `src/main/resources/db/changelog` (Liquibase, one file per change).

### More

- [Swagger UI](http://localhost:8080/swagger-ui/index.html) — every endpoint, with examples of requests and errors
- [docs/docker-and-deployment.md](docs/docker-and-deployment.md) — Docker image, CI/CD, infra repo
- [CONTRIBUTING.md](CONTRIBUTING.md) — Git flow and pull requests
- `src/main/resources/db/changelog/` — the database history

