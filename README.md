# RAG Smart FlowPAI

RAG Smart FlowPAI is a full-stack knowledge-base application built around a Java/Spring Boot backend. Users upload documents, the backend stores the original files, parses them into text chunks, generates embeddings, indexes the chunks in Elasticsearch, and retrieves permission-aware context for WebSocket-based chat responses.

The repository is not only an LLM chat UI. Most of the interesting work is in the backend: resumable upload state, object storage, asynchronous ingestion, document parsing, Elasticsearch indexing, organization-aware authorization, JWT security, usage quotas, rate limits, and admin-facing operational APIs.

## Stack

| Area | Implementation |
| --- | --- |
| Backend | Java 17, Spring Boot 3.4, Spring MVC, Spring Security, Spring WebSocket, WebFlux `WebClient` |
| Persistence and infrastructure | MySQL, Redis, Kafka, Elasticsearch 8, MinIO |
| Document processing | Apache Tika, PDFBox, HanLP |
| Retrieval and generation | OpenAI-compatible chat completions, configurable LLM providers, OpenAI-compatible embedding endpoint |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router, Naive UI, UnoCSS |
| Testing | JUnit 5, Spring Boot Test, H2, Mockito |

## What The System Does

The application supports a private or organization-scoped document knowledge base:

- Authenticated users upload supported document files in chunks.
- The backend records upload metadata in MySQL and chunk state in Redis.
- Chunks are stored in MinIO and composed into a final object after upload completion.
- A Kafka task triggers asynchronous parsing and vectorization.
- Parsed chunks are stored in MySQL and indexed into the `knowledge_base` Elasticsearch index.
- Chat requests run permission-aware retrieval before calling an active LLM provider.
- Responses stream back to the frontend over WebSocket.
- Admin APIs manage users, organization tags, invite codes, provider settings, rate limits, usage dashboards, recharge packages, and knowledge-base operations.

## Architecture

```mermaid
flowchart TB
    FE[Vue / TypeScript frontend]
    API[Spring Boot REST API]
    WS["Spring WebSocket /chat/{token}"]
    AUTH[Spring Security + JWT filters]
    UPLOAD[UploadController + UploadService]
    MINIO[(MinIO)]
    MYSQL[(MySQL)]
    REDIS[(Redis)]
    KAFKA[(Kafka)]
    CONSUMER[FileProcessingConsumer]
    PARSE[ParseService]
    VECTOR[VectorizationService]
    EMBED[Embedding provider]
    ES[(Elasticsearch knowledge_base)]
    CHAT[ChatHandler]
    SEARCH[HybridSearchService]
    LLM[LlmProviderRouter]

    FE -->|REST| API
    FE -->|WebSocket| WS
    API --> AUTH
    WS --> AUTH
    AUTH --> UPLOAD
    UPLOAD --> MYSQL
    UPLOAD --> REDIS
    UPLOAD --> MINIO
    UPLOAD --> KAFKA
    KAFKA --> CONSUMER
    CONSUMER --> MINIO
    CONSUMER --> PARSE
    PARSE --> MYSQL
    CONSUMER --> VECTOR
    VECTOR --> EMBED
    VECTOR --> ES
    WS --> CHAT
    CHAT --> REDIS
    CHAT --> SEARCH
    SEARCH --> EMBED
    SEARCH --> ES
    CHAT --> LLM
    LLM --> WS
```

## Storage Responsibilities

| Component | Responsibility in this codebase |
| --- | --- |
| MySQL | Users, organization tags, upload metadata, chunk metadata, parsed text chunks, invite codes, provider config, recharge records, token records, usage-related entities |
| Redis | Upload bitmap state, chat conversation history, current conversation IDs, organization-tag caches, rate-limit counters, quota and usage counters, PDF single-page preview cache |
| MinIO | Uploaded chunks under `chunks/{fileMd5}/{chunkIndex}` and merged files under `merged/{fileMd5}` |
| Kafka | Asynchronous file-processing handoff after upload merge; configured with retry and a dead-letter topic |
| Elasticsearch | `knowledge_base` index containing chunk text, dense vectors, page/anchor metadata, model version, owner, organization tag, and visibility fields |

