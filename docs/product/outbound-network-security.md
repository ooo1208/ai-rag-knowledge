# 出站联网安全边界

最后更新：2026-10-06

联网搜索、远程 MCP、固定 AI 服务和动态模型连接都属于“服务端代替调用方访问地址”。在
权限、审计和 egress 网络隔离尚未补齐前，不能让 HTTP 请求直接接受任意 URL。

## 当前已落地

基础设施层新增了
`io.github.ooo1208.infrastructure.network.OutboundUrlValidator`，作为所有
HTTP 出站适配器共用的第一层校验工具：

- 仅允许 `http` 和 `https`；
- 拒绝 userinfo、query、fragment、控制字符和过长 URL，避免把凭证藏在地址中；
- 公网策略只允许 80/443；
- 对域名解析得到的每个 IPv4/IPv6 地址检查回环、私网、链路本地、CGNAT、
  元数据、保留和组播范围；
- 返回通过校验的 `ValidatedUrl`，下游不应重新使用原始字符串。

默认策略是 `PUBLIC_INTERNET`。已经由管理员登记的动态模型 Provider，以及启动时创建的固定
Ollama/OpenAI API，使用
`ModelProviderEndpointValidator`，分别按 Ollama 和 OpenAI-compatible 配置 host、
端口和是否允许私网；私网 Provider 也必须命中显式 host allowlist，不能因为数据库
里有一个连接 ID 就自动放行。

这层校验不是完整的网络隔离：DNS 校验和实际连接之间存在 TOCTOU 窗口。因此生产
环境还需要代理/防火墙层 egress 规则、MCP 服务器 host allowlist、连接超时和响应
大小限制。当前固定联网搜索适配器、固定 AI API 和第一版 SSE MCP 执行适配器已经在发送请求前调用
该校验器；MCP 执行还强制要求 `MCP_EXECUTION_ALLOWED_HOSTS`，并且必须先解析数据库里的
稳定连接 ID，禁止从 `ChatRequest` 或工具执行请求直接接收 URL。固定搜索 endpoint
来自服务端配置，不应被误认为由数据库连接 ID 保护。动态模型聊天和连接测试现在也
必须通过同一套按 Provider 分组的 managed-provider host/port allowlist；默认开发配置
允许 `192.168.23.100:11434` 的 Ollama 和 `api.openai.com:443` 的 OpenAI-compatible，
更换地址时必须同步覆盖对应环境变量。

PostgreSQL 的 `POSTGRES_URL` 是 JDBC 连接，不属于 HTTP URL 校验器覆盖范围；其主机、端口
和数据库网络访问控制需要在部署网络、数据库防火墙或代理层单独限制。

## 后续接入顺序

1. 已在 MCP 连接目录落地 `transportType`、`endpointUrl`、`credentialRef`；当前
   host allowlist 先由执行开关配置注入，后续再下沉为每台服务器的数据库字段，URL 只由管理员写入；
2. MCP 工具同步、SSE 执行和联网搜索适配器在真正发起请求前调用
   `OutboundUrlValidator.validatePublicInternet(...)`；动态模型聊天和连接探针通过
   `ModelProviderEndpointValidator` 使用按 Provider 分组的 managed-provider 策略；
3. 联网搜索第一版已固定 Tavily-compatible provider、超时、响应字节级上限并禁止自动
   跟随重定向；未来若允许重定向则每个目标必须重新通过同一校验；
4. 将公网搜索限定为固定的 `SEARCH_ONLY` 工具，写操作和任意 MCP `stdio` 命令
   需要单独的审批和白名单。
