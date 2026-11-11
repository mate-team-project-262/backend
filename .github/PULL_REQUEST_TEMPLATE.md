## What and why
<!-- Briefly describe the backend changes and link the Jira task: T2-XX -->

## How to check
1. 

## Checklist
- [ ] `mvn clean verify` passes (Unit & MySQL Testcontainers integration tests)
- [ ] Database schema changes are managed strictly via Liquibase migrations
- [ ] Branch follows Git Flow naming (`feat/T2-XX-...` or `fix/T2-XX-...`) and targets `develop`
- [ ] No hardcoded secrets, tokens, or `.env` files in the commit