## Backend Data Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as Spring Boot API
    participant Redis
    participant MySQL
    participant MinIO
    participant Kafka
    participant Consumer
    participant Parser
    participant Embed as Embedding Provider
    participant ES as Elasticsearch
    participant LLM

    Client->>API: POST /api/v1/upload/chunk
    API->>MySQL: create/update file and chunk metadata
    API->>Redis: mark uploaded chunk bit
    API->>MinIO: store chunk object
    Client->>API: POST /api/v1/upload/merge
    API->>Redis: verify uploaded bitmap
    API->>MinIO: compose chunks into merged/{fileMd5}
    API->>MySQL: mark upload complete and estimate embedding usage
    API->>Kafka: publish FileProcessingTask
    Kafka->>Consumer: consume file-processing task
    Consumer->>MinIO: download merged object by presigned URL
    Consumer->>Parser: parse and chunk document
    Parser->>MySQL: save DocumentVector rows
    Consumer->>Embed: batch embedding request
    Consumer->>ES: bulk index chunk documents
    Client->>API: WebSocket chat message
    API->>ES: hybrid retrieval with authorization filters
    API->>LLM: prompt with retrieved references
    LLM-->>Client: streamed response chunks
```

## Document Ingestion

Upload is implemented by `UploadController` and `UploadService`.

1. The frontend computes and sends `fileMd5`, `chunkIndex`, `totalSize`, `fileName`, optional `orgTag`, and `isPublic`.
2. On the first chunk, the backend validates the extension with `FileTypeValidationService`.
3. If no organization tag is supplied, the upload uses the user's primary organization tag.
4. Non-admin uploads are checked against the upload size limit configured on the target `OrganizationTag`, when one is set.
5. Each chunk is written to MinIO, recorded in `chunk_info`, and marked in Redis using a bitmap key shaped like `upload:{userId}:{fileMd5}`.
6. Upload status reads the bitmap in one Redis fetch and decodes uploaded chunk indexes locally.
7. Merge validates that all expected chunks exist, composes them in MinIO into `merged/{fileMd5}`, deletes chunk objects best-effort, clears the Redis upload bitmap, and marks the file upload complete.
8. Merge estimates embedding usage by parsing the merged file stream.
9. A transactional Kafka send publishes `FileProcessingTask` with file path, owner, org tag, and visibility.

The file-processing consumer downloads the merged file, parses it, saves chunks, embeds them, bulk-indexes Elasticsearch documents, and writes actual token/chunk counts back to the upload row.

## Parsing And Chunking

`ParseService` is the main parsing entry point.

- PDF files are detected by `%PDF-` header, parsed page by page using PDFBox, and stored with page numbers.
- Repeated PDF boundary lines are filtered as likely headers/footers.
- Non-PDF files are parsed with Apache Tika through a streaming content handler.
- Large non-PDF text is handled as parent chunks, then split into smaller child chunks.
- Chunking prefers paragraph and sentence boundaries.
- HanLP is used as a fallback for very long Chinese sentences; if HanLP fails, the code falls back to character splitting.
- Saved chunks include `fileMd5`, `chunkId`, `textContent`, `pageNumber`, `anchorText`, `userId`, `orgTag`, and `isPublic`.

## Vectorization And Indexing

`VectorizationService` loads parsed chunks from MySQL, calls `EmbeddingClient` in batches, verifies that the embedding count matches the chunk count, then writes `EsDocument` records to Elasticsearch through `ElasticsearchService.bulkIndex`.

`EmbeddingClient` uses the active embedding provider from `ModelProviderConfigService`. The default configuration is OpenAI-compatible and points to DashScope-compatible embeddings unless overridden through persisted provider config or environment variables.

The Elasticsearch index is initialized from `src/main/resources/es-mappings/knowledge_base.json`. It contains:

- `textContent` analyzed with IK analyzers.
- `vector` as a 2048-dimensional cosine dense vector.
- metadata fields for file ID, chunk ID, page number, model version, owner, org tag, and visibility.

## Hybrid Retrieval

`HybridSearchService.searchWithPermission` is the permission-aware retrieval path used by chat.

1. Resolve the user and compute effective organization tags through `OrgTagCacheService`.
2. Generate a query embedding through `EmbeddingClient`.
3. If embedding fails, fall back to text-only Elasticsearch search.
4. Run Elasticsearch KNN recall against `vector`.
5. Require a text match on `textContent`.
6. Apply authorization filters in Elasticsearch for owner, public documents, or effective organization tags.
7. Apply a second-stage rescore using a stricter `textContent` match with `Operator.And`.
8. Attach file names from MySQL before returning results.

The retrieval mode is returned as `HYBRID` or `TEXT_ONLY`, and chat stores reference mappings so the frontend can request citation details later.

## Authentication And Permissions

Authentication is JWT-based and stateless.

- `SecurityConfig` protects REST APIs by role and installs `JwtAuthenticationFilter`.
- `JwtAuthenticationFilter` reads `Authorization: Bearer ...`, validates the token, refreshes tokens when configured logic allows it, and sets the Spring Security authentication.
- `OrgTagAuthorizationFilter` extracts `userId`, `role`, and `orgTags` request attributes for upload/document/search APIs.
- Resource checks are based on the `file_upload` table for document-style URLs.
- Users have comma-separated org tags and a primary org tag.
- New users receive a private organization tag and the default tag during registration.
- Admin users can create org tags, assign org tags, and manage users through admin APIs.

Document access is allowed when a file is owned by the user, marked public, or belongs to an organization tag in the user's effective tag set. Effective tags include direct tags, parent tags, and the default tag.

## Chat Flow

The WebSocket endpoint is `/chat/{token}`. `ChatWebSocketHandler` validates the JWT token from the path and maps the session to the token's user ID.

For each chat message, `ChatHandler`:

1. Checks per-user chat rate limits.
2. Loads or creates a Redis-backed current conversation ID.
3. Reads recent conversation history from Redis.
4. Runs `HybridSearchService.searchWithPermission`.
5. Builds a reference context for the LLM prompt.
6. Streams through `LlmProviderRouter`.
7. Sends chunks to the WebSocket client as JSON.
8. Detects completion, sends a completion event, and stores the latest conversation messages and reference mappings in Redis.

`LlmProviderRouter` uses active provider settings from `ModelProviderConfigService`, reserves estimated token usage before the request, and settles usage after streaming based on provider usage data when available.

## API Surface

| Prefix | Purpose |
| --- | --- |
| `/api/v1/users` | Registration, login, current user, org tags, user usage, token records, logout |
| `/api/v1/auth` | Token refresh |
| `/api/v1/upload` | Chunk upload, upload status, merge, supported file types |
| `/api/v1/documents` | Accessible files, uploaded files, delete, reindex, download, preview, citation detail |
| `/api/v1/search` | Hybrid search |
| `/api/v1/chat` | WebSocket token metadata for stop commands |
| `/api/v1/users/conversation` | Conversation history |
| `/api/v1/admin` | User, knowledge-base, org-tag, invite-code, model-provider, rate-limit, usage, recharge administration |
| `/api/v1/recharge` | Recharge packages, order creation, callbacks, order lookup |

## Frontend

The frontend lives in `frontend/` and is a Vue 3 + TypeScript Vite application. Implemented views include chat, chat history, knowledge-base upload/search/management, user management, org-tag management, model-provider settings, invite codes, usage monitoring, recharge, and personal center.

The knowledge-base upload UI sends both `public` and `isPublic` values, while the backend upload endpoint consumes `isPublic`.

## Local Development

### Requirements

- JDK 17
- Maven 3.8+
- Node.js 18.20+
- pnpm 8.7+
- Docker / Docker Compose

### Configure Environment

Copy the example environment file and fill in local credentials:

```bash
cp .env.example .env
```

Important values:

```bash
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8081
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/PaiSmart?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=change-me
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=
SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
MINIO_ENDPOINT=http://localhost:9000
MINIO_PUBLIC_URL=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=uploads
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
ELASTICSEARCH_SCHEME=https
ELASTICSEARCH_USERNAME=elastic
ELASTICSEARCH_PASSWORD=change-me
JWT_SECRET_KEY=<base64 secret from openssl rand -base64 32>
DEEPSEEK_API_KEY=<optional LLM key>
EMBEDDING_API_KEY=<embedding key>
```

The application loads `.env` through `DotenvEnvironmentPostProcessor`.

### Start Infrastructure

The repository includes an infrastructure Compose file:

```bash
docker compose -f docs/docker-compose.yaml up -d
```

It starts MySQL, Redis, Kafka, MinIO, and Elasticsearch. The Compose file is infrastructure-only; it does not containerize the Spring Boot or frontend applications.

The application also defines `NewTopic` beans for the configured Kafka topics. The default application topic is `file-processing-topic1`; the Compose file additionally creates `file-processing` and `vectorization`, so check topic names if you customize Kafka setup.

### Initialize Database

The application defaults to Hibernate `ddl-auto=update`, and the repository also includes SQL under `docs/databases/ddl.sql`.

For SQL initialization:

```bash
mysql -uroot -p PaiSmart < docs/databases/ddl.sql
```

For first admin creation, temporarily enable the bootstrap values in `.env`:

```bash
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=<strong password of at least 12 characters>
```

Turn `ADMIN_BOOTSTRAP_ENABLED` back to `false` after the account exists.

### Run Backend

```bash
mvn spring-boot:run
```

Default backend port: `http://localhost:8081`.

