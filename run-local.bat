@echo off
set JWT_SECRET=local-development-secret-key-change-before-deploy-123456
set APP_BASE_URL=http://localhost:8080
set FRONTEND_ORIGIN=http://localhost:5173
mvn spring-boot:run
