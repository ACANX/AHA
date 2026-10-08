# 验证与守卫：怎么知道「这次真的补齐了」

元数据最容易骗人的地方，是**它错了也不会让构建失败**。
所以「补了元数据」这件事本身不能作为验收，必须有守卫。

## 0. 四层守卫，各拦一类错

| 层 | 拦住的错 | 运行时机 | 成本 |
|---|---|---|---|
| 单测断言 | 清单被删 / 被改坏；生成器失效 | 每次构建 | 低 |
| 产物自证 | 元数据没被打进**构建产物**（只在源码里） | 原生镜像工作流 | 低 |
| 反向验证 | 「守卫其实是摆设」 | 建立守卫时做一次 | 低 |
| 真机走查 / agent | 还有未被发现的缺口 | 初版验收 / 发版前 | 高 |

**四层缺一不可**：前三层保证「我知道的那部分不会退化」，第四层负责「找出我不知道的」。

## 1. 单测断言：把「清单内容」变成会失败的检查

元数据是资源文件，最直接的守卫就是**把关键条目读出来断言**。

```java
class NativeImageMetadataTest {

    private static final Path METADATA = Path.of(
            System.getProperty("basedir", System.getProperty("user.dir")),
            "src", "main", "resources", "META-INF", "native-image",
            "<groupId>", "<artifactId>", "reachability-metadata.json");

    @Test
    void registersStartupReflectionChain() throws IOException {
        String json = Files.readString(METADATA);
        for (String type : List.of(
                "com.example.MainClass",
                "com.example.FrameworkToolkit",
                "com.example.platform.WinFactory")) {
            assertThat(json).as("%s 必须注册（否则运行期 ClassNotFoundException）", type)
                    .contains("\"" + type + "\"");
        }
    }

    @Test
    void registersMethodWithExactSignature() throws IOException {
        // 带 methods 的条目是多层嵌套 JSON，用大括号配平截取，别用 indexOf('}')
        String entry = entryFor(Files.readString(METADATA), "com.example.ShaderLoader");
        assertThat(entry)
                .contains("\"loadShader\"")
                .contains("com.example.Factory")
                .contains("java.lang.String")
                .contains("java.io.InputStream");
    }

    @Test
    void noDuplicateTypes() throws IOException {
        List<String> types = new ArrayList<>();
        Matcher m = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(METADATA));
        while (m.find()) types.add(m.group(1));
        assertThat(new HashSet<>(types)).as("重复注册是无意义噪声，请合并").hasSameSizeAs(types);
    }

    /** 大括号配平：截出某个 type 的完整对象（methods 子对象不会被截断）。 */
    private static String entryFor(String json, String type) {
        int at = json.indexOf("\"" + type + "\"");
        assertThat(at).isGreaterThanOrEqualTo(0);
        int start = json.lastIndexOf('{', at);
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { if (--depth == 0) return json.substring(start, i + 1); }
        }
        throw new AssertionError("未闭合：" + type);
    }
}
```

**如果元数据是生成的（如 picocli-codegen），就断言生成结果**：

```java
@Test
void generatedMetadataCoversCommands() throws IOException {
    try (InputStream in = getClass().getResourceAsStream(
            "/META-INF/native-image/picocli-generated/reflect-config.json")) {
        assertThat(in).as("picocli-codegen 必须生成反射元数据").isNotNull();
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(json).contains("com.example.cli.MainCommand");
        assertThat(json).contains("com.example.cli.ConfigCommand$SetSub");
    }
}
```

这样无论「处理器被删」还是「生成的条目变少」，构建阶段就会红。

## 2. 产物自证：在**打出来的东西**里再查一次

单测查的是源码；产物自证查的是**真正的构建产物 / 工作目录**。
两者会不一致（打包时丢文件、复制路径写错）。

工作流里的形态（诊断式，不阻塞，但要叫得响）：

