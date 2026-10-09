# TD-00006 DesktopProviderEnhancement

> 待办编号：TD-00006
> 标题：桌面端：供应商连接测试与密钥写入密钥库
> 状态：☐ 未开始
> 跟踪 Issue：[77](https://github.com/ACANX/AHA/issues/77)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/Dbsx.txt`「0.2 桌面端 · 下一步待办」第 8 项（**待办**）。

## 需求

桌面端供应商配置目前只能查看 / 修改 / 保存，缺两项能力：

1. **连接测试**：等价于 `aha provider test`——在界面上验证当前供应商与模型是否真的可用；
2. **密钥写入密钥库**：API Key 目前是明文，应改为写入密钥库（`Security.KeyStore` 路径），界面只展示引用与状态。

## 验收标准

- [ ] 供应商配置面板有「测试连接」入口，成功 / 失败给出可读原因（不暴露密钥）；
- [ ] 新增 / 修改的 API Key 写入密钥库，配置文件里不再出现明文；
- [ ] 密钥缺失或密钥库不可用时降级行为明确（提示而非崩溃）；
- [ ] 与 CLI 端 `aha secret set|get|delete|list` 使用同一套存储实现。

## 关联

- `Docs/Design/DesktopDesign.md` §10、`Docs/Design/ConfigurationGuide.md`
- `Docs/Dbsx.txt`（0.2 待办第 8 项）

