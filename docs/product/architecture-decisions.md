# 架构决策记录

这份文件记录已经做出的约束。后续新增功能应优先适配这些决策，而不是为了省一个类把边界重新打乱。

## ADR-001：采用六边形依赖方向

状态：已采用

依赖方向固定为：

```text
domain <- application <- infrastructure
                         trigger
                         boot
```

application 定义入站端口和出站端口。infrastructure 实现出站端口，trigger 调用入站端口，boot 负责装配。application 不得导入 Spring AI、Spring Web、数据库驱动、Redis 或 JGit。

## ADR-002：对外使用统一聊天接口

状态：已采用

统一入口：

```text
POST /api/v1/chat
POST /api/v1/chat/stream
```

请求使用 `modelConfigId`，不能把供应商、API Key、Base URL 或具体 SDK 客户端暴露给前端。服务商差异只在 `ChatGenerationPort` 的适配器中处理。

## ADR-003：`modelConfigId` 是产品 ID，不是上游模型名

状态：已采用

`modelConfigId` 必须稳定、可追踪、与第三方模型 ID 解耦。当前 `ollama-local` 和 `openai-compatible` 只是 Phase 0 预置值。未来模型改名、供应商迁移或配置参数变化时，不能直接用上游模型名替换这个 ID。

调用配置至少分开表达：

- `ProviderConnection`：怎么连接服务商。
- `ModelBinding`：连接下的实际模型。
- `ModelPreset`：用户可选择的温度、最大输出、系统提示词和 RAG 开关。
- `ResolvedModelConfig`：本次请求解析后的临时调用信息。

## ADR-004：DeepSeek 复用 OpenAI Compatible 协议

状态：已采用

只要服务商遵循 OpenAI Chat Completions 兼容协议，就使用同一个适配器。不要为 DeepSeek、Moonshot、SiliconFlow 等每家服务商复制一个 Controller 和 Service。

只有出现无法通过协议适配的真实差异时，才增加新的 `ProviderType` 和 adapter。

## ADR-005：RAG 检索、Prompt 组装和模型调用分离

状态：已采用

固定顺序：

```text
解析模型配置
  -> RAG 检索
  -> Prompt 组装
  -> 模型调用
```

RAG 检索不生成 Prompt，PromptAssembler 不访问向量库，模型适配器不决定是否检索。

## ADR-006：聊天模型和 Embedding 模型分开

状态：已采用，数据库部分待实现

聊天模型可以切换，不能因此改变已有知识库的 Embedding 模型、维度和版本。Embedding 配置属于知识库索引契约，变更时必须重建或迁移向量。

## ADR-007：凭证不进入请求对象和模型展示对象

状态：已采用

前端不能发送 API Key。`ModelConfig` 和模型选择器不能返回明文密钥。后续使用加密存储或外部 Secret 引用，运行时只在 infrastructure 适配器内解析。

## ADR-008：当前 InMemory 配置是过渡实现

状态：明确标记为临时

`InMemoryModelConfigQueryAdapter` 只用于先验证统一调用链。当前默认实现已经替换为 PostgreSQL JDBC 适配器，内存实现仅保留在显式 profile 下，且 ChatApplicationService 不感知数据来源变化。

## ADR-009：不为了“看起来支持动态”而提前做任意 URL

状态：已采用

开放用户输入任意 baseUrl 会引入 SSRF、内网探测、凭证泄漏和计费归属问题。先实现管理员连接、连接测试、权限和审计，再考虑 BYOK。

## ADR-010：优先借鉴模式，不直接复制不清楚来源的代码

状态：已采用

可以借鉴高星项目的模块划分、协议设计和产品流程；直接复制代码前必须检查许可证、版权声明和依赖兼容性。Dify、Open WebUI 和 LiteLLM 的代码不能因为开源就默认可以直接搬进本项目。

## ADR-011：模型目录以 PostgreSQL 为数据源

状态：已采用

`provider_connection`、`model_binding` 和 `model_preset` 通过 Flyway 管理初始表结构，聊天应用继续依赖 `ModelConfigQueryPort`，由 infrastructure 的 JDBC 适配器查询启用配置。`InMemoryModelConfigQueryAdapter` 只作为显式 profile 下的过渡实现。

启动时只补齐不存在的系统预置配置，不覆盖数据库中已有的连接、模型和预设。聊天适配器通过 `ChatModelFactory` 按本次解析结果创建客户端；启动时的模型 Bean 仍保留给 Embedding 和兼容场景使用。连接测试第一版通过已登记 `modelConfigId` 的轻量探针提供，管理员权限、客户端缓存和失效策略属于后续阶段。

OpenAI Compatible 的 `baseUrl` 允许历史配置带 `/v1`，基础设施层在构造 Spring AI `OpenAiApi` 时统一去掉末尾版本路径，使用 SDK 默认的 `/v1/chat/completions` 和 `/v1/embeddings` 路径，避免重复拼接。

## ADR-012：联网和 MCP 先做服务端目录与出站安全边界

状态：已采用，SSE 只读执行第一版已实现

联网搜索和远程 MCP 都会让服务端代替调用方访问外部地址。第一步不开放请求方传任意 URL、API Key、STDIO 命令或工具参数，而是：

- 用 `mcp_server_connection`、`mcp_tool` 和 `model_preset_tool` 保存管理员登记的连接、工具和模型白名单；
- 通过 `GET /api/v1/model-configs/{modelConfigId}/tools` 只返回可选择的安全摘要；
- 通过 `POST /api/v1/model-configs/{modelConfigId}/tools/selection` 只预检已绑定的 `toolId`，不接受 endpoint、命令或凭证；
- 通过 `GET /api/v1/model-configs` 暴露模型选择器摘要，前端只消费稳定 `modelConfigId`，不直接消费 Provider 连接细节；
- 基础设施层统一调用 `OutboundUrlValidator`，公网默认只允许 HTTP/HTTPS 的 80/443，并拒绝解析到回环、私网、链路本地、CGNAT、元数据、保留或组播地址；
- 固定搜索和 MCP 执行通过基础设施适配器接入；MCP 第一版只使用 `spring-ai-mcp` 的 SSE transport，所有工具输出标记为不可信内容，所有出站连接仍需超时、无重定向和 URL 校验。
- MCP 执行默认关闭，只允许只读且无需确认的工具；Streamable HTTP、STDIO、写操作审批、工具同步和审计后续单独设计。

这样可以先稳定前端选择契约和数据库白名单，再引入协议 SDK，不会把任意网络访问能力误认为已经安全可用。

## ADR-013：联网搜索只允许固定 Provider 的只读入口

状态：已采用，第一版已实现

联网能力先落成 `NetworkSearchPort` 和 Tavily-compatible 基础设施适配器，默认关闭。HTTP 入口只接收查询文本和结果数量，Provider endpoint、凭证引用、超时和上限由服务端配置管理；搜索结果返回 `untrusted=true`，不能直接当作系统指令或 MCP 参数。

出站请求在发送前通过 `OutboundUrlValidator`，JDK HTTP 客户端不自动跟随重定向，响应体有字节级上限。application 层只增加固定 TTL、容量受限的进程内缓存，不缓存 Provider 异常；Provider 健康检查和权限审计属于后续工作。这样“能联网搜索”与“允许任意 URL 抓取”保持明确区分。

聊天请求的 `webSearch` 是显式开关，默认关闭；开启后只把服务端搜索结果作为不可信上下文交给 PromptAssembler，不能让模型直接访问任意 URL，也不会把搜索结果当作系统指令。
