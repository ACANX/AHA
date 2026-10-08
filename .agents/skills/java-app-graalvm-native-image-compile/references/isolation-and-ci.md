# 隔离与 CI 编排

原生镜像是**旁路**：它的失败不允许挡住开发、测试与正式发版。这一节是把它做扎实的全部要点。

## 1. 四条隔离（缺一条就不算隔离）

### 1.1 反应堆隔离：放进 profile，不放进 `<modules>`

```xml
<!-- 父 POM -->
<profiles>
    <profile>
        <id>desktop-native</id>          <!-- 名字按项目语义改 -->
        <modules>
            <module>my-native</module>   <!-- 默认构建看不见它 -->
        </modules>
    </profile>
</profiles>
```

- 常规 `./mvnw clean verify` 里**没有**这个模块；
- 需要时显式激活：`./mvnw -Pdesktop-native -pl my-native,my-app -am package -DskipTests`。

> **注意**：父 POM 通常已经有别的 `<profiles>` 块（如按平台的 profile）。
> **新 profile 要并进已有块**，另起一个 `<profiles>` 会报 `Duplicated tag: 'profiles'`。
> 另外 XML 注释里禁止出现 `--`。

### 1.2 检查隔离：独立工作流 + 独立作业名

- 单独一个 workflow 文件（原生镜像构建慢，不该混进快检查）；
- 作业名**不要复用** `build` / `gate` 这类名字 —— 分支保护的必需检查按作业名匹配，
  复用会把实验性失败变成「PR 卡住」；
- 不把它加进必需检查列表。

### 1.3 Tag 隔离：独立前缀，且**不要命中正式发版的过滤**

正式发版通常按 tag 触发（例如 `V*` / `v*`）。原生镜像用另一种前缀，如 `native-v0.1.1.00021`：

- 首字符不是 `v`，因此 `v*` 不匹配 → **不会触发正式发版**；
- 发布为**预发行版**（prerelease），不出现在 `releases/latest`，避免被误当成正式版本。

### 1.4 失败传播隔离

```yaml
strategy:
  fail-fast: false            # 一条腿失败不取消其余（三个平台是并列交付物）
continue-on-error: ${{ matrix.experimental }}   # 实验腿失败只标注自己
# 发布作业：
if: always() && needs.<版本作业>.result == 'success'   # 部分平台成功也照发
```

## 2. 工作流骨架要点

按顺序的关键步骤（完整骨架见 [../assets/workflow-native.yml](../assets/workflow-native.yml)）：

1. **`native-version` 作业**：先算版本，三条腿共用同一个版本号（避免各算各的）。
2. **矩阵**：`include:` 显式列出 `os` / `label`（如 `linux-x64`）/ `platform`（分类器名）/
   `jdk` / `experimental`。
   - native-image **不能交叉编译**：每个 runner 只能产自己平台的产物；
   - 架构差异要显式（`macos-arm64` 与 `linux-x64` 不是一回事）。
3. **装 GraalVM**：用官方 action（`graalvm/setup-graalvm@v1`），
   `distribution` 选 `graalvm-community`（开源项目无授权顾虑，同样带 native-image）
   或 `graalvm`（Oracle 版）。
   - 记得核对 action 的**大版本**（首次运行时看日志，失败是孤立的）；
4. **本腿参数**：Windows 上 `native-image` 的可执行名带 `.cmd`（`exec` 不会自动补后缀）；
   JDK 轴 / 平台轴在 profile 里切换，命令行只传 `-P…`。
5. **工具链自证**：把 `java -version`、`native-image --version`、本腿参数、
   版本号全部打印出来（「门禁必须自证」）。
6. **构建**：走项目统一的 Maven 入口（若有「清负缓存 + 只对瞬时故障重试」的包装，就用它）。
7. **产物自证**：存在 → 体积下限 → 平台魔法数 → 关键依赖数量（见下一节）。
8. **改名并上传制品**，命名包含「版本 + JDK 轴 + 平台标签」，用户一眼能挑对。
   - 构建产物统一输出到 `dist/`（仓库根只放源码与文档）；
   - 「制品（artifact）」与「构建产物目录」是两回事：下载/汇总制品时另用
     `artifacts/` 之类的目录名，免得看起来像在往 `dist/` 里塞东西。
9. **单独一个发布作业**汇总制品并发布：三条腿并发写同一个 release 会互相打架，
   所以要**单写者**。

## 3. 产物自证脚本（可直接改用）

```bash
set -euo pipefail
work=my-native/target/native

# ① 存在（Windows 带 .exe）
bin=$(ls "$work/my-native" "$work/my-native.exe" 2>/dev/null | head -n1 || true)
[ -n "$bin" ] || { echo "::error::没有产出可执行文件"; ls -la "$work"; exit 1; }
size=$(stat -c%s "$bin" 2>/dev/null || stat -f%z "$bin")
echo "产物：$bin（$((size / 1024)) KB）"

# ② 体积下限（原生镜像把 JDK 编进去了，异常小说明只编了个壳）
[ "$size" -ge 5242880 ] || { echo "::error::产物过小，不像原生镜像"; exit 1; }

# ③ 平台魔法数
magic=$(head -c 4 "$bin" | od -An -tx1 | tr -d ' \n')
case "${RUNNER_OS}" in
  Windows) expect="4d5a" ;;      # PE
  Linux)   expect="7f454c46" ;;  # ELF
  macOS)   expect="cffaedfe" ;;  # Mach-O 64 位小端
esac
[ "$magic" = "$expect" ] || { echo "::error::平台魔法数不符：$magic != $expect"; exit 1; }

# ④ 关键依赖数量（示例：GUI 框架三件套必须带平台分类器）
n=$(ls "$work"/lib/*gui*-*.jar 2>/dev/null | wc -l)
[ "$n" -eq 3 ] || { echo "::error::期望 3 个本平台 GUI jar，实际 $n"; exit 1; }
```

## 4. 版本号：可追溯到 PR

```bash
# 基线从根 POM 读（唯一来源）。注意命名空间问题：
base=$(python3 -c "import xml.etree.ElementTree as ET; r = ET.parse('pom.xml').getroot(); \
                   print(next(e.text for e in r if e.tag.endswith('version')).strip())")

# PR 号：pull_request 事件自带；push（含 merge commit）要按 SHA 反查 API
pr=$(curl -sS -H "Authorization: Bearer ${GH_TOKEN}" \
       -H "Accept: application/vnd.github+json" \
       "https://api.github.com/repos/${REPO}/commits/${SHA}/pulls" \
     | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['number'] if isinstance(d,list) and d else '')")

version=$(printf '%s.%05d' "$base" "${pr:-0}")   # 0.1.1 + 21 → 0.1.1.00021
```

要点：

- **取不到 PR 号不要猜**：记 `00000` 并 `::warning::`，否则版本号会骗人；
- 提供 `workflow_dispatch` 输入（`pr` / `version`）用于补跑与重打；
- 版本、基线、PR 号都写进 release 说明与 step summary。

## 5. 触发策略

| 选择 | 适用 | 代价 |
|---|---|---|
| `push` 到开发主分支（每次合并都出包） | 想让「验证线」始终有最新包可用 | 文档类改动也会触发一次慢构建 |
| 加 `paths` 过滤 | 想省 CI 时间 | 「要验证时不一定拿得到包」，可预期性下降 |
| 只 `workflow_dispatch` | 极省 CI | 需要人记得点 |

本项目选第一种：**只有「合并即产出」才谈得上可预期**。
另加 `concurrency`（同一分支只保留最新一次运行）避免旧运行堆积。
