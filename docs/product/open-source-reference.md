# 高星开源项目参考与借鉴边界

记录时间：2026-10-03

以下 star 数是官方 GitHub 页面在记录时间的快照，会变化；它们用于判断项目成熟度和设计参考价值，不代表要照搬全部功能。

## 1. Dify

官方仓库：[langgenius/dify](https://github.com/langgenius/dify)（页面显示约 157.8k stars）。

官方 README 把模型支持、Prompt、RAG Pipeline、Workflow、API 和 LLMOps 放在同一个产品体系里。我们借鉴：

- 模型供应商、模型目录和应用配置分层，不把“一个模型名称”当成全部配置。
- RAG 从文档导入、检索到回答形成完整流程，而不是把检索代码散落在 Controller。
- 先定义产品能力和管理面，再实现具体供应商。
- 后续加入调用日志、评估和可观测性，而不是只关注“请求能不能返回”。

我们不照搬：

- Dify 的整套工作流、插件、Agent 和多租户实现。
- Dify 特有的许可证条款和代码，除非逐个确认许可证和版权要求。

## 2. Open WebUI

官方仓库：[open-webui/open-webui](https://github.com/open-webui/open-webui)（页面显示约 153.9k stars）。

官方 README 强调 Ollama 与 OpenAI Compatible API 的供应商无关体验，同时具备模型包装、知识库、权限和用量分析。我们借鉴：

- 用户看到的是可用模型和模型预设，不应该看到底层连接细节。
- 本地 Ollama 和云端 OpenAI Compatible 应该在同一个产品入口中工作。
- 模型配置应支持系统级默认值、用户可见性和权限控制。
- RAG、模型、用户权限和运营统计最终要形成闭环。

我们不照搬：

- Open WebUI 的前端、插件和全量功能范围。
- 任何没有完成许可证审查的实现代码。

## 3. LiteLLM

官方仓库：[BerriAI/litellm](https://github.com/BerriAI/litellm)（页面显示支持 100+ LLM，并提供统一 OpenAI 格式、虚拟 Key、成本统计、guardrails、负载均衡和管理面）。

我们借鉴：

- 对外统一协议，对内由 Provider Adapter 处理供应商差异。
- 连接、认证、路由、重试、fallback、成本和日志是独立能力，不要塞进一个 ChatService。
- 后续模型配置需要记录调用来源、用户、项目和成本。
- 连接测试、模型可用性和管理面必须先于开放 BYOK。

我们不照搬：

- LiteLLM 作为独立 AI Gateway 的全部实现。
- 在当前项目还没有权限、配额和观测基础时，直接添加复杂路由。

## 4. LangChain4j

官方仓库：[langchain4j/langchain4j](https://github.com/langchain4j/langchain4j)（页面显示约 13.2k stars）。

官方 README 的核心思想是：用统一 API 隔离不同模型供应商和向量存储，并为每个抽象提供多个实现。我们借鉴：

- 用接口和强类型对象定义模型调用、Embedding、向量存储和 RAG 边界。
- Java 代码使用 POJO、接口和依赖注入，不把 SDK 类型扩散到业务层。
- 先设计可替换抽象，再增加具体集成。

## 5. Spring AI

官方仓库：[spring-projects/spring-ai](https://github.com/spring-projects/spring-ai)（页面显示约 9.5k stars）。

官方 README 强调 Spring 友好的强类型抽象、同步和流式模型 API、向量存储、ETL、RAG、观测和可插拔实现。我们借鉴：

- Spring AI 只放在 infrastructure，用它连接模型和向量库。
- application 只依赖本项目定义的端口，避免被某个 Spring AI 版本锁死。
- 使用它已有的 `Document`、`SearchRequest`、模型选项和向量存储适配器，减少重复造轮子。

## 本项目的取舍

### 联网搜索与 MCP 协议

当前联网搜索只实现 Tavily-compatible 的 HTTP 请求/响应形状，没有复制第三方 SDK 或代码；
endpoint 和密钥由服务端配置管理，默认关闭。MCP 目前只落地数据库工具目录和选择契约，
尚未引入具体 MCP client 依赖，待确认 Spring AI 版本和传输安全边界后再接入。

我们不追求一次性复制这些项目的全部能力。当前只保留最小闭环：

```text
稳定 modelConfigId
  -> 统一聊天请求
  -> 可替换 Provider Adapter
  -> 可选 RAG
  -> 可追踪结果
```

等数据库配置、权限、密钥和调用记录具备后，再增加模型发现、BYOK、路由和成本统计。这样每个阶段都能运行和验收，不会因为模仿大项目而把一个个人项目变成不可维护的平台。
