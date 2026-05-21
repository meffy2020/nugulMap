# PROJECT KNOWLEDGE BASE

**Generated:** 2026-02-14
**Context:** Monorepo (Next.js, Expo, Spring Boot, Python)

## OVERVIEW
Neogul Map is a multi-service platform featuring a Next.js web frontend, an Expo/React Native mobile app, a Spring Boot API server, and Python FastAPI data scripts. The system is containerized via Docker Compose for full-stack orchestration.

## STRUCTURE
```
.
├── frontend/             # Next.js 13+ App Router application
├── mobile/               # Expo/React Native mobile application
├── backend/
│   ├── api-server/       # Spring Boot Main API (Java 21)
│   └── data-scripts/     # Python FastAPI utilities
└── docker-compose.yml    # Root orchestration
```

## COMMANDS
```bash
# Full Stack
docker-compose up -d

# Frontend (Dev)
cd frontend && npm run dev

# Mobile (Dev)
cd mobile && npx expo start

# Backend API (Dev)
cd backend/api-server && ./gradlew bootRun

# Data Scripts (Dev)
cd backend/data-scripts && uvicorn main:app --reload
```
