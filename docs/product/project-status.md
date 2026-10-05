# 项目状态与交接记录

最后更新：2026-10-03

## 当前结论

Phase 0 和 Phase 1 的核心代码已经落地：聊天和 RAG 都有统一的 application 用例，HTTP 层不再直接编排模型或向量库，Ollama 和 OpenAI Compatible 通过出站适配器接入。

这一版是后续动态模型平台的架构基线，不是最终的动态配置产品。模型配置目前仍由 `InMemoryModelConfigQueryAdapter` 提供两个系统预置项。

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
- 请求只包含 `modelConfigId`、`message` 和可选 `ragTag`。
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

1. 数据库保存 `ProviderConnection`、`ModelBinding`、`ModelPreset`。
2. 管理员新增、编辑、启用、禁用和测试模型连接。
3. 用户自定义 API Key、密钥加密、Secret 引用、轮换和权限控制。
4. 按服务商自动同步模型列表。
5. 前端按服务商分组展示模型、搜索、收藏和最近使用。
6. 按不同 baseUrl 和 credentialRef 动态创建模型客户端。
7. 会话保存实际调用的模型配置快照。
8. 统一错误码、超时、重试、fallback、限流、用量和成本统计。
9. EmbeddingProfile 和知识库级向量模型版本管理。

## 下一步执行顺序

### Step 1：先替换模型配置查询实现

- 设计数据库表：`provider_connection`、`model_binding`、`model_preset`。
- 先只支持系统管理员配置，不急着做普通用户 BYOK。
- `ModelConfigQueryPort` 保持不变，把 `InMemoryModelConfigQueryAdapter` 替换成数据库适配器。
- API Key 只保存加密值或 Secret 引用，任何查询接口只返回脱敏信息。

### Step 2：实现连接测试和动态客户端工厂

- 新增 `ModelProviderClientFactory` 或等价的 infrastructure 组件。
- 根据解析后的 `providerType`、`baseUrl`、`credentialRef` 和 `upstreamModelId` 创建调用客户端。
- 连接测试必须先于启用模型。
- 失败要区分认证失败、网络失败、模型不存在和服务商暂时不可用。

### Step 3：提供模型目录 API

- 增加管理员模型配置接口。
- 增加用户可见模型查询接口，只返回已启用且有权限使用的模型。
- 前端选择器消费 `modelConfigId`，不消费 API Key、Base URL 或上游模型 ID。

### Step 4：再做模型发现

- Ollama 使用本地模型列表接口。
- OpenAI Compatible 先尝试 `/models`，不支持时允许手工录入。
- 同步结果写入缓存和数据库，失败不能删除上一次可用模型。

### Step 5：最后做 BYOK 和运营能力

- 先有用户、租户、权限、审计、限流和用量记录，再开放用户自己的 API Key。
- 在此之前不开放任意用户输入自定义 URL，避免 SSRF 和内网访问风险。

## 新会话接手规则

新的开发会话必须先读取本文件和 `architecture-decisions.md`，然后：

1. 检查 `git status` 和当前构建是否通过。
2. 选定一个未完成步骤，不跨阶段添加无关功能。
3. 先补端口、命令、领域模型和验收条件，再实现基础设施和 Controller。
4. 完成后执行最小必要验证。
5. 更新本文件的“已完成”“验证记录”和“下一步执行顺序”。

如果新需求与本文件冲突，先记录决策和原因，再改代码。
