# 代码结构地图

这份文件用于快速定位代码。先看模块，再看类注释；不要从 Controller 直接跳到 Spring AI 代码。

## 模块依赖

```text
ooo1208-domain
        ↑
ooo1208-application
        ↑                    ↑
ooo1208-trigger      ooo1208-infrastructure
        \                    /
             ooo1208-boot
```

### `ooo1208-domain`

领域层只放稳定的业务概念，不知道 Spring、HTTP、数据库和具体模型 SDK。

- `ModelConfigId`：模型配置的稳定 ID。
- `ProviderType`：供应商协议类型。

### `ooo1208-application`

应用层负责业务流程和端口定义，是整个项目的核心。

#### 聊天

- `chat/command/StreamChatCommand`：聊天用例输入。
- `chat/model/ChatPrompt`：发给模型的统一输入。
- `chat/model/ChatChunk`：流式输出片段。
- `chat/model/ChatResponse`：非流式输出结果。
- `chat/model/ResolvedModelConfig`：根据 `modelConfigId` 解析出的调用配置。
- `chat/service/ChatApplicationService`：统一编排配置解析、RAG、Prompt 和模型调用。
- `chat/service/PromptAssembler`：拼装系统提示词、知识库内容和用户问题。
- `chat/port/in/*`：Controller 可以调用的入站用例。
- `chat/port/out/*`：模型配置、RAG 和模型调用的出站端口。

#### 知识库

- `rag/service/RagKnowledgeApplicationService`：知识库用例编排器。
- `rag/port/in/*`：上传文件、分析 Git、查询标签的入站用例。
- `rag/port/out/*`：文件存储、标签存储和 Git 读取端口。
- `rag/command/*`：知识库操作命令。
- `rag/model/*`：不依赖 Web 或 Spring AI 的文件模型。

### `ooo1208-infrastructure`

基础设施层实现 application 定义的出站端口。

- `chat/*Adapter`：把统一聊天模型转换为 Ollama 或 OpenAI Compatible SDK 调用。
- `modelcatalog/InMemoryModelConfigQueryAdapter`：Phase 0 的两个预置模型配置。
- `rag/PgVectorRagRetrieverAdapter`：PgVector 检索。
- `rag/PgVectorRagDocumentStoreAdapter`：Tika 解析、切分和向量写入。
- `rag/RedisRagTagStoreAdapter`：Redis 标签存储。
- `rag/JGitRepositoryReaderAdapter`：Git 仓库读取。
- `config/AiInfrastructureConfig`：创建 Spring AI、Embedding 和 PgVector Bean。

### `ooo1208-trigger`

入口适配层只负责协议转换。

- `ChatController`：`POST /api/v1/chat` 和 `/api/v1/chat/stream`。
- `RagController`：知识库上传、Git 分析和标签查询。
- `ChatRequest`：聊天 HTTP 请求 DTO。
- `ApiResponse`：HTTP 返回包装对象。

### `ooo1208-app`

启动和装配层。

- `Application`：Spring Boot 启动入口。
- `ChatApplicationConfiguration`：组装聊天应用服务。
- `RagApplicationConfiguration`：组装知识库应用服务。
- `RedisClientConfig`：创建 Redisson 客户端。

## 一次聊天请求怎么走

```text
ChatController
  -> ChatRequest
  -> StreamChatCommand
  -> ChatApplicationService
  -> ModelConfigQueryPort
  -> RagRetrieverPort（有 ragTag 时）
  -> PromptAssembler
  -> ChatGenerationPort
  -> OllamaChatGenerationAdapter / OpenAiCompatibleChatGenerationAdapter
  -> ChatChunk / ChatResponse
```

## 当前阶段边界

当前 `InMemoryModelConfigQueryAdapter` 只是过渡实现，模型配置仍是系统预置值。数据库模型目录、用户自定义 API Key、模型自动发现和前端模型选择器属于后续阶段。
