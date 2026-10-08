# 按症状定位

分两段：**构建期**（native-image 跑失败）与**运行期**（构建成功但跑不起来）。
先判断是哪一段，能省掉一半时间。

## 0. 先做二分：构建期还是运行期？

- native-image 命令**退出非 0** → 构建期，看第 1 节；
- 构建**成功**、可执行文件却起不来/闪退/开窗失败 → 运行期，看第 2 节；
- 构建成功、也能跑，但某个**功能**失效（某个工具、某个界面、某次网络调用）→ 第 2.4 节。

> 经验：**构建期的问题几乎都能靠参数解决**（加一类初始化或资源）；
> **运行期的问题几乎都是「少了一份清单」**（反射/资源/原生库）。
> 不要用改源码去换参数 —— 至少在把四类清单试完之前不要。

## 1. 构建期症状

| 症状（关键词） | 根因 | 修法 |
|---|---|---|
| `Classes that should be initialized at run time got initialized during image building` | 构建期初始化了本该运行期初始化的类 | 按报错里的类名加 `--initialize-at-run-time=<类>` |
| `... is reachable but was not initialized` / 初始化时机与其他类冲突 | 初始化时机冲突（A 想构建期、B 想运行期） | 把冲突的一方改成运行期；用 `-H:+PrintClassInitialization` 看全量结论 |
| `UnsupportedFeatureException` / `not supported in native-image` | 用到了镜像不支持的能力 | 换实现；或 `--initialize-at-run-time` 把那段代码推到运行期；实在不行只能放弃该路径 |
| `BuildTimeError` / 反射注册缺失导致构建期就报 | 构建期需要知道反射目标 | 补 `reflect-config.json` |
| 找不到 `native-image` / `Cannot run program` | 没装 GraalVM，或 Windows 上需要 `.cmd` 后缀 | 装 GraalVM；`exec` 的 executable 在 Windows 上写成 `native-image.cmd` |
| `OutOfMemoryError`（构建期） | 宿主 JVM 堆不足 | `-J-Xmx4g`（CI 上按 runner 内存调） |
| 构建极慢 / 卡住 | 资源清单过宽（例如 `.*` 把所有文件都收进去） | 收窄 `-H:IncludeResources` |

## 2. 运行期症状

### 2.1 原生库相关

| 症状 | 根因 | 修法 |
|---|---|---|
| `UnsatisfiedLinkError: no <lib> in java.library.path` | 原生库没进镜像 | 把 `.so/.dylib/.dll` 纳入 `-H:IncludeResources`；必要时确认「加载器类」在运行期初始化 |
| `UnsatisfiedLinkError: ... undefined symbol` | 版本/平台不匹配 | 确认用的是**目标平台分类器**的工件（见 2.5） |
| `--enable-native-access` 警告（JVM 侧） | 未显式声明原生访问 | 镜像构建期就加 `--enable-native-access`（JVM 侧的警告说明同一件事） |

### 2.2 反射与资源

| 症状 | 根因 | 修法 |
|---|---|---|
| `ClassNotFoundException` / `NoSuchMethodException`（**创建某对象时**） | 反射目标未注册 | 补反射配置或运行期初始化 |
| `ServiceConfigurationError` / 「找不到实现」 | `META-INF/services` 没进镜像 | `-H:IncludeResources=META-INF/services/.*` |
| 缺图标/模板/字体/`properties` | 资源未包含 | 扩展 `-H:IncludeResources` |
| 界面文字变乱码 / `UnsupportedCharsetException` | 字符集不全 | `-H:+AddAllCharsets` |

### 2.3 启动即崩（连日志都没有）

| 症状 | 根因 | 修法 |
|---|---|---|
| 双击无声退出、无任何输出 | 初始化期异常被吞 | 先从**命令行**运行以拿到 stderr；再据此加清单 |
| 启动时报某个 `static {}` 里的错 | 构建期/运行期初始化时机错 | 把那个类改成运行期初始化 |
| 开窗失败 / 报屏幕相关异常 | GUI 工具包在构建期就去读了屏幕参数 | 把 GUI 的 `application`/`glass`/`prism` 一批类设为运行期初始化 |

### 2.4 部分功能失效

- **网络请求失败**：确认 `--enable-url-protocols=http,https`（以及项目用到的其它协议）；
- **某个工具/适配器找不到**：多半是 `ServiceLoader`（见 2.2）；
- **文件写入失败**：原生镜像里**没有**构建机的路径概念，检查配置里是不是有写死的路径；
- **数据库/驱动异常**：驱动主类常需运行期初始化 + 本地库作为资源。

### 2.5 怀疑平台错配时

1. 看二进制魔法数：PE `4d5a` / ELF `7f454c46` / Mach-O `cffaedfe`（小端 64 位）；
2. 看 classpath 里 GUI/原生库依赖是不是**带目标平台分类器**的那三个；
3. 看有没有**无分类器的空壳 jar** 混进来（0 KB、无类、无 `module-info`）——
   它们与带分类器的真工件共用同一份 POM，会通过传递依赖漏进来；
   要**同时**在多个依赖上排除（实测：只排一个仍会漏）。

## 3. 构建系统层的坑（不是 native-image 的错，但症状很像）

| 症状 | 根因 | 修法 |
|---|---|---|
| 「在中央仓库找不到 `<本地模块>`」 | ① 依赖坐标写错（子模块可能覆盖了父 POM 的 groupId）；② **profile 加入的模块不会被 `-am` 带上游**（Maven 3.9.x 与 4.x 实测一致） | 核对坐标；命令行显式写 `-pl 原生模块,主模块 -am` |
| 修好后仍然同样报错 | 本地仓库的**负缓存**（`*.lastUpdated`） | 删除 `~/.m2/repository/**/*.lastUpdated` 后重跑 |
| `lib/` 里的 jar 比依赖树多 | 拷贝步骤**不删**已不在依赖集里的旧文件 | 在拷贝前清该目录（只清该目录，别删整个 `target/`） |
| macOS/Linux 上下载后不能双击运行 | 包内二进制权限不是 0755 | 打包时给二进制 `fileMode 0755`（文档保持 0644） |
| 版本号变成空的（无报错） | XML 解析器标签**带命名空间**，`findtext('version')` 静默返回 None | 用「第一个以 `version` 结尾的直接子节点」这类写法 |
| Maven 解析 POM 报 `String '--' not allowed in comment` | XML 注释里出现了 `--`（例如写了 `native-image --version`） | 改写措辞；`--gc=` 等同理 |

## 4. 排错习惯（这几条比任何单条修法都值钱）

1. **一次只改一个变量**：换参数就只换参数，别顺手升级工具链；
2. **把结论写进日志**：工具链版本、参数文件、classpath jar 数量、产物体积 ——
   这些打出来，「这次和上次哪里不一样」就不用猜；
3. **构建成功不等于能跑**：CI 只管住「编出来了」，真机点开是另一件事，必须有人做；
4. **未验证的条目要标出来**：把「我猜这样能行」和「我试过这样能行」分开写，
   否则下一个人会把猜测当结论。
