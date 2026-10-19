# 🛠️ Team 262 — Contribution & Git Flow Guidelines

Welcome to the project! To maintain code quality and avoid merge conflicts, please follow these team rules.

---

## 🌿 Git Branching Strategy (Git Flow)

* **`main`** — Production-ready branch for stable releases and demos. Direct pushes are blocked.
* **`develop`** — Default working branch. All features and fixes are merged here via Pull Requests. Direct pushes are blocked.
* **`feature/T2-XX-short-name`** — Working branches for Jira tasks (e.g., `feature/T2-12-create-room`).
* **`fix/T2-XX-short-name`** — Working branches for bug fixes.

---

## 🚀 Step-by-Step Git Workflow

### 1. Start a New Task
Always pull the latest changes from `develop` before creating your branch:
```bash
git checkout develop
git pull origin develop
git checkout -b feature/T2-XX-task-title
```

### 2. Commit Your Changes
* Write clear commit messages with your Jira ticket number:
  ```bash
  git add .
  git commit -m "feat(room): create room endpoint (T2-12)"
  ```
* 🔐 **Security Rule:** NEVER commit real passwords, API keys, or `.env` files! Always use environment variables.

### 3. Update & Open a Pull Request (PR)
Before pushing, sync your branch with `develop` to avoid merge conflicts:
```bash
git checkout develop
git pull origin develop
git checkout feature/T2-XX-task-title
git merge develop
git push -u origin feature/T2-XX-task-title
```
* Go to GitHub and open a **Pull Request into `develop`**.
* Use **Squash and merge** when merging your PR.

---

## 🗄️ Local Database Setup (MySQL 8.0 via Docker)

1. Create your local `.env` file from the template:
   ```bash
   cp .env.example .env
   ```
2. Start the MySQL container in the background:
   ```bash
   docker compose up -d
   ```
3. Check that the container status is `healthy`:
   ```bash
   docker compose ps
   ```