```bash
meta="$work/reachability-metadata.json"
ok=true; note=""
require_meta() {  # $1=字符串 $2=人话名称
  if [ ! -f "$meta" ] || ! grep -q "$1" "$meta"; then
    ok=false; note="${note:+$note；}缺 $2"
  fi
}
require_meta "com.example.MainClass"        "主类"
require_meta "com.example.FrameworkToolkit" "工具包"
require_meta "com.example.config.AppConfig" "配置记录（JSON 序列化）"
# 生成的元数据：查构建产物里的生成文件
gen="target/classes/META-INF/native-image/picocli-generated/reflect-config.json"
grep -q "com.example.cli.MainCommand" "$gen" || { ok=false; note="${note:+$note；}缺生成的命令元数据"; }

if [ "$ok" = true ]; then
  echo "可达性元数据：已注册主类与启动链路"
else
  echo "::warning::可达性元数据不完整：$note"
fi
```

要点：

- 同时写 `::warning::`（步骤注解）与 Job Summary，避免「被跳过」被误读成「没跑」；
- 判据只留真正的不变式，别把"文件多大""恰好几条"当门禁；
- 这一步即使判失败也**不要**让可选管线变红叉（可选管线的约定见
  [java-app-graalvm-native-image-compile](../../java-app-graalvm-native-image-compile/SKILL.md)）。

## 3. 反向验证：证明守卫真的会拦

**只读配置只能证明"应该会拦"。** 建立或修改守卫后，一定做一次破坏性验证：

1. 临时删掉一条关键条目（或把精确签名改错，如 `loadShader` 去掉 `String`）；
2. 跑单测 → **必须失败**，且**错误信息要点名那个类 / 签名**；
3. 恢复，再跑 → 通过。

如果删了条目测试还是绿，说明断言写得太松（例如只查了子串、或路径指错）。

## 4. 真机走查 / tracing agent：找出"我不知道的"

### 4.1 走查清单：每个已知反射点 → 一个功能动作

| 反射点类别 | 对应功能动作 |
|---|---|
| 主类 / 应用入口 | 启动程序 |
| 配置序列化 POJO | 启动读配置 + 修改并保存配置 |
| 数据库驱动 | 新建会话 / 查询记忆 |
| HTTP / TLS | 完成一次真实网络调用（HTTPS） |
| 终端 / 信号处理 | 交互式输入；Ctrl+C 打断 |
| 日志 | 触发一条日志并确认落盘 |
| 资源（配置 / 图标 / 着色器） | 打开用到它的界面 / 执行用到它的命令 |

**没跑过的动作，对应条目要标"未验证"**，不要默认成立。

### 4.2 tracing agent 作为终局

见 [discovery](discovery.md) §2。让 agent 跑同一套走查动作，对比它采集到的
与手写清单的差集——差集就是"你没想到的"。

### 4.3 诚实边界

- 手工清单 + 静态审计**无法证明完整**；
- agent 只能覆盖**跑到的路径**；
- 所以交付话术必须是：

  > ✅「已知缺口已闭，真机逐功能验证待做」
  > ❌「一定不会再报 ClassNotFoundException」

  前者可被检验，后者不可。

## 5. 假守卫（看起来在守，其实没用的典型）

- **只查文件存在**，不查内容：文件在、条目被删，照样绿；
- **只查类名，不查方法签名**：`getMethod` 因签名不符失败，守卫却通过；
- **JSON 解析用 `indexOf('}')`**：`methods` 子对象会把它提前截断，断言形同虚设；
- **测源码不测产物**：源码对、打包丢，运行期照样炸；
- **有守卫从不反向验证**：永远不知道它是不是摆设；
- **守卫失败但被 `continue-on-error` 吞掉且无输出**：等于没有。

## 6. 建议的落地顺序

```
写元数据 → 写单测（含反向验证一次）→ 加产物自证 → 真机/agent 走查 → 把新发现回写经验库
```

前三步在**每次构建**都会跑，成本很低；第四步是唯一较重的，但它是"完整"的唯一来源。
