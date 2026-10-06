# 项目状态与交接记录

最后更新：2026-10-05

## 当前结论

Phase 0 和 Phase 1 的核心代码已经落地：聊天和 RAG 都有统一的 application 用例，HTTP 层不再直接编排模型或向量库，Ollama 和 OpenAI Compatible 通过出站适配器接入。

这一版已经完成模型目录数据库化、动态客户端和连接测试的第一版：Flyway 创建三张模型目录表，JDBC 适配器读取启用的模型预设，启动时补齐两个系统预置项，聊天适配器会按解析结果创建对应的 Ollama 或 OpenAI Compatible 客户端，并提供基于已登记 `modelConfigId` 的轻量连接探针。它仍不是最终的动态配置产品，管理员 API、权限控制和客户端缓存策略还没有完成。

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

### 模型目录第一版

- Flyway 迁移创建 `provider_connection`、`model_binding`、`model_preset`。
- `JdbcModelConfigQueryAdapter` 按 `modelConfigId` 联表查询启用的预设、模型绑定和服务商连接。
- boot 启动时只在记录不存在时写入 Ollama 和 OpenAI Compatible 两个系统预置配置。
- `InMemoryModelConfigQueryAdapter` 保留为显式 `in-memory-model-config` profile 下的过渡实现。
- `ChatModelFactory` 使用解析结果中的 `baseUrl` 和 `credentialRef` 创建每次调用所需的客户端；凭证只从外部配置解析，不从数据库读取明文。
- OpenAI Compatible 的 base URL 会统一兼容带或不带 `/v1` 的写法，避免与 Spring AI 默认路径重复拼接。
- `POST /api/v1/model-connections/test` 只接受已登记且启用的 `modelConfigId`，Ollama 探测模型列表，OpenAI Compatible 探测 `/v1/models`，结果区分认证、网络、服务不可用和模型不存在。

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

2026-10-05 的 clean package 已包含 Flyway 模型目录迁移，7 个模块全部成功。

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
