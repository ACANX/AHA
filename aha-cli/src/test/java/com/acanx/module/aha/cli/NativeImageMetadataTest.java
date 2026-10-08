package com.acanx.module.aha.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI 原生镜像可达性元数据守卫。
 *
 * <p>CLI 的 native-image 有两条反射来源，都必须有守卫：</p>
 *
 * <ul>
 *   <li><b>picocli</b>：命令类靠反射实例化、{@code @Option} / {@code @Parameters}
 *       靠反射注入。picocli <b>不自带</b> native-image 元数据，本项目在编译期用
 *       {@code picocli-codegen} 注解处理器生成
 *       {@code META-INF/native-image/picocli-generated/reflect-config.json}。
 *       本测试直接检查<b>生成结果</b>——处理器被误删 / 失效时立刻红，而不是等运行期。</li>
 *   <li><b>JLine</b>：自带完整元数据，但其 {@code org.jline.utils.Signals} 仍用
 *       {@code Class.forName("sun.misc.Signal")} 处理 Ctrl+C，这块<b>不在</b> JLine 的
 *       元数据里，必须由本项目的 {@code reachability-metadata.json} 补。</li>
 * </ul>
 *
 * <p>此外还守卫 AHA 自身被 Jackson 反射读写的配置记录（Jackson 3 不随附元数据，
 * 漏注册会「启动读配置就崩」）。</p>
 *
 * @since 0.1.1
 */
class NativeImageMetadataTest {

    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

    private static final Path METADATA = Path.of(
            System.getProperty("basedir", System.getProperty("user.dir")),
            "src", "main", "resources", "META-INF", "native-image",
            "com.acanx.module.aha", "aha-cli", "reachability-metadata.json");

    /** picocli-codegen 生成的元数据（随 aha-cli.jar 进 classpath）。 */
    private static final String PICOCLI_GENERATED =
            "/META-INF/native-image/picocli-generated/reflect-config.json";

    @Test
    void picocliGeneratedMetadataCoversCommandsAndSubcommands() throws IOException {
        String json = readResource(PICOCLI_GENERATED);
        assertThat(json)
                .as("picocli-codegen 注解处理器必须在编译期生成反射元数据；"
                        + "缺失说明处理器被删或失效（见 aha-cli/pom.xml），"
                        + "原生镜像会在执行子命令时 NoSuchMethodException / 字段注入失败")
                .contains(AhaCli.class.getName())
                .contains(ConfigCommand.class.getName() + "$SetSub");
    }

    @Test
    void reachabilityMetadataRegistersJacksonConfigAndProtocolTypes() throws IOException {
        String json = readMetadata();
        for (String type : List.of(
                "com.acanx.module.aha.core.config.AhaConfig",
                "com.acanx.module.aha.core.config.ModelConfig",
                "com.acanx.module.aha.core.llm.protocol.ToolCall")) {
            String entry = entryFor(json, type);
            assertThat(entry)
                    .as("%s 由 Jackson 反射构造 / 读写（Jackson 3 不随附 native-image 元数据），"
                            + "必须注册构造器与字段，否则读配置 / 会话时运行期失败", type)
                    .contains("allDeclaredConstructors");
        }
    }

    @Test
    void reachabilityMetadataRegistersJlineSignalReflection() throws IOException {
        String json = readMetadata();
        // JLine 的 Signals 用 Class.forName 加载这两个类型，JLine 自带元数据没有覆盖。
        for (String type : List.of("sun.misc.Signal", "sun.misc.SignalHandler")) {
            assertThat(json)
                    .as("JLine 的 Signals 用 Class.forName 加载 %s，必须注册，"
                            + "否则 Ctrl+C 处理在原生镜像里失败", type)
                    .contains("\"type\": \"" + type + "\"");
        }
    }

    @Test
    void reachabilityMetadataHasNoDuplicateTypes() throws IOException {
        List<String> types = new ArrayList<>();
        Matcher matcher = TYPE.matcher(readMetadata());
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        Set<String> unique = new HashSet<>(types);
        assertThat(unique)
                .as("同一条目重复注册是无意义噪声（后一条会覆盖前一条），请合并")
                .hasSameSizeAs(types);
    }

    private static String readMetadata() throws IOException {
        assertThat(METADATA).as("CLI 原生镜像可达性元数据").exists();
        return Files.readString(METADATA);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = NativeImageMetadataTest.class.getResourceAsStream(path)) {
            assertThat(in).as("classpath 资源 %s 必须存在", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 截取某个 {@code type} 对应的完整 JSON 对象（做大括号配平，method 子对象不会被截断）。 */
    private static String entryFor(String json, String type) {
        int at = json.indexOf("\"" + type + "\"");
        assertThat(at).as("必须注册 %s", type).isGreaterThanOrEqualTo(0);

        int start = json.lastIndexOf('{', at);
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("元数据里 " + type + " 的对象没有闭合：" + json.substring(start));
    }
}
