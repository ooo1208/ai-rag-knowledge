# AI RAG Knowledge 产品文档中心

这个目录是项目的产品、架构和开发交接记录。每次开始新的开发会话，先阅读：

1. `project-status.md`：当前已经完成什么、现在真正卡在哪里。
2. `architecture-decisions.md`：哪些边界已经确定，不能随意推翻。
3. `dynamic-model-platform-roadmap.md`：产品从固定配置走向动态模型平台的阶段路线。
4. `phase-0-design.md`：统一聊天接口和核心对象的契约。
5. `open-source-reference.md`：参考的高星开源项目，以及哪些思想适合本项目。
6. `../architecture/code-map.md`：代码目录和调用链的快速地图。

## 文档维护规则

- 完成一个可验证的代码切片后，更新 `project-status.md` 的完成项、验证结果和遗留问题。
- 做出会影响模块边界、接口、数据模型或安全策略的决定后，追加到 `architecture-decisions.md`。
- 改变阶段顺序或验收标准时，更新 `dynamic-model-platform-roadmap.md`，不能只改代码不改路线图。
- 引入新的外部项目、框架或代码片段时，先更新 `open-source-reference.md`，记录来源、许可证和实际借鉴点。
- 每次开发只推进一个可以运行和验证的垂直切片；不要同时改数据库、前端、权限和路由而没有验收证据。
- 文档中的“已完成”必须有代码位置或命令验证作为依据，不能把计划写成事实。

## 当前项目一句话定义

这是一个基于 Spring Boot、Spring AI、PgVector 和 Redis 的 RAG 应用，正在从“配置文件固定模型”升级为“统一聊天接口 + 可管理模型目录 + 可替换模型供应商适配器”。

当前优先级是稳定统一调用链和模型配置边界，然后再做数据库配置、模型发现、权限和 BYOK。不要先做漂亮的模型下拉框，再补安全和数据模型。
