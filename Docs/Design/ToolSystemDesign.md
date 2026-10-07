# 工具系统设计

**文档版本**：v1.1.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 补「Windows 上的命令执行方式」（临时批处理文件、CRLF、原生编码） | @ACANX |

---

## 1. 权限模型

| 权限级别 | 说明 | 默认行为 |
|---|---|---|
| `READ` | 读取文件、环境变量 | 自动批准 |
| `WRITE` | 写入文件、修改配置 | CLI 提示确认；桌面端弹窗 |
| `NETWORK` | HTTP 请求 | 自动批准（可配置白名单） |
| `EXECUTE` | 执行外部命令 | 必须显式确认，不可默认批准 |
| `ADMIN` | 修改系统配置、安装工具 | 必须显式确认 |

## 2. 发现机制

`ToolRegistry` 通过 `ServiceLoader.load(ToolProvider.class)` 发现工具。

## 3. 内置工具

| 工具名 | 权限 | 说明 |
|---|---|---|
| `file-read` | READ | 读取文件内容，支持路径白名单 |
| `file-write` | WRITE | 写入文件，需用户确认 |
| `file-list` | READ | 列出目录内容 |
| `http-get` | NETWORK | HTTP GET 请求 |
| `shell-exec` | EXECUTE | 执行 Shell 命令，需显式确认。Windows 上把命令写入临时批处理文件后执行（见下） |

### Windows 上的命令执行方式

Windows 侧**不**使用 `cmd.exe /c <命令原文>`，而是把命令写入临时批处理文件（`%TEMP%\aha-shell-*.cmd`）
后执行 `cmd.exe /c <文件>`。原因是 `cmd /c` 只执行命令行的**第一行**：
多行命令的后续行被静默丢弃——不报错、退出码为 0，用户看到的是「命令成功但什么都没发生」。

落盘时有两处细节不能省：

| 细节 | 原因 |
|---|---|
| 首行写入 `@echo off` | 批处理默认逐行回显，会把 `C:\...>echo first` 之类混进结果 |
| 行尾统一为 CRLF | LF-only 批处理的 `if (...)` 块解析不可靠（见 `Docs/DevSpec/DocumentationSpec.md`） |
| 用平台原生编码写入 | cmd 按控制台代码页解析批处理文件，UTF-8 写入会让其中的中文字节被误读 |

顺带消除了另一个问题：Java 的参数引用规则与 cmd 的解析规则不兼容，
此前嵌套 `cmd /c` 时输出会多出一个引号（`echo nested` → `nested"`）。

POSIX 侧仍用 `/bin/sh -c <原文>`——它本身就能处理多行，无需落盘。

## 4. MCP

0.1 仅定义 `McpAdapter` 接口，不提供实现。

## 5. 权限策略（实现）

`PermissionPolicy` 为函数式接口，在 `ToolRegistry.invoke` 前门控：

```java
@FunctionalInterface
public interface PermissionPolicy {
    boolean isAutoApproved(String toolName, ToolPermission permission);

    static PermissionPolicy defaultPolicy();           // READ + NETWORK
    static PermissionPolicy defaultPlus(Set<ToolPermission> extra);
    static PermissionPolicy autoApprove(Set<ToolPermission> set);
    static PermissionPolicy allowAll();
    static PermissionPolicy denyAll();
}
```

未获授权时抛出 `ToolExecutionException`，错误码 `PERMISSION_DENIED`。

### 5.1 配置与交互

| 场景 | 行为 |
|---|---|
| `Tools.AutoApprove: []`（默认） | `READ` / `NETWORK` 自动放行 |
| `Tools.AutoApprove: [WRITE]` | 额外自动放行 `WRITE` |
| `Tools.AutoApprove: [ALL]` | `allowAll()` |
| CLI `tool invoke <高风险工具> -y` | 绕过策略，用户显式确认 |

### 5.2 工具发现

`ToolRegistry` 默认构造器通过 `ServiceLoader.load(ToolProvider.class)` 发现工具；
`aha-tool` 同时声明了 `module-info` 的 `provides` 与 `META-INF/services`。
`aha-cli` 显式 `requires`/依赖 `aha-tool`，确保运行时可达。
