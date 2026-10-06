# Phase 0：统一模型调用设计契约

> 实现状态：本契约已经落到当前代码。统一聊天入口、application 端口、Provider 适配器、RAG 边界和模型目录第一版已经完成；管理员配置、用户自定义 API Key 和模型自动发现仍属于后续阶段。

## 目标

为后续代码重构冻结统一的聊天入口、核心对象职责、模型配置关系和流式响应格式。

本阶段只确定契约，不实现数据库、前端模型选择器或用户自定义 API Key。

## 当前问题

当前接口按服务商拆分：

```text
/api/v1/ollama/generate
/api/v1/ollama/generate_stream
/api/v1/ollama/generate_stream_rag
/api/v1/openai/generate
/api/v1/openai/generate_stream
/api/v1/openai/generate_stream_rag
```

这导致 Controller 重复编排 RAG 检索、Prompt 组装和流式调用，前端也必须知道具体服务商。

## 目标调用链

```text
ChatController
    ↓
ChatService
    ├── ModelConfigResolver
    ├── RagRetriever（启用 RAG 时）
    ├── PromptAssembler
    └── ChatModelProviderRegistry
            ↓
       ChatModelProvider
            ├── OllamaChatModelProvider
            └── OpenAiCompatibleChatModelProvider
```

## 核心对象

### ProviderConnection

表示如何访问一个服务商。

```text
id
providerType
name
baseUrl
credentialRef
enabled
status
```

不保存明文 API Key，不保存具体聊天参数。

### ModelBinding

表示服务商连接下的一个具体模型。

```text
id
connectionId
upstreamModelId
displayName
capabilities
enabled
```

`upstreamModelId` 是发送给第三方服务商的模型 ID，`displayName` 是产品展示名称。

### ModelConfig

表示用户可以选择的模型使用方案。

```text
id
name
modelBindingId
temperature
maxTokens
systemPrompt
ragEnabled
enabled
```

不保存 `apiKey`、`baseUrl` 或具体 Provider 客户端对象。

### ResolvedModelConfig

表示后端根据 `modelConfigId` 解析出来的本次调用配置，仅在请求期间使用。

```text
modelConfigId
providerType
baseUrl
upstreamModelId
credentialRef
temperature
maxTokens
systemPrompt
ragEnabled
capabilities
```

它不应被返回给前端，也不应被完整写入日志。

### EmbeddingProfile

表示知识库使用的向量模型。

聊天模型和 Embedding 模型独立管理。Embedding 变化时需要明确重建索引，不允许无提示地混用不同模型生成的向量。

## 统一请求

### 普通聊天

```http
POST /api/v1/chat
Content-Type: application/json
```

```json
{
  "modelConfigId": "mc_001",
  "message": "你好",
  "conversationId": "conv_001"
}
```

### RAG 聊天

```json
{
  "modelConfigId": "mc_001",
  "message": "项目如何启动？",
  "ragTag": "project-a",
  "conversationId": "conv_001"
}
```

### 流式聊天

```http
POST /api/v1/chat/stream
Content-Type: application/json
```

请求体与普通聊天相同，响应使用统一的流式事件格式。

## 统一响应

### ChatResponse

```json
{
  "content": "完整回答",
  "finishReason": "STOP",
  "modelConfigId": "mc_001"
}
```

### ChatChunk

流式响应中的一个增量片段：

```json
{
  "content": "项目使用 Spring Boot",
  "finishReason": null
}
```

结束片段：

```json
{
  "content": "",
  "finishReason": "STOP"
}
```

Provider 可以返回不同的上游格式，但必须转换成统一的 `ChatResponse` 或 `ChatChunk`。

## 组件职责

### ChatController

- 接收 HTTP 请求；
- 校验基础参数；
- 调用 `ChatService`；
- 返回普通或流式 HTTP 响应。

不负责服务商选择、RAG 检索和 Prompt 组装。

### ChatService

- 编排一次聊天请求的完整流程；
- 调用配置解析、RAG 检索、Prompt 组装和 Provider；
- 不直接依赖 `OllamaChatModel` 或 `OpenAiChatModel`。

### ModelConfigResolver

- 根据 `modelConfigId` 查找配置；
- 校验配置是否存在、启用和可访问；
- 解析出 `ResolvedModelConfig`。

### RagRetriever

- 根据用户问题和 `ragTag` 构造检索请求；
- 调用向量库；
- 返回 `Document` 列表。

### PromptAssembler

- 将系统提示词、检索内容、历史消息和用户问题组装成统一 `ChatPrompt`；
- 不调用模型和向量库。

### ChatModelProvider

- 接收统一 `ChatPrompt` 和 `ResolvedModelConfig`；
- 调用具体服务商；
- 将上游响应转换为统一响应。

## 当前版本的决策

1. 前端最终选择 `ModelConfig`，不直接选择 API Key、Base URL 或 Provider 客户端。
2. 对外聊天接口不按服务商拆分。
3. DeepSeek 如果使用 OpenAI 兼容协议，复用 `OpenAiCompatibleChatModelProvider`，不新增 `DeepSeekController`。
4. `modelConfigId` 是系统内部稳定标识，前端不直接依赖第三方模型名称作为配置身份。
5. API Key 通过 `credentialRef` 间接取得，不放进 `ModelConfig`。
6. 聊天模型与 Embedding 模型分开管理。

## Phase 0 验收标准

- 能用一条统一调用链解释 Ollama 和 DeepSeek 的调用过程；
- 能区分 ProviderConnection、ModelBinding、ModelConfig 和 EmbeddingProfile；
- 能说明 `modelConfigId` 如何解析成实际模型调用配置；
- 能说明 RAG 检索发生在模型调用之前；
- 能说明 `ChatChunk` 为什么是增量片段；
- 后续代码可以根据本契约实现，而不需要重新讨论核心边界。
