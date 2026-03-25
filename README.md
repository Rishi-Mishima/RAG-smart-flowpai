## RAG Smart Flow – AI Knowledge & Conversational System

A production-grade **Retrieval-Augmented Generation** (RAG) system for intelligent document processing, semantic search, and conversational AI.

It enables intelligent document processing and semantic retrieval by combining Elasticsearch-based search, vector embeddings, and large language models.

Built with a modern full-stack architecture combining Spring Boot, Elasticsearch, and LLM integration, this system enables users to query their own knowledge base using natural language and receive context-aware AI-generated responses.

> Core technologies include: `Elasticsearch`, `Kafka`, `WebSocket`, `Spring Security`, `Docker`, `MySQL`, and `Redis`.



![派聪明多模块架构](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102133.png)

## ✨ Features

- 📄 Upload and manage various types of documents
- ⚙️ Automatic document processing, parsing, and indexing
- 🔍 Natural language querying over user-owned knowledge bases
- 🤖 Context-aware AI responses grounded in retrieved documents



## 🧱 Tech Stack && 🏗️ Project Structure

### Backend

- **Framework**: Spring Boot 3 (Java 17)
- **Database**: MySQL 8.0 (JPA / Hibernate)
- **Cache**: Redis
- **Search**: Elasticsearch 8.x (Hybrid Retrieval)
- **Message Queue**: Apache Kafka
- **File Storage**: MinIO
- **Document Processing**: Apache Tika
- **Security**: Spring Security + JWT
- **AI Integration**:
  - LLM: DeepSeek / Ollama (local)
  - Embedding: Vector-based semantic search
- **Realtime Communication**: WebSocket / SSE
- **Build Tool**: Maven
- **Reactive Support**: WebFlux

<br>
 🧠 What Makes It Interesting

- Hybrid retrieval (keyword + vector search)
- RAG-based conversational system
- Scalable async architecture (Kafka)
- Real-time streaming responses
- Enterprise-ready multi-tenant design


### Backend (Spring Boot)

The backend follows a layered architecture with clear separation of concerns:

```bash
src/main/java/com/yourname/ragflow/
├── application/        # Application entry point
├── client/             # External service clients (LLM, Embedding APIs)
├── config/             # Configuration (Security, JWT, ES, Redis)
├── controller/         # REST API layer
├── service/            # Core business logic (RAG pipeline)
├── repository/         # Data access layer (JPA)
├── entity/             # Database entities
├── model/              # Domain models / DTOs
├── consumer/           # Kafka consumers (async processing)
├── handler/            # WebSocket / streaming handlers
├── exception/          # Global exception handling
└── utils/              # Utility classes
```
💡 Highlights
- Layered architecture (Controller → Service → Repository)
- Async processing via Kafka consumers
- Real-time communication via WebSocket handlers
- Modular design for RAG pipeline and external AI integration

<br>

### Frontend (Vue 3 + TypeScript)

🧱 Frontend Tech Stack:
- **Framework**: Vue 3 + TypeScript  
- **Build Tool**: Vite  
- **UI Library**: Naive UI  
- **State Management**: Pinia  
- **Routing**: Vue Router  
- **Styling**: UnoCSS + SCSS  
- **Icons**: Iconify  
- **Package Manager**: pnpm  

<br>
The frontend is constructed as :

```bash
frontend/
├── public/             # Static assets
├── src/
│   ├── assets/         # Images & icons
│   ├── components/     # Reusable UI components
│   ├── layouts/        # Layout system
│   ├── router/         # Routing configuration
│   ├── store/          # State management (Pinia)
│   ├── service/        # API layer
│   ├── views/          # Page-level components
│   └── utils/          # Utility functions
```



## 核心功能

这里我先带大家了解一下什么是派聪明，我为什么要做派聪明这个企业级的 RAG 知识库？派聪明这个 AI 项目能让大家学到什么？以及如何解锁派聪明的源码仓库和教程？

