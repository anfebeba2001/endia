# Quickstart: Endia MVP — Local Development

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 21+ | Backend runtime |
| **Maven** | 3.9+ | Backend build tool |
| **Node.js** | 20+ | Frontend runtime |
| **npm** | 10+ | Frontend package manager |
| **Angular CLI** | 19+ | Frontend scaffolding (`npm install -g @angular/cli`) |
| **MySQL** | 8.x | Database |
| **Git** | 2.x+ | Version control |

## Environment Variables

Create a `.env` file at the repository root (or set these in your shell):

```bash
# LLM Configuration
LLM_API_KEY=sk-your-openai-compatible-api-key
LLM_BASE_URL=https://api.openai.com/v1

# MySQL Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=endia
DB_USERNAME=endia
DB_PASSWORD=endia_dev_password
```

## Database Setup

```bash
# Create the database (if not using Hibernate auto-DDL)
mysql -u root -e "CREATE DATABASE IF NOT EXISTS endia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -e "CREATE USER IF NOT EXISTS 'endia'@'localhost' IDENTIFIED BY 'endia_dev_password';"
mysql -u root -e "GRANT ALL PRIVILEGES ON endia.* TO 'endia'@'localhost';"
```

> **Note**: Hibernate's `ddl-auto=update` will create/update tables automatically in dev. No manual migration needed for MVP.

## Build & Run

### Backend (Spring Boot)

```bash
# From repository root
cd backend

# Build
mvn clean install -DskipTests

# Run (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the JAR
java -jar target/endia-backend-*.jar --spring.profiles.active=dev
```

Backend starts on **http://localhost:8080**.

### Frontend (Angular)

```bash
# From repository root
cd frontend

# Install dependencies
npm install

# Run dev server
ng serve

# Or with proxy to backend
ng serve --proxy-config proxy.conf.json
```

Frontend starts on **http://localhost:4200**.

## Proxy Configuration (Frontend → Backend)

Create `frontend/proxy.conf.json`:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

## Smoke Test

Once both servers are running:

```bash
# 1. Suggest subtopics
curl -X POST http://localhost:8080/api/themes/suggest \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Photosynthesis"}' | jq

# 2. Generate snacks (use themeId and subtopicIds from step 1)
curl -X POST http://localhost:8080/api/snacks/generate \
  -H "Content-Type: application/json" \
  -d '{"themeId": "<themeId>", "subtopicIds": ["<subtopicId1>", "<subtopicId2>"]}' | jq

# 3. Generate quiz (use themeId and subtopicId from step 1)
curl -X POST http://localhost:8080/api/quiz/generate \
  -H "Content-Type: application/json" \
  -d '{"themeId": "<themeId>", "subtopicId": "<subtopicId1>", "count": 4}' | jq

# 4. Answer a question (use questionId from step 3)
curl -X POST http://localhost:8080/api/quiz/answer \
  -H "Content-Type: application/json" \
  -d '{"questionId": "<questionId>", "chosenIndex": 0}' | jq
```

## Common Issues

### MySQL Connection Refused
- Ensure MySQL 8.x is running: `sudo systemctl status mysql`
- Check credentials match your `.env` file

### LLM API Errors
- Verify `LLM_API_KEY` and `LLM_BASE_URL` are set correctly
- The backend uses Java 21 `RestClient` (not WebClient) — no reactive dependencies needed
- LLM timeout is 30 seconds per call

### Port Conflicts
- Backend: 8080 (configurable via `server.port` in `application.yml`)
- Frontend: 4200 (configurable via `ng serve --port`)
- MySQL: 3306 (default)

## Project Structure Reference

```
endia/
├── backend/          # Spring Boot 3.4.x, Java 21
│   ├── pom.xml
│   └── src/
├── frontend/         # Angular 19+
│   ├── package.json
│   └── src/
├── specs/            # Feature specifications and plans
├── instructions/     # Weekly instruction docs
└── .env.example      # Environment variable template
```
