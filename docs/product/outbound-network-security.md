# 出站联网安全边界

最后更新：2026-10-06

联网搜索、远程 MCP 和动态模型连接都属于“服务端代替调用方访问地址”。在
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

默认策略是 `PUBLIC_INTERNET`。只有已经由管理员登记的内部 Provider 才可以显式
构造 `OutboundUrlPolicy.managedProvider(...)`，并且仍应额外限制 host allowlist。

这层校验不是完整的网络隔离：DNS 校验和实际连接之间存在 TOCTOU 窗口。因此生产
环境还需要代理/防火墙层 egress 规则、MCP 服务器 host allowlist、连接超时和响应
大小限制。当前固定联网搜索适配器和第一版 SSE MCP 执行适配器已经在发送请求前调用
该校验器；两者都必须先解析数据库里的稳定连接 ID，再调用校验器，禁止从
`ChatRequest` 或工具执行请求直接接收 URL。

## 后续接入顺序

1. 为 MCP 连接目录增加 `transportType`、`endpointUrl`、`credentialRef` 和
   `allowedHosts`，URL 只由管理员写入；
2. MCP 工具同步、SSE 执行和联网搜索适配器在真正发起请求前调用
   `OutboundUrlValidator.validatePublicInternet(...)`；
3. 联网搜索第一版已固定 Tavily-compatible provider、超时、响应字节级上限并禁止自动
   跟随重定向；未来若允许重定向则每个目标必须重新通过同一校验；
4. 将公网搜索限定为固定的 `SEARCH_ONLY` 工具，写操作和任意 MCP `stdio` 命令
   需要单独的审批和白名单。
