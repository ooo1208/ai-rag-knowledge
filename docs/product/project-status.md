# 项目状态与交接记录

最后更新：2026-10-06

## 当前结论

Phase 0 和 Phase 1 的核心代码已经落地：聊天和 RAG 都有统一的 application 用例，HTTP 层不再直接编排模型或向量库，Ollama 和 OpenAI Compatible 通过出站适配器接入。

这一版已经完成模型目录数据库化、动态客户端、连接测试、MCP 工具选择目录、第一版 SSE MCP 只读执行和固定联网搜索：Flyway 创建模型目录与 MCP 白名单表，JDBC 适配器读取启用的模型预设、工具绑定和服务器连接，聊天适配器会按解析结果创建对应的 Ollama 或 OpenAI Compatible 客户端，并提供基于已登记 `modelConfigId` 的轻量连接探针。基础设施层还增加了统一的公网出站 URL 校验边界、默认关闭的 Tavily-compatible 搜索适配器和默认关闭的 MCP SSE 客户端。它仍不是最终的动态配置产品，管理员 API、权限控制、MCP 审批/持久化审计和搜索权限审计还没有完成。

## 已完成

### 模块和依赖

- 根 Maven 工程按 `domain -> application -> infrastructure/trigger -> boot` 的方向组织。
- `ooo1208-application` 不再依赖 Spring AI、Spring Web、Redis、JGit 或 PgVector。
- `ooo1208-trigger` 只依赖 application 的入站用例和 HTTP 依赖。
- Spring AI、PgVector、Redis、JGit 只出现在 infrastructure 或 boot。
- boot artifact 已命名为 `ooo1208-boot`；物理目录仍是 `ooo1208-app`，里面有既有本地数据和 IDEA 文件。

### 统一聊天

- `POST /api/v1/chat`：非流式聊天。
- `POST /api/v1/chat/stream`：流式聊天。
- 请求包含 `modelConfigId`、`message`，以及可选 `ragTag`、`webSearch`；`webSearch` 默认 `false`，开启时只调用服务端固定的搜索 Provider。
- `ChatApplicationService` 统一编排模型配置解析、RAG 检索、Prompt 组装和模型调用。
- `ChatGenerationPort` 隔离 Ollama 和 OpenAI Compatible 实现。
- DeepSeek 如果使用 OpenAI 兼容协议，复用 OpenAI Compatible 适配器，不新增 DeepSeekController。
- `ChatResponse` 返回统一内容、结束原因和 `modelConfigId`。

### RAG

- `RagKnowledgeApplicationService` 编排文件上传、Git 仓库分析和标签登记。
- PgVector 检索实现 `RagRetrieverPort`。
- Tika 解析、文本切分和 PgVector 写入实现 `RagDocumentStorePort`。
- Redis 标签列表实现 `RagTagStorePort`。
- JGit 读取实现 `GitRepositoryReaderPort`。
- Controller 不再直接引用 `PgVectorStore`、`TokenTextSplitter`、`TikaDocumentReader`、`RedissonClient` 或 JGit。

### 模型目录第一版

- Flyway 迁移创建 `provider_connection`、`model_binding`、`model_preset`。
- `JdbcModelConfigQueryAdapter` 按 `modelConfigId` 联表查询启用的预设、模型绑定和服务商连接。
- boot 启动时只在记录不存在时写入 Ollama 和 OpenAI Compatible 两个系统预置配置。
- `InMemoryModelConfigQueryAdapter` 保留为显式 `in-memory-model-config` profile 下的过渡实现。
- `ChatModelFactory` 使用解析结果中的 `baseUrl` 和 `credentialRef` 创建每次调用所需的客户端；凭证只从外部配置解析，不从数据库读取明文。
- 动态模型聊天和连接探针在创建客户端前通过 `ModelProviderEndpointValidator` 校验按 Provider 分组的 host、端口和私网策略；默认开发配置为 Ollama `192.168.23.100:11434`、OpenAI-compatible `api.openai.com:443`。
- OpenAI Compatible 的 base URL 会统一兼容带或不带 `/v1` 的写法，避免与 Spring AI 默认路径重复拼接。
- `POST /api/v1/model-connections/test` 只接受已登记且启用的 `modelConfigId`，Ollama 探测模型列表，OpenAI Compatible 探测 `/v1/models`，结果区分认证、网络、服务不可用和模型不存在。

### MCP 工具目录和联网安全第一版

