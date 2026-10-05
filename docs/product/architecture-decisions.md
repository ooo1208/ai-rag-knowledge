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

启动时只补齐不存在的系统预置配置，不覆盖数据库中已有的连接、模型和预设。聊天适配器通过 `ChatModelFactory` 按本次解析结果创建客户端；启动时的模型 Bean 仍保留给 Embedding 和兼容场景使用。连接测试、客户端缓存和失效策略属于后续阶段。

OpenAI Compatible 的 `baseUrl` 允许历史配置带 `/v1`，基础设施层在构造 Spring AI `OpenAiApi` 时统一去掉末尾版本路径，使用 SDK 默认的 `/v1/chat/completions` 和 `/v1/embeddings` 路径，避免重复拼接。
