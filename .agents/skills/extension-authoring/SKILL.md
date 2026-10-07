---
name: extension-authoring
description: 新增扩展：实现 AhaExtension、编写 AhaExtension.yaml 描述符、通过 ExtensionContext 注册可逆注销的扩展点。当需要开发扩展、注册扩展点或定义扩展权限时使用。
license: Apache-2.0
compatibility: 需要 JDK 25；扩展以 aha-extension-api 与 aha-common 为编译期依赖；扩展运行时在 0.1 为占位实现。
metadata:
  version: "1.0.0"
  owner: ACANX
---

# 新增扩展

扩展通过 `aha-extension-api` 提供的契约扩展 AHA 能力。扩展不依赖 `aha-core`，由 Core 侧加载。

## 何时使用

- 开发新扩展
- 注册新的扩展点
- 声明扩展权限与依赖

## 步骤

1. 扩展工程依赖 `aha-extension-api` 与 `aha-common`
2. 实现 `AhaExtension` 接口
3. 编写 `AhaExtension.yaml` 描述符（ID、版本、权限、依赖）
4. 在 `onStart` 中通过 `ExtensionContext.register` 注册扩展点
5. 在 `onStop` 中完成可逆注销

## 约束

- **注册必须可逆**：`onStart` 中注册的每一项，都必须在 `onStop` 中注销
- 权限必须在描述符中声明，运行时按声明校验，不得越权
- 扩展不得依赖 `aha-core` 内部实现，只使用 `aha-extension-api` 暴露的契约
- 扩展 ID 建议 PascalCase，与目录名一致
- 扩展资源位于 `AHA_HOME/Extension/`

## 参考

本技能目录内的资源：

- [assets/MyExtension.java](assets/MyExtension.java) - 扩展骨架

仓库文档：

- [ExtensionSystemDesign.md](../../../Docs/Design/ExtensionSystemDesign.md) - 扩展系统设计
- [ExtensionAuthoringGuide.md](../../../Docs/Guide/ExtensionAuthoringGuide.md) - 扩展开发指南
- [EventBusDesign.md](../../../Docs/Design/EventBusDesign.md) - 事件总线与分发模式