- Flyway V2 创建 `mcp_server_connection`、`mcp_tool` 和 `model_preset_tool`，用模型预设绑定 MCP 工具白名单。
- `GET /api/v1/model-configs/{modelConfigId}/tools` 只返回已启用工具的稳定 ID、展示信息、只读和确认策略，不返回 endpoint、STDIO 命令或凭证引用。
- `POST /api/v1/model-configs/{modelConfigId}/tools/selection` 只接受稳定 `toolIds`，服务端按预设白名单校验、去除空白并保持选择顺序；未绑定或禁用工具直接返回 `400`。
- `POST /api/v1/model-configs/{modelConfigId}/tools/{toolId}/execute` 已接入第一版执行入口；执行前再次校验模型预设白名单，只允许只读且无需确认的工具，参数最多 32 个并限制嵌套/字符串/总量；按绑定的 `maxCalls` 对每个模型工具做进程内每分钟限流；默认关闭时返回 `503`。
- HTTP 入口在 JSON 反序列化前限制请求体（默认 128 KiB）；这只保护 MCP 执行入口，其他管理/认证边界仍待补齐。
- application 层新增 `McpToolCatalogQueryPort` 和只读目录用例；MCP SDK 和传输细节仍留在 infrastructure 边界之外。
- `OutboundUrlValidator` 统一限制后续 HTTP 出站访问：公网默认只允许 HTTP/HTTPS 的 80/443，并拒绝 userinfo、查询串、回环、私网、链路本地、CGNAT、元数据和组播地址；动态模型 Provider 另按 Provider 使用显式 managed host/port allowlist。
- `spring-ai-mcp` 只放在 infrastructure；第一版适配器使用 MCP Java SDK 的 SSE transport，每次调用创建并关闭短生命周期 client，限制配置的 host allowlist、超时、无重定向、解析后 JSON 深度/文档大小和逻辑输出长度；Streamable HTTP、STDIO、工具同步、审批、鉴权和持久化审计仍未完成。

### 固定联网搜索第一版

- application 层新增 `NetworkSearchPort`、查询/结果模型和 `SearchWebUseCase`，不携带 URL、API Key 或 Provider 细节。
- infrastructure 提供默认关闭的 Tavily-compatible 适配器，endpoint、凭证引用、超时和结果上限只从服务端配置读取。
- application 搜索用例增加固定 TTL、容量受限的进程内缓存；缓存键包含规范化查询和结果数量，Provider 异常不会写入缓存。
- `GET /api/v1/web-search?query=...&maxResults=...` 只接受查询文本和数量上限，结果只返回标题、链接、摘要，并强制标记 `untrusted=true`。
- Provider 请求前复用公网 URL 校验，JDK HTTP 客户端禁止自动跟随重定向；未配置 Provider 时返回明确的 `503`，不会让应用启动失败。

### 模型选择器目录第一版

- `GET /api/v1/model-configs` 返回启用模型的安全摘要（配置 ID、展示名、协议类型、能力和 RAG 标记）。
- 响应不包含 Base URL、上游模型 ID、credentialRef 或任何 API Key，前端可以用返回的 `modelConfigId` 继续查询 MCP 工具。

### 配置和可读性

- API Key、数据库和 Redis 连接支持环境变量覆盖。
- 所有核心 Java 文件都有类级中文注释。
- 新增 `docs/architecture/code-map.md`，用于快速定位每个模块和主要类。

## 验证记录

通过：

```text
mvn -DskipTests compile
mvn package -DskipTests
mvn clean package -DskipTests
```

2026-10-06 的 `compile` 已包含 MCP V2 迁移、工具目录、SSE 执行适配器、动态 Provider 出站策略和统一 URL 校验，7 个模块全部成功；最近的 application 18 个单元测试、infrastructure 21 个安全测试和 trigger 5 个请求体过滤器测试通过；随后仍需在真实 PostgreSQL/MCP Provider 上执行迁移和连接冒烟。

构建产物：

```text
ooo1208-app/target/ai-rag-knowledge.jar
```

启动冒烟测试曾执行到 Spring 容器初始化阶段，但当前环境无法连接 Redis：

```text
192.168.23.100:16379
```

因此真实的 HTTP 聊天和 RAG 请求还需要在 PostgreSQL、Redis、Ollama 或 OpenAI Compatible 服务可用的环境中验证。现有 boot POM 默认跳过测试，`JGitTest` 也是依赖真实外部服务的手工集成测试样例。

## 当前明确未完成

这些功能不能被误认为已经完成：

1. 管理员新增、编辑、启用、禁用模型连接，并把连接测试纳入启用前流程。
2. 用户自定义 API Key、密钥加密、Secret 引用、轮换和权限控制。
3. 按服务商自动同步模型列表。
4. 前端按服务商分组展示模型、搜索、收藏和最近使用。
5. 连接测试、客户端缓存/生命周期管理和更细的上游错误分类。
6. 会话保存实际调用的模型配置快照。
7. 统一错误码、超时、重试、fallback、限流、用量和成本统计。
8. EmbeddingProfile 和知识库级向量模型版本管理。
9. 固定 provider 联网搜索的健康检查、权限审计和更细结果清洗（基础搜索、不可信上下文和受限缓存第一版已完成）。
10. MCP Streamable HTTP/STDIO 的受控连接、工具同步、执行审批、鉴权、持久化审计和跨实例配额；SSE 只读执行已有默认关闭的第一版适配器，当前仅在显式配置 host allowlist 后允许开启，限流仅为单进程保护。

