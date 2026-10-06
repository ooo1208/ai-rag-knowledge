# 开发行动日志

这不是 Git commit 替代品，而是给后续会话看的“为什么这样改”和“改完验证了什么”。

## 2026-10-05：模型目录数据库化第一版

### 行动

- 引入 Flyway，并新增 `provider_connection`、`model_binding`、`model_preset` 初始迁移。
- 新增 `JdbcModelConfigQueryAdapter`，保持 `ModelConfigQueryPort` 不变。
- 将两个系统预置模型写入空数据库；使用 `ON CONFLICT DO NOTHING` 保留后续管理员修改。
- 将 `InMemoryModelConfigQueryAdapter` 限制为显式 `in-memory-model-config` profile。
- 为已有非空 PostgreSQL schema 配置 baseline version 0，避免因为 PgVector 表已存在而跳过 V1 迁移。

### 结果

- `mvn clean package -DskipTests` 通过，7 个模块全部成功。
- 可执行 JAR 中包含 Flyway 依赖、迁移脚本和新的数据库查询适配器。
- 真实数据库迁移和聊天请求仍需要在 PostgreSQL、Redis、Ollama 或 OpenAI Compatible 服务可用的环境中验证。

### 下一次行动

实现管理员连接测试，并为动态客户端补充缓存、失效和统一错误分类。

## 2026-10-05：动态模型客户端第一版

### 行动

- 新增 infrastructure 层 `ChatModelFactory`，根据 `ResolvedModelConfig` 创建 Ollama 或 OpenAI Compatible 客户端。
- 聊天适配器不再注入固定的启动时聊天模型 Bean，而是按请求使用数据库解析出的 `baseUrl` 和凭证引用。
- `credentialRef` 当前支持 `config:` 引用，实际密钥从 Spring `Environment` 获取，数据库只保存引用。

### 结果

- `mvn -pl ooo1208-infrastructure -am package -DskipTests` 通过。
- 动态客户端链路已经接通，但真实服务商连接测试、统一错误分类和客户端缓存尚未实现。

### 下一次行动

增加管理员连接测试用例和按连接配置缓存客户端的失效策略。

## 2026-10-06：模型连接测试第一版

### 行动

- 新增 `TestModelConnectionUseCase` 和 `ModelConnectionTestPort`，复用聊天的模型目录解析链路。
- 新增 `POST /api/v1/model-connections/test`，只接受已有 `modelConfigId`，不接受任意 URL 或明文 API Key。
- Ollama 使用模型列表接口，OpenAI Compatible 使用 `/v1/models`，并统一返回连接状态分类。

### 结果

- 连接测试不会调用模型生成内容，只进行轻量探针。
- `mvn -pl ooo1208-app -am package -DskipTests` 通过。
- 管理员认证、禁用连接测试、启用前强制校验和统一 HTTP 异常映射仍未完成。

### 下一次行动

增加管理员模型目录 CRUD 和权限边界，再把连接测试接入启用流程。

## 2026-10-06：MCP 工具目录和联网出站边界第一版

### 行动

- 新增 Flyway V2：`mcp_server_connection`、`mcp_tool`、`model_preset_tool`，以模型预设绑定 MCP 工具白名单。
- 新增 application 端口和只读用例，暴露 `GET /api/v1/model-configs/{modelConfigId}/tools`。
- 工具查询同时检查模型预设、绑定、工具和服务器的启用状态；返回值不包含 endpoint、STDIO 命令或凭证引用。
- 新增 `OutboundUrlValidator`，为公网 HTTP 出站统一拒绝 userinfo、查询串、危险端口及私网/回环/链路本地/元数据地址。

### 结果

- `mvn -DskipTests compile` 通过，7 个模块成功。
- 当前只完成安全目录和边界，尚未接入真实 MCP transport、工具执行 loop 或固定联网搜索 provider。

### 下一次行动

先实现固定 provider 的只读联网搜索端口，再引入与当前 Spring AI 版本匹配的 MCP client 适配器；两者都必须复用出站校验、超时、响应大小和审计策略。

## 2026-10-06：固定联网搜索 Provider 第一版

### 行动

- 新增 application `NetworkSearchPort`、`NetworkSearchQuery`、`NetworkSearchResult` 和 `SearchWebUseCase`。
- 新增默认关闭的 Tavily-compatible HTTP 适配器；endpoint、credentialRef、超时和最大结果数只从服务端配置读取。
- 新增 `GET /api/v1/web-search`，请求方不能传 URL、API Key 或抓取选项；结果统一带 `untrusted=true`。
- 使用 `OutboundUrlValidator` 校验 Provider 地址，并使用 JDK HTTP 客户端禁止自动跟随重定向。

### 结果

- 默认配置不会对外发起联网请求，未启用时接口返回 `503`；启用后才解析凭证并访问固定 Provider。
- `mvn -DskipTests compile` 通过；真实 Tavily-compatible 服务仍需配置 `WEB_SEARCH_ENABLED=true` 和 `TAVILY_API_KEY` 后验证。

### 下一次行动

补充响应字节级限制、Provider 健康检查和权限审计，再将搜索结果作为明确标记的不可信上下文接入聊天编排；随后评估 MCP transport 依赖和工具执行审批。

## 2026-10-03：统一聊天与 RAG 架构

### 行动

- 将原 API 模块整理为 application 层，并新增 infrastructure 层。
- 将 provider-specific Controller 删除，新增 `/api/v1/chat` 和 `/api/v1/chat/stream`。
- 新增 `ChatApplicationService`，统一编排模型配置、RAG、Prompt 和模型调用。
- 用 `ChatGenerationPort` 隔离 Ollama 与 OpenAI Compatible。
- 将 RAG 的 PgVector、Tika、Redis、JGit 操作移出 Controller，改成 application 端口和 infrastructure 适配器。
- 将 HTTP `Response` 移到 trigger 层，避免 application 依赖 HTTP 返回包装。
- 将模型、数据库、Redis 的敏感连接信息改为环境变量优先。
- 为核心 Java 文件增加职责注释，并新增代码结构地图。

### 结果

- `mvn -DskipTests compile` 通过。
- `mvn package -DskipTests` 通过。
- `mvn clean package -DskipTests` 通过。
- 启动冒烟测试因开发环境 Redis `192.168.23.100:16379` 不可达而停止，外部依赖恢复后需要重新验证真实接口。

### 有意保留的临时实现

- `InMemoryModelConfigQueryAdapter` 只提供 `ollama-local` 和 `openai-compatible` 两个预置配置。
- OpenAI Compatible 和 Ollama 仍通过启动时 Bean 创建，尚未支持每个用户/连接独立的动态客户端。
- 现有 Maven 配置默认跳过测试，真实 Git 导入测试仍依赖外部服务。

## 2026-10-03：文档和参考体系

### 行动

- 为 `docs/product` 增加入口 README、项目状态、架构决策、开源参考和行动日志。
- 将 Dify、Open WebUI、LiteLLM、LangChain4j 和 Spring AI 的可借鉴思想记录下来。
- 明确“借鉴模式优先，直接复制代码必须先审许可证”的规则。

### 下一次行动

从 `project-status.md` 的 Step 1 开始：设计 `provider_connection`、`model_binding`、`model_preset` 数据模型和迁移脚本，然后用数据库适配器替换内存模型配置查询。
