# 开发行动日志

这不是 Git commit 替代品，而是给后续会话看的“为什么这样改”和“改完验证了什么”。

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
