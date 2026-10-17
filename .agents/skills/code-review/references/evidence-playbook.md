# 取证命令集

原则：**每条 finding 配一条能重跑的命令**。取不到证据的，降级为「推断」或删掉 —— 不写结论词。

---

## A. 范围与上下文

```bash
gh pr view <n> --json files,title,body --jq '.files[].path'   # 改动文件清单（步 0 用）
gh pr diff <n> --name-only                                     # 同上，快
gh pr checks <n>                                               # CI 结论（要 run 编号就去 Actions）
gh api repos/<owner>/<repo>/pulls/<n>/reviews                  # 有人提过 review 吗
gh api --paginate repos/<owner>/<repo>/issues/<n>/comments --jq '.[].id'  # 历轮评论 id（复核时按 id 引用）
git log --oneline <base>..<head>                               # 被审提交区间
```

> 评论 id 要**照抄引用**（`#issuecomment-<id>`）。下轮的复核表就靠它对齐「上一轮说了哪几条」。

---

## B. 「这东西真的在提交里吗」<a id="in-commit"></a>

```bash
git check-ignore -v <路径>              # 被哪条规则忽略
git cat-file -e HEAD:<路径>             # 不在 HEAD ⇒ 致命（P0 级）
git ls-files <目录>/                    # 已跟踪清单
git status --porcelain --ignored=matching <目录>/   # 把「被忽略」也显出来
```

配套：**打包描述符 ↔ 磁盘**对照。

```bash
grep -n '<include>' <模块>/src/assembly/*.xml         # 描述符要什么
git ls-files <产物目录>/                              # 仓库里有什么
```

差异即 P0（描述符引用了不存在的产物 ⇒ 打包工具**静默跳过**）。

---

## C. 真代码 probe<a id="probe"></a>

不要靠推演，跑最不常见的那条取值（通常 5 行 `jshell` 或一个临时 `main` 就够）：

```bash
# 例：验证「计划文件对某取值能否写出来」
jshell --class-path <编译输出> <<'EOF'
var layout = com.acanx.module.aha.core.update.InstallLayout.of(java.nio.file.Path.of("/tmp/probe/install"));
System.out.println(layout.restartCommand());
EOF
```

输出就是证据。**在报告里贴命令 + 输出**，别只写「会失败」。

> 临时 driver 一律放 `/tmp`，**不进仓库**；结论写进报告或文档。

---

## D. 反向注入（守卫验证的标配）<a id="inject"></a>

任何新增 / 修改的守卫，必须**正反各验一次**：

| 方向 | 动作 | 期望 |
|---|---|---|
| 正向 | 构造一个应被抓住的违规（真实的、不是想象的） | 守卫**红**，消息指名到文件 |
| 反向 | 删掉守卫依赖的前提（`.gitignore` 例外 / 某行断言 / 元数据登记） | 守卫**红** |
| 恢复 | 还原 | 守卫**绿** |

```bash
git rm --cached <路径>            # 反向注入 1：让文件离开索引 ⇒ 守卫必须红
git restore --staged <路径>       # 还原
```

**逐条对照真实样本**：一次注入一个条件，别一口气删三处 —— 多行样本会掩盖写错的那一条。
（真实教训：一条正则写错，样本里另一条目标准确匹配，于是「验证通过」。）

---

## E. 死代码与调用点

```bash
grep -rn "<方法名>" --include=*.java .                  # 有定义、有测试、无生产调用 ⇒ 死代码
grep -rn "<配置键>" --include=*.java --include=*.ps1 --include=*.py .
grep -rn "\-\-<选项>" .github/workflows Script/         # 工作流引用但不存在的选项
```

**判据**：新增的「对外可见行为」（提示 / 清理 / 统计 / 开关）都要问**谁调它**。

---

## F. 契约三方对照

对同一个字段（计划文件、清单、配置文件、环境变量），把三处并排抄下来：

| 角色 | 文件 | 对它的解释 |
|---|---|---|
| 写方 | `XxxWriter.java:行` | … |
| 校验方 | `XxxValidator.java:行` | … |
| 执行方 | `Script/Yyy.ps1:行` | … |

不一致即 P1 起（跨语言组合优先级更高：错了往往只报一句莫名其妙的错）。
**最不常见的那条取值一定要 probe**（例：只有单体 jar 时的兜底）。

---

## G. 文档与文案漂移

```bash
grep -rn "<承诺词>" --include=*.md --include=*.java .    # 例：「续传」「可恢复」「自动清理」「两个 helper」
grep -rn "<改掉的旧说法>" --include=*.md --include=*.java .
```

范围要**含**：面向用户的指南、设计文档的**验收口径**、代码注释、测试方法名。
（改了行为没回头改这四处，是真实发生过的一整条 P2。）

---

## H. 「为什么 CI 没红」

对每条 P0/P1 追问一句，并落到具体环节（详见 [ci-blind-spots](ci-blind-spots.md)）：

```bash
git check-ignore -v <路径>                                     # 忽略链
grep -n "<include>" <描述符>                                    # 打包静默跳过
grep -n "ls-files\|collect\|glob" bin/Check<X>.py               # 守卫的取样范围
grep -n "<被引用的文件>" <Test>.java                            # 测试是不是自己造的
```

产出：**逃逸链表**（逃逸环节 → 为什么没拦住）。这一节是报告里最有价值的部分：
它决定了「要立哪道守卫」，而不是「下次小心点」。

---

## I. CI 运行编号（作为证据引用）

```bash
gh run list --commit <sha> --json databaseId,name,conclusion,event \
  --jq '.[] | "\(.databaseId) \(.name) \(.conclusion) \(.event)"'
gh run view <run-id> --log-failed | head -50
```

报告里引用**运行编号 + 结论**（如「`<sha>` 的 push Build 7/7 ✅」），并注明**事件类型**
（`push` / `pull_request`）—— 同一提交两个 run 的结论可能不同。

---

## 取证成色（写报告时的三档标注）

| 成色 | 含义 | 报告里的写法 |
|---|---|---|
| 已观测事实 | 有命令输出 / CI 运行编号 | 贴命令与输出 |
| 机制行为模型 | 读代码推出的必然结果，未跑 | 「据 `X.java:12` 与 `Y.ps1:8` 推出…（未跑）」 |
| 未定位未复现 | 现象有、机制没定 | 明确写「**未定位**」，并给出下一步取证方向 |

**禁止**把第三档写成第一档。这不是措辞问题：真机验证会照着报告走，标错方向等于浪费一轮。