### Run Frontend

```bash
cd frontend
pnpm install
pnpm dev
```

The frontend package scripts use Vite modes named `test` and `prod`.

## Testing

Backend tests are under `src/test/java`.

```bash
mvn test
```

The test profile uses H2 in MySQL compatibility mode and disables startup knowledge bootstrap. Many service tests use Mockito for collaborators such as repositories, MinIO, Elasticsearch, Kafka, and provider clients.

Current coverage focuses on:

- JWT refresh behavior.
- User registration and org-tag behavior.
- Invite-code logic.
- Usage quota and dashboard calculations.
- Rate-limit configuration.
- Model-provider configuration.
- Parse-service chunking behavior.
- Upload-controller behavior.
- Bootstrap knowledge initialization.
- Conversation service behavior.

`UploadServicePerformanceTest` is explanatory: it logs the expected reason for the Redis bitmap optimization but does not run a reproducible benchmark.

Frontend scripts include:

```bash
cd frontend
pnpm typecheck
pnpm lint
pnpm build
```

There is also a Playwright spec at `frontend/playwright-kb-column.spec.ts`.

## Current Limitations

- Elasticsearch public-field naming needs verification. The mapping defines `isPublic`, while `HybridSearchService` filters on `public`. If Jackson indexes the field as `public`, the mapping and query should be aligned; if it indexes `isPublic`, public-document filtering in Elasticsearch will not match as intended.
- `SearchController` falls back to the non-permission `search(query, topK)` path when `userId` is absent, despite a comment saying anonymous search should return only public content. The Spring Security configuration requires authentication for `/api/v1/search/**`, but the fallback should still be treated carefully.
- `OrgTagAuthorizationFilter` only resolves resource metadata from `file_upload`; the code has a TODO for other resource types.
- Kafka retry and DLT are configured through `DefaultErrorHandler`, but there are no tests proving end-to-end DLT behavior against a real Kafka broker.
- The Compose file provisions infrastructure only. There is no root Dockerfile for the backend or a full application stack Compose file.
- The local helper script `infra.sh` contains machine-specific paths, so `docs/docker-compose.yaml` is the portable infrastructure path.
- Token accounting uses estimation before provider responses and settles with provider-reported usage when available; estimates are heuristic, not tokenizer-exact.
- Switching the active embedding provider is blocked when it would require re-embedding existing content; there is no migration job for that path yet.
- The chat completion monitor infers stream completion by watching response length over time instead of using an explicit provider completion signal.
- Tests cover important units and controller behavior, but there is no full integration test that starts MySQL, Redis, Kafka, MinIO, Elasticsearch, and provider mocks together.

## Repository Layout

```text
src/main/java/com/yizhaoqi/smartpai
  client/       LLM and embedding API clients
  config/       Spring Security, Kafka, Redis, MinIO, Elasticsearch, WebSocket, quota, bootstrap config
  consumer/     Kafka document-processing consumer
  controller/   REST API controllers
  entity/       Elasticsearch/search DTOs
  model/        JPA entities and task models
  repository/   Spring Data repositories
  service/      Upload, parsing, vectorization, retrieval, chat, quota, user, document, admin services
  utils/        JWT, password, crypto, logging, JSON, migration helpers

src/main/resources
  application*.yml
  es-mappings/knowledge_base.json

frontend/
  Vue 3 / TypeScript frontend application

docs/
  docker-compose.yaml
  databases/ddl.sql
  bootstrap knowledge documents and deployment notes
```

## License

This repository is licensed under the terms in `LICENSE`.
