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
- `chat/service/ModelConnectionTestApplicationService`：复用模型配置解析链路，编排连接测试用例。
- `chat/service/PromptAssembler`：拼装系统提示词、知识库内容和用户问题。
- `chat/port/in/*`：Controller 可以调用的入站用例。
- `chat/port/out/*`：模型配置、RAG 和模型调用的出站端口。

#### 知识库

- `rag/service/RagKnowledgeApplicationService`：知识库用例编排器。
- `rag/port/in/*`：上传文件、分析 Git、查询标签的入站用例。
- `rag/port/out/*`：文件存储、标签存储和 Git 读取端口。
- `rag/command/*`：知识库操作命令。
- `rag/model/*`：不依赖 Web 或 Spring AI 的文件模型。

#### 模型选择器目录

- `modelcatalog/model/ModelConfigSummary`：不含连接细节的模型配置摘要。
- `modelcatalog/port/in/ListModelConfigsUseCase`：查询启用模型的入站用例。
- `modelcatalog/port/out/ModelConfigCatalogQueryPort`：模型目录查询出站端口。
- `modelcatalog/service/ModelConfigCatalogApplicationService`：模型选择器目录用例。

#### MCP 工具目录

- `mcp/model/McpToolDescriptor`：暴露给选择器的安全工具摘要。
- `mcp/port/in/ListModelToolsUseCase`：按模型配置列出允许工具的入站用例。
- `mcp/port/in/ExecuteMcpToolUseCase`：执行已绑定且通过只读策略的工具。
- `mcp/port/out/McpToolCatalogQueryPort`：工具目录查询出站端口。
- `mcp/port/out/McpServerConnectionQueryPort` / `McpToolExecutionPort`：解析登记连接并隔离 MCP SDK。
- `mcp/service/McpToolCatalogApplicationService`：只读工具目录用例编排。
- `mcp/service/McpToolExecutionApplicationService`：再次校验工具白名单、只读策略、每分钟调用预算和递归参数边界。

### `ooo1208-infrastructure`

基础设施层实现 application 定义的出站端口。

- `chat/*Adapter`：把统一聊天模型转换为 Ollama 或 OpenAI Compatible SDK 调用。
- `chat/ChatModelFactory`：按本次解析出的连接地址和凭证引用创建聊天客户端。
- `config/OpenAiApiSupport`：规范化 OpenAI Compatible base URL，兼容带或不带 `/v1` 的配置。
- `modelcatalog/ModelConnectionTestAdapter`：通过 Ollama 模型列表或 OpenAI Compatible `/v1/models` 进行轻量探针。
- `modelcatalog/JdbcModelConfigQueryAdapter`：从 PostgreSQL 模型目录读取启用的模型预设。
- `modelcatalog/InMemoryModelConfigQueryAdapter`：仅在 `in-memory-model-config` profile 下作为过渡实现。
- `mcp/JdbcMcpToolCatalogQueryAdapter`：按模型预设、工具绑定和服务器状态查询 MCP 白名单。
- `mcp/InMemoryMcpToolCatalogQueryAdapter`：离线 profile 返回空工具集，不伪造外部工具。
- `mcp/JdbcMcpServerConnectionQueryAdapter`：只读取启用且 ACTIVE 的登记服务器连接。
- `mcp/SpringAiMcpToolExecutionAdapter`：默认关闭的 SSE MCP client，限制 endpoint 路径、公网 URL、超时、无重定向、原始 SSE event、JSON 解析大小和不可信输出。
- `network/OutboundUrlValidator`：联网搜索和远程 MCP 共用的公网 URL 安全校验；`OutboundHostAllowlist` 与 `ModelProviderEndpointValidator` 为固定/动态模型聊天、Embedding 和连接测试提供按 Provider 分组的 managed host/port 策略。
- `websearch/TavilyCompatibleNetworkSearchAdapter`：默认关闭的固定 Provider 搜索适配器，凭证只从配置引用解析。
- `websearch/NetworkSearchApplicationService`：固定 TTL/LRU 搜索缓存和 Provider 结果不可变保护。
- `rag/PgVectorRagRetrieverAdapter`：PgVector 检索。
- `rag/PgVectorRagDocumentStoreAdapter`：Tika 解析、切分和向量写入。
- `rag/RedisRagTagStoreAdapter`：Redis 标签存储。
- `rag/JGitRepositoryReaderAdapter`：Git 仓库读取。
- `config/AiInfrastructureConfig`：按 Provider 出站策略创建 Spring AI、Embedding 和 PgVector Bean；PostgreSQL JDBC 连接另受数据库网络配置约束。

### `ooo1208-trigger`

入口适配层只负责协议转换。

- `ChatController`：`POST /api/v1/chat` 和 `/api/v1/chat/stream`。
- `ModelConnectionController`：`POST /api/v1/model-connections/test`。
- `ModelToolController`：工具目录、选择预检，以及 `POST /api/v1/model-configs/{modelConfigId}/tools/{toolId}/execute`。
- `McpToolRequestSizeFilter`：在 MVC JSON 反序列化前限制 MCP 执行请求体，默认 128 KiB；chunked/未知长度会先做受限缓存，避免 JSON 提前结束留下尾部 padding 旁路。
- `ModelConfigController`：`GET /api/v1/model-configs`，返回不含凭证和连接地址的模型摘要。
- `WebSearchController`：`GET /api/v1/web-search`，只接收查询文本和结果数量。
- `RagController`：知识库上传、Git 分析和标签查询。
- `ChatRequest`：聊天 HTTP 请求 DTO。
- `ModelConnectionTestRequest/Response`：连接测试请求和稳定的 HTTP 结果 DTO。
- `ApiResponse`：HTTP 返回包装对象。

### `ooo1208-app`

启动和装配层。

- `Application`：Spring Boot 启动入口。
- `ChatApplicationConfiguration`：组装聊天应用服务。
- `RagApplicationConfiguration`：组装知识库应用服务。
- `ModelCatalogDataInitializer`：补齐数据库中的系统预置模型配置。
- `RedisClientConfig`：创建 Redisson 客户端。

## 一次聊天请求怎么走

```text
ChatController
  -> ChatRequest
  -> StreamChatCommand
  -> ChatApplicationService
  -> ModelConfigQueryPort
  -> RagRetrieverPort（有 ragTag 时）
  -> NetworkSearchPort（有 webSearch=true 时）
  -> PromptAssembler
  -> ChatGenerationPort
  -> OllamaChatGenerationAdapter / OpenAiCompatibleChatGenerationAdapter
  -> ChatChunk / ChatResponse
```

## 当前阶段边界

当前数据库模型目录已经具备初始表结构、读取适配器、动态聊天客户端工厂和已登记配置的轻量连接测试；MCP 工具目录具备 V2 表结构、按模型预设的读取/选择适配器和默认关闭的 SSE 只读执行入口；固定联网搜索第一版已具备 application 端口、默认关闭的 Tavily-compatible 适配器、受限缓存和 HTTP 入口，公网出站 URL 有统一校验边界。管理员 CRUD、权限控制、MCP 工具同步、Streamable HTTP/STDIO、审批审计、用户自定义 API Key、模型自动发现和完整前端选择器属于后续阶段。
