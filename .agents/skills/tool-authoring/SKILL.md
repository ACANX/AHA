---
name: tool-authoring
description: 新增内置工具：实现 Tool 接口、注册 ToolProvider、声明 requiredPermission 并添加测试。当需要实现新工具、注册工具提供者或调整工具权限时使用。
license: GPL-3.0-or-later
compatibility: 需要 JDK 25；工具实现位于 aha-tool 模块；必须通过 JPMS provides 与 META-INF/services 双路注册。
metadata:
  version: "1.0.0"
  owner: ACANX
---

# 新增内置工具

工具是 Agent 可调用的能力单元。所有工具实现位于 `aha-tool` 模块，通过 `ToolProvider` 被发现。

## 何时使用

- 实现新的内置工具
- 注册或调整工具提供者
- 修改工具的权限声明

## 步骤

1. 在 `aha-tool/src/main/java/com/acanx/module/aha/tool/` 实现 `Tool` 接口
2. 在对应的 `ToolProvider.tools()` 中返回该工具实例
3. 在 `aha-tool/src/main/java/module-info.java` 的 `provides` 中声明 Provider
4. 在 `aha-tool/src/main/resources/META-INF/services/com.acanx.module.aha.common.tool.ToolProvider` 中同步声明
5. 通过 `requiredPermission()` 声明所需权限
6. 添加单元测试

## 约束

- `requiredPermission()` 必须如实声明：`READ` / `NETWORK` 默认放行，`WRITE` / `EXECUTE` / `ADMIN` 需要显式授权
- 参数 schema 使用 `JsonSchema` 描述，必须与实现实际读取的键一致
- 工具必须响应 `CancellationToken`，长时间操作需可中断
- 不得抛出未包装的受检异常，统一转为 `ToolExecutionException`
- 输出需要可序列化，避免返回巨型对象树

## 参考

本技能目录内的资源：

- [assets/MyTool.java](assets/MyTool.java) - 工具实现骨架
- [assets/MyToolProvider.java](assets/MyToolProvider.java) - Provider 骨架

仓库文档：

- [ToolSystemDesign.md](../../../Docs/Design/ToolSystemDesign.md) - 工具系统设计
- [ToolUsageGuide.md](../../../Docs/Guide/ToolUsageGuide.md) - 工具使用说明
- [SecurityDesign.md](../../../Docs/Design/SecurityDesign.md) - 权限模型
