# 扩展开发指南

**文档版本**：v1.1.0
**状态**：草稿
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、包名、类名、配置段与描述符 | @ACANX |

---

## 1. 依赖

第三方扩展仅依赖 `aha-extension-api` 与 `aha-common`，不依赖 `aha-core` 内部实现。

## 2. 实现 AhaExtension

```java
public class WeatherExtension implements AhaExtension {
    @Override
    public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor(
            "com.example.weather", "Weather Extension", "1.0.0", "1.0",
            "com.example.weather", "com.example.weather.WeatherExtension",
            List.of(ExtensionPermission.NETWORK), List.of());
    }

    @Override
    public void onStart(ExtensionContext ctx) {
        ctx.register(ToolProvider.class, () -> List.of(new WeatherTool()));
    }

    @Override
    public void onStop() { }
}
```

## 3. AhaExtension.yaml

```yaml
Id: com.example.weather
Name: Weather Extension
Version: 1.0.0
ApiVersion: "1.0"
MainModule: com.example.weather
MainClass: com.example.weather.WeatherExtension
Permissions:
  - NETWORK
Dependencies: []
```

## 4. 约束

- 扩展不得直接修改全局静态状态
- 扩展不得调用 `System.exit()`
- 注册资源必须通过 `Registration` 可逆注销

## 5. 实现现状（0.1.0）

| 组件 | 模块 | 状态 |
|---|---|---|
| `AhaExtension`、`ExtensionContext`、`Registration` | `aha-extension-api` | ✅ 接口已冻结 |
| `ExtensionDescriptor`、`ExtensionDependency`、`ExtensionPermission` | `aha-extension-api` | ✅ |
| `EventBus`、`EventKey`、`DispatchMode` | `aha-extension-api` | ✅ 接口已冻结 |
| `ExtensionLoader`、`ExtensionManager`、`ExtensionContextImpl` | `aha-core` | ❌ 0.3 |
| `EventBusImpl`、`RegistrationTracker` | `aha-core` | ❌ 0.3 |
| `aha extension` 命令 | `aha-cli` | ⛔ 占位，输出「扩展运行时自 AHA 0.3 起提供」 |

当 0.3 实现落地后，本指南将补充：

- 扩展项目模板与目录结构
- `module-info` 写法（含 `provides AhaExtension`）
- 权限声明与运行时校验行为
- 生命周期与热重载限制

> 当前如需扩展能力，可先以内置 `Tool`（见 [ToolUsageGuide.md](ToolUsageGuide.md)）形式接入。