![派聪明的聊天助手：会依据知识库进行问答](https://cdn.tobebetterjavaer.com/paicoding/2550c873a349d8bee29d46400f12ce76.png)

![派聪明的架构概览](https://cdn.tobebetterjavaer.com/stutymore/README-20250730101618.png)

### 知识库管理

派聪明提供了完整的文档上传与解析功能，支持文件分片上传和断点续传，并支持标签进行组织管理。文档可以是公开的，也可以是私有的，并且可以与特定的组织标签关联，以便更好地进行权限分类。

![派聪明文档处理](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102808.png)

### AI驱动的RAG实现

派聪明的核心是 RAG 实现：

![派聪明聊天交互](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102837.png)

- 将上传的文档进行语义分块
- 调用豆包 Embedding 模型为每个文本块生成高维向量
- 将向量存储到 ElasticSearch 以支持语义搜索和关键词搜索
- 可以根据用户的查询检索相关文档
- 为 LLM 提供完整的上下文，从而生成更准确、基于文档的响应内容

### 企业级多租户

派聪明通过组织标签支持多租户架构。每个用户可以创建或加入一个或多个组织，每个组织可以拥有独立的知识库和文档管理。这样，企业可以在同一系统中管理多个团队或部门的知识库，而无需担心数据混淆或权限问题。

![派聪明的安全架构](https://cdn.tobebetterjavaer.com/stutymore/README-20250730103118.png)

### 实时通信

系统采用 WebSocket 技术，提供用户与 AI 系统之间的实时交互，支持响应式聊天界面，便于知识检索和 AI 互动。

## 前置环境

在开始之前，请确保已安装以下软件：

- Java 17
- Maven 3.8.6 或更高版本
- Node.js 18.20.0 或更高版本
- pnpm 8.7.0 或更高版本
- MySQL 8.0
- Elasticsearch 8.10.0
- MinIO 8.5.12
- Kafka 3.2.1
- Redis 7.0.11
- Docker（可选，用于运行 Redis、MinIO、Elasticsearch 和 Kafka 等服务）

## 架构设计

派聪明的架构具备一个现代化的、云原生应用程序的特点，具有清晰的关注点分离、可扩展的组件和与 AI 技术的集成。模块化设计允许随着技术的发展，特别是快速变化的 AI 集成领域，未来可以扩展和替换单个组件。

![派聪明的系统概述](https://cdn.tobebetterjavaer.com/stutymore/README-20250730102655.png)

控制层用于处理 HTTP 请求，验证输入，管理请求/响应格式化，并将业务逻辑委托给服务层。控制器按领域功能组织。遵循 RESTful 设计原则，集成了性能监控和日志记录，用于跟踪 API 使用和故障排除。

```java
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    @Autowired
    private DocumentService documentService;
    
    @DeleteMapping("/{fileMd5}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("role") String role) {
        // 参数验证和委托给服务
        documentService.deleteDocument(fileMd5);
        // 响应处理
    }
}
```

服务层主要用来处理应用的业务逻辑，具有事务感知能力，能够处理跨越多个数据源的操作。

```java
@Service
public class DocumentService {
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private ElasticsearchService elasticsearchService;
    
    @Transactional
    public void deleteDocument(String fileMd5) {
        // 文档删除的业务逻辑
        // 协调多个仓储和系统
    }
}
```

数据访问层使用 Spring Data JPA 进行数据库操作，提供了对 MySQL 的 CRUD 操作。

```java
@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
    Optional<FileUpload> findByFileMd5(String fileMd5);
    
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId OR f.isPublic = true OR (f.orgTag IN :orgTagList AND f.isPublic = false)")
    List<FileUpload> findAccessibleFilesWithTags(@Param("userId") String userId, @Param("orgTagList") List<String> orgTagList);
}
```

实体层由映射到数据库表的 JPA 实体以及用于 API 请求和响应的 DTO（数据传输对象）组成。

```java
@Entity
public class FileUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fileMd5;
    private String fileName;
    private String userId;
    private boolean isPublic;
    private String orgTag;
    // 其他字段和方法
}
```

## 前端启动

```bash
# 进入前端项目目录
cd frontend

# 安装依赖
pnpm install

# 启动项目
pnpm run dev
```

## 环境变量与脚本

项目根目录的 `.env` 用于保存本地运行和部署相关配置。该文件已在 `.gitignore` 中忽略，不会进入版本库；首次使用时可参考 `.env.example` 填写真实值。

当前 `.env` 里主要包含两类配置：

- 应用运行配置：如 MySQL、Redis、Kafka、MinIO、Elasticsearch、JWT、AI Provider 等
- 前端部署配置：如 `DEPLOY_SERVER_HOST`、`DEPLOY_SERVER_USER`、`DEPLOY_SERVER_KEY`、`DEPLOY_TARGET_DIR`、`DEPLOY_HEALTHCHECK_URL`

常用脚本如下。

### `infra.sh`

用于在本机启动、停止和查看基础依赖服务，目前支持 `minio`、`kafka`、`elasticsearch`。

```bash
# 启动全部基础服务
./infra.sh start

# 启动指定服务
./infra.sh start minio kafka

# 查看状态
./infra.sh status

# 查看某个服务日志
./infra.sh logs elasticsearch

# 输出本地访问地址
./infra.sh urls
```

### `deploy-front.sh`

用于构建前端、打 zip 包、上传到服务器，并在远端替换 `/home/www/PaiSmart-Front/dist`。脚本会自动读取根目录 `.env` 中的部署配置。

```bash
# 直接构建并部署前端
./deploy-front.sh
```

部署脚本默认会执行这些步骤：

- 进入 `frontend` 执行 `pnpm build`
- 打包 `dist` 为 zip 文件并上传到服务器
- 删除远端旧的 `dist` 目录并解压新包
- 检查远端 `dist/index.html` 是否存在
- 请求 `DEPLOY_HEALTHCHECK_URL` 做健康检查

如果只想复用已有的前端构建产物，可以在执行时跳过构建：

```bash
DEPLOY_SKIP_BUILD=1 ./deploy-front.sh
```

