# KnowFlow – AI Knowledge & Decision Assistant (Backend)

A Java 17 and Spring Boot 3 modular backend for the PanScience Innovations Senior Software Engineer assignment.

KnowFlow helps organizations upload their knowledge documents and provide a public AI-powered assistant that answers questions using organization-specific document content.

## 1. Architecture

```text
React + Vite Frontend
       |
       | Admin JWT / Public Assistant Token
       |
Spring Boot REST API (Port 8080)
       |
       ├── Authentication
       │   ├── Organization Registration
       │   ├── Login
       │   ├── BCrypt Password Hashing
       │   └── JWT Authentication
       |
       ├── Document Processing
       │   ├── PDF Upload
       │   ├── PDFBox Text Extraction
       │   └── Page-Level Chunking
       |
       ├── MongoDB
       │   ├── Organizations
       │   ├── Documents
       │   └── Knowledge Chunks
       |
       ├── Knowledge Retrieval
       │   └── Tenant-Scoped BM25-Like Ranking
       |
       ├── AI Decision Pipeline
       │   ├── Question Decomposition
       │   ├── Choice Model
       │   ├── Boolean Model
       │   ├── Score Model
       │   └── Decision Combination
       |
       └── Answer Generation
           ├── Optional LLM Integration
           └── Extractive Fallback
```

This is a modular Spring Boot application rather than separately deployed microservices. The modular approach keeps local development and deployment simple.

## 2. Technology Stack

* Java 17
* Spring Boot 3
* Spring Security
* Spring Data MongoDB
* MongoDB
* JWT Authentication
* BCrypt
* Apache PDFBox
* Maven
* Optional OpenAI-compatible LLM API

## 3. Prerequisites

* Java 17 or later
* Maven 3.9+
* MongoDB 6/7
* IntelliJ IDEA (recommended)
* Node.js 20+ and npm for the frontend

## 4. Backend Setup

### Step 1: Start MongoDB

Ensure MongoDB is running locally on:

`mongodb://localhost:27017`

If Docker Compose is configured in the project, you can alternatively start MongoDB using:

```bash
docker compose up -d
```

### Step 2: Configure Environment Variables

Configure the following values in your application environment:

```properties
JWT_SECRET=your-secure-secret-key
LLM_BASE_URL=
LLM_API_KEY=
LLM_MODEL=
```

Use a strong JWT secret. Never commit real API keys or production credentials.

### Step 3: Run the Backend

Open the backend project in IntelliJ IDEA.

Run:

```bash
mvn spring-boot:run
```

Alternatively, run `AssistantApplication` from IntelliJ IDEA.

Backend URL:

`http://localhost:8080`

### Step 4: Build the Project

```bash
mvn clean package
```

### Step 5: Run Tests

```bash
mvn test
```

## 5. API Documentation

| Method | Endpoint                        | Authentication | Description                 |
| ------ | ------------------------------- | -------------- | --------------------------- |
| POST   | `/api/auth/register`            | Public         | Register organization       |
| POST   | `/api/auth/login`               | Public         | Login                       |
| GET    | `/api/dashboard`                | Admin JWT      | Organization dashboard      |
| GET    | `/api/documents`                | Admin JWT      | List organization PDFs      |
| POST   | `/api/documents`                | Admin JWT      | Upload PDF                  |
| DELETE | `/api/documents/{id}`           | Admin JWT      | Delete PDF                  |
| POST   | `/api/assistant/regenerate`     | Admin JWT      | Regenerate assistant token  |
| GET    | `/api/assistant/{token}/config` | Public token   | Get assistant configuration |
| POST   | `/api/public/{token}/ask`       | Public token   | Ask assistant a question    |

Example registration request:

```json
{
  "organizationName": "North University",
  "email": "admin@north.test",
  "password": "password123"
}
```

## 6. Document Processing

The application supports:

* Maximum 10 PDFs per organization.
* Maximum 20 pages per PDF.
* Maximum file size of 10 MB.
* PDF text extraction using Apache PDFBox.
* Page-level text chunking.
* Organization, document and page metadata.

Scanned PDFs without extractable text are not supported because OCR is not currently implemented.

## 7. AI Knowledge & Decision Pipeline

The assistant processes questions through the following stages:

1. Resolve the organization using its public assistant token.
2. Retrieve knowledge chunks belonging to that organization.
3. Rank relevant chunks using a BM25-like lexical scoring approach.
4. Decompose the question into independently checkable conditions.
5. Execute three structured decision models in parallel:

    * Choice: classifies the question domain.
    * Boolean: checks evidence for individual conditions.
    * Score: evaluates retrieval relevance and condition coverage.
6. Combine the decision outputs.
7. Generate a grounded answer using retrieved document excerpts.
8. Return source document names, page numbers and decision signals.

An optional OpenAI-compatible LLM can be configured for question decomposition and answer generation.

Without an LLM API key, deterministic decomposition and extractive answer generation are used.

## 8. Security

The application includes:

* BCrypt password hashing.
* Signed JWT authentication for administrators.
* Separate random public assistant tokens.
* Organization IDs associated with documents and knowledge chunks.
* Organization-scoped document operations.
* Tenant-scoped knowledge retrieval.
* Document deletion restricted to the owning organization.

Tenant isolation must also be verified using cross-organization negative tests before deployment or submission.

## 9. Testing Scenarios

The following scenarios should be tested:

* Organization registration and login.
* Correct and incorrect passwords.
* PDF upload and deletion.
* PDF size and page limits.
* Direct questions.
* Policy questions.
* Procedural questions.
* Multi-condition questions.
* Comparison questions.
* Questions with missing knowledge.
* Public assistant access.
* Invalid assistant tokens.
* North-to-South and South-to-North tenant isolation.

## 10. Known Limitations

This project is a take-home MVP and is not intended as a production-ready deployment.

Current limitations include:

* Lexical retrieval instead of vector embeddings.
* No OCR support for scanned PDFs.
* No external document object storage.
* No email verification or password recovery.
* No comprehensive rate limiting.
* No audit logging.
* No refresh-token rotation.
* No formal privacy or compliance review.

## 11. Optional LLM Configuration

The backend supports an OpenAI-compatible Chat Completions API.

Example configuration:

```properties
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=your-api-key
LLM_MODEL=gpt-4o-mini
```

Use your own valid provider credentials. Keep API keys outside source control.

## 12. Frontend

The frontend is a separate React and Vite application.

Refer to the frontend README for installation, configuration and execution instructions.
