# 扩展系统设计

**文档版本**：v1.1.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、契约包与类名 | @ACANX |

---

## 1. 架构

微内核 + ModuleLayer + 事件总线。

```
ExtensionRuntime (微内核)
├── ModuleLayer Manager
├── ServiceBus (事件总线)
└── Registration Tracker
```

## 2. 扩展契约

- `AhaExtension`：`descriptor()` / `onStart(ExtensionContext)` / `onStop()`
- `ExtensionDescriptor`：id、name、version、apiVersion、mainModule、mainClass、permissions、dependencies
- `ExtensionContext`：`register` / `events` / `config` / `logger` / `dataDir`
- `Registration`：可逆注销

## 3. 扩展目录

```
AHA_HOME/Extension/
├── WeatherExtension/
│   ├── WeatherExtension.jar
│   └── AhaExtension.yaml
└── CustomLlmExtension/
```

## 4. 生命周期

```
DISCOVERED → LOADED → STARTED → STOPPED → UNLOADED
```

## 5. 隔离与权限

- 每个扩展独立 `ModuleLayer`，类加载隔离
- 扩展只读取 `aha-extension-api` 与 `aha-common`
- 权限：`READ` / `NETWORK` 默认允许；`WRITE` / `EXECUTE` / `ADMIN` 需用户确认
- 权限决策记录到日志

## 6. 分阶段实施

| 版本 | 内容 |
|---|---|
| 0.3 | `aha-extension-api` + ExtensionRuntime 基础版（静态加载 + Registration 追踪） |
| 0.4 | ServiceBus 事件总线 + 生命周期事件 + 内置组件扩展化 |
| 0.5 | ModuleLayer 隔离 + 权限模型 + 热重载探索 |
| 1.0 | 扩展生态稳定 + 官方仓库 + 文档与模板 |

## 7. 实现现状（0.1.0）

| 组件 | 位置 | 状态 |
|---|---|---|
| `AhaExtension`、`ExtensionContext`、`Registration` | `aha-extension-api` | ✅ 接口已冻结 |
| `ExtensionDescriptor`、`ExtensionDependency`、`ExtensionPermission` | `aha-extension-api` | ✅ |
| `CommandProvider`、`ConfigSource`、`EventListenerProvider` | `aha-extension-api` | ✅ 扩展点定义 |
| `ExtensionLoader`、`ExtensionManager`、`ExtensionContextImpl` | `aha-core.extension` | ⛔ 0.3 |
| `RegistrationTracker` | `aha-core.extension` | ⛔ 0.3 |
| `ExtensionLayer`（ModuleLayer 隔离） | `aha-core.extension.loader` | ⛔ 0.5 |
| `aha extension` CLI 命令 | `aha-cli` | ⛔ 占位 |

**0.1 策略**：只交付接口契约（`aha-extension-api`），运行时不引入，
避免在 0.1 阶段引入未验证的类加载与权限模型。扩展运行时目录
（`core/extension/**`）不纳入 0.1 覆盖率门禁。