## 下一步执行顺序

### Step 1：模型目录数据库化（第一版已完成）

- 已完成三张表的 Flyway 初始迁移和 `ModelConfigQueryPort` 的 JDBC 实现。
- 当前只写入系统预置配置，不提供管理员 CRUD。
- 连接的 `credentialRef` 只作为引用保存，不保存明文 API Key。

### Step 2：连接测试和动态客户端工厂（第一版已完成）

- 已新增 `ChatModelFactory`，根据解析后的 `providerType`、`baseUrl` 和 `credentialRef` 创建 Ollama 或 OpenAI Compatible 客户端；`upstreamModelId` 仍由请求 Prompt 的 options 使用。
- 凭证引用当前支持 `config:` 方案，实际值从 Spring `Environment` 获取，数据库和日志不保存明文 Key。
- 已新增 `POST /api/v1/model-connections/test`，只测试已登记模型配置，不接受任意外部 URL。
- 已实现认证失败、网络失败、模型不存在、服务商暂时不可用和探针不支持的结果分类。
- 待补管理员权限、禁用连接测试、统一异常 HTTP 映射，以及在启用模型前强制完成测试。
- 后续再增加按连接维度的客户端缓存和失效策略，避免在每次请求中重复创建客户端。

### Step 3：提供模型目录和 MCP 选择 API（第一版已完成）

- 管理员模型配置 CRUD 仍待实现；当前只由启动种子和数据库迁移提供系统预置目录。
- 已增加用户可见模型查询接口，只返回已启用模型的安全摘要；真正的用户权限过滤仍待接入。
- 前端选择器消费 `modelConfigId`，不消费 API Key、Base URL 或上游模型 ID。
- 已增加 `GET /api/v1/model-configs` 安全模型摘要接口。
- 已增加按 `modelConfigId` 查询、校验 MCP 工具白名单和执行只读工具的接口；`/tools/selection` 是无状态预检，不会替用户持久化绑定；执行默认关闭，当前只支持登记服务器的 SSE transport。

### Step 4：接入受控联网搜索和模型发现（联网搜索第一版已完成）

- 先固定一个服务端配置的 `SEARCH_ONLY` provider，不允许聊天请求传任意 URL。
- 搜索请求和所有重定向都要通过 `OutboundUrlValidator`，增加超时、响应大小和域名白名单。
- 已提供默认关闭的 Tavily-compatible `GET /api/v1/web-search`，并限制超时、响应字节数、重定向和进程内缓存；仍待增加固定 provider 健康检查、鉴权和权限审计。
- Ollama 使用本地模型列表接口；OpenAI Compatible 先尝试 `/models`，不支持时允许手工录入。
- 同步结果写入缓存和数据库，失败不能删除上一次可用模型。

### Step 5：接入 MCP 传输和执行策略（SSE 只读第一版已完成）

- 已引入与当前 Spring Boot 版本兼容的 `spring-ai-mcp` 底层依赖；第一版只支持 SSE，不引入会直接升级 Boot 的 starter。
- 只允许数据库中的稳定 `serverId`、`toolId`，禁止用户传 endpoint、命令或凭证。
- 已增加执行用例和 `POST /api/v1/model-configs/{modelConfigId}/tools/{toolId}/execute`；默认只读且无需确认的工具，工具输出标记为不可信内容。
- 仍需补齐 Streamable HTTP、STDIO 命令白名单、写操作显式确认、持久化调用审计、跨实例配额、工具同步和权限控制；当前限流只提供单进程每分钟保护。

### Step 6：最后做 BYOK 和运营能力

- 先有用户、租户、权限、审计、限流和用量记录，再开放用户自己的 API Key。
- 在此之前不开放任意用户输入自定义 URL，避免 SSRF 和内网访问风险。

## 新会话接手规则

新的开发会话必须先读取本文件、`architecture-decisions.md` 和 `outbound-network-security.md`，然后：

1. 检查 `git status` 和当前构建是否通过。
2. 选定一个未完成步骤，不跨阶段添加无关功能。
3. 先补端口、命令、领域模型和验收条件，再实现基础设施和 Controller。
4. 完成后执行最小必要验证。
5. 更新本文件的“已完成”“验证记录”和“下一步执行顺序”。

如果新需求与本文件冲突，先记录决策和原因，再改代码。
