# 工具使用指南

**文档版本**：v1.1.0
**状态**：草稿
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-06 | 新增 §3.1 交互式确认（`y`/`n`/`a`） | @ACANX |

---

## 1. 内置工具

| 工具名 | 权限 | 说明 |
|---|---|---|
| `file-read` | READ | 读取文件内容 |
| `file-write` | WRITE | 写入文件（需确认） |
| `file-list` | READ | 列出目录内容 |
| `http-get` | NETWORK | HTTP GET 请求 |
| `shell-exec` | EXECUTE | 执行 Shell 命令（必须确认） |

## 2. 管理命令

```bash
aha tool list
aha tool invoke <name>
```

## 3. 权限

- `READ` / `NETWORK`：自动批准
- `WRITE` / `EXECUTE` / `ADMIN`：需显式确认

以上由 `PermissionPolicy` 统一门控：

| 策略 | 行为 |
|---|---|
| `defaultPolicy()` | 自动放行 `READ` 与 `NETWORK` |
| `defaultPlus(Set)` | 在默认基础上追加（对应 `Tools.AutoApprove`） |
| `allowAll()` | 全部放行（对应 `AutoApprove: [ALL]`） |
| `denyAll()` | 全部拒绝 |

### 3.1 交互式确认

未被自动放行的权限交由 `ToolApprover` 裁决。`chat` 使用 `ConsoleToolApprover`，
**暂停并询问**而非直接失败：

```
需要授权：file-write 请求 WRITE 权限
  path = CLAUDE.md
  content = # Test
  [y] 允许本次   [n] 拒绝   [a] 本次会话内始终允许 WRITE
授权>
```

| 输入 | 行为 |
|---|---|
| `y` | 允许本次 |
| `n` / 回车 | 拒绝（回车默认拒绝） |
| `a` | 本会话内始终允许**该权限** |
| `Ctrl+C` / `Ctrl+D` | 视为拒绝，不中断对话 |

拒绝后终端会列出授权方式。非交互场景（`run`）默认 `denyAll`，需加 `--yes`。

> 引入 `ToolApprover` 的原因：早期版本直接抛 `PERMISSION_DENIED`，
> 用户只看到“未获自动授权”——既没有确认入口，也不知道如何授权。

## 4. 内置工具（0.1.0）

| 工具 | 权限 | 参数 | 说明 |
|---|---|---|---|
| `file-read` | `READ` | `path` | 读取文本文件 |
| `file-write` | `WRITE` | `path`、`content` | 写入文本文件 |
| `file-list` | `READ` | `path` | 列出目录条目 |
| `http-get` | `NETWORK` | `url` | HTTP GET，返回响应体 |
| `shell-exec` | `EXECUTE` | `command` | 执行 Shell 命令 |

## 5. CLI 用法

```bash
# 列出工具（含权限与是否自动放行）
aha tool list

# 调用只读工具
aha tool invoke file-read --param path=./README.md

# 调用高风险工具：必须加 --yes 显式确认
aha tool invoke shell-exec --param "command=echo hi" --yes
```

无 `--yes` 时输出：

```
[error] 工具 shell-exec 需要权限 EXECUTE，未获自动授权
提示：使用 aha tool invoke shell-exec -y 确认执行
```

## 6. 自定义工具

实现 `com.acanx.module.aha.common.tool.Tool`，并通过 `ToolProvider` 注册（同时声明
`module-info` 的 `provides` 与 `META-INF/services`，以兼容 JPMS 与 classpath）：

```java
public final class FileToolProvider implements ToolProvider {
    @Override
    public List<Tool> tools() {
        return List.of(new FileReadTool(), new FileWriteTool(), new FileListTool());
    }
}
```

参见 `.agents/skills/tool-authoring/SKILL.md`。
