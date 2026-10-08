package com.acanx.module.aha.desktop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
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
 * 原生镜像可达性元数据守卫（issue #26、issue #35）。
 *
 * <p>JavaFX 的启动路径全在 native-image 的 closed-world 静态分析之外，
 * 必须在 {@code reachability-metadata.json} 里显式注册，否则运行期直接抛
 * {@code ClassNotFoundException} / {@code NoSuchMethodException}：</p>
 *
 * <ul>
 *   <li>issue #26：{@code Application.launch} 用 {@code Class.forName} 加载主类，
 *       {@code LauncherImpl} 用 {@code getConstructor().newInstance()} 实例化它；</li>
 *   <li>issue #35：{@code Toolkit.getToolkit()} 用 {@code Class.forName} 加载
 *       {@code com.sun.javafx.tk.quantum.QuantumToolkit}，{@code PlatformFactory}
 *       用同一手法加载各平台的 Glass 工厂，{@code GraphicsPipeline} 用同一手法
 *       加载 Prism 管线。</li>
 * </ul>
 *
 * <p>本测试在 Build / Gate 阶段就能拦住「元数据被删或被改坏」，
 * 不必等可选的原生镜像管线真跑起来（那条管线不上门禁）。</p>
 *
 * @since 0.2.0
 */
class NativeImageMetadataTest {

    private static final String MAIN_CLASS = AhaDesktopApp.class.getName();

    /** 启动链路里所有由 {@code Class.forName} 加载的类型（issue #35）。 */
    private static final List<String> STARTUP_REFLECTION_TYPES = List.of(
            "com.sun.javafx.tk.quantum.QuantumToolkit",
            "com.sun.glass.ui.win.WinPlatformFactory",
            "com.sun.glass.ui.gtk.GtkPlatformFactory",
            "com.sun.glass.ui.mac.MacPlatformFactory",
            "com.sun.prism.d3d.D3DPipeline",
            "com.sun.prism.es2.ES2Pipeline",
            "com.sun.prism.sw.SWPipeline",
            "com.sun.prism.j2d.J2DPipeline");

    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

    private static final Path METADATA = Path.of(
            System.getProperty("basedir", System.getProperty("user.dir")),
            "src", "main", "resources", "META-INF", "native-image",
            "com.acanx.module.aha", "aha-desktop", "reachability-metadata.json");

    @Test
    void reachabilityMetadataRegistersTheJavafxApplicationClass() throws IOException {
        String json = readMetadata();

        String entry = entryFor(json, MAIN_CLASS);
        assertThat(entry)
                .as("主类条目必须注册构造器：LauncherImpl 用 getConstructor().newInstance() 实例化它"
                        + "（漏注册时原生镜像启动即报 ClassNotFoundException，issue #26）")
                .containsAnyOf("allDeclaredConstructors", "allPublicConstructors");
    }

    @Test
    void reachabilityMetadataRegistersTheJavafxToolkitStartupChain() throws IOException {
        String json = readMetadata();
        List<String> missing = new ArrayList<>();
        for (String type : STARTUP_REFLECTION_TYPES) {
            if (json.indexOf("\"" + type + "\"") < 0) {
                missing.add(type);
            }
        }
        assertThat(missing)
                .as("JavaFX 启动链路由 Class.forName 加载这些类型，必须逐个注册；"
                        + "漏注册时原生镜像一启动就 ClassNotFoundException（issue #35）")
                .isEmpty();
    }

    @Test
    void reachabilityMetadataRegistersTheQuantumToolkitConstructor() throws IOException {
        String entry = entryFor(readMetadata(), "com.sun.javafx.tk.quantum.QuantumToolkit");
        assertThat(entry)
                .as("QuantumToolkit 必须注册无参构造器：Toolkit.getToolkit() 用"
                        + " getDeclaredConstructor().newInstance() 实例化它（issue #35 的直接报错点）")
                .contains("\"<init>\"");
    }

    @Test
    void reachabilityMetadataRegistersPrismPipelineEntryPoints() throws IOException {
        String json = readMetadata();
        for (String type : List.of("com.sun.prism.d3d.D3DPipeline",
                "com.sun.prism.es2.ES2Pipeline",
                "com.sun.prism.sw.SWPipeline",
                "com.sun.prism.j2d.J2DPipeline")) {
            String entry = entryFor(json, type);
            assertThat(entry)
                    .as("%s 必须注册静态 getInstance()：GraphicsPipeline.createPipeline()"
                            + " 用 Class.forName + getMethod(\"getInstance\") 加载它", type)
                    .contains("\"getInstance\"");
        }
    }

    @Test
    void reachabilityMetadataRegistersStockShaderLoadersWithExactSignature() throws IOException {
        String entry = entryFor(readMetadata(), "com.sun.prism.shader.Solid_Color_Loader");
        assertThat(entry)
                .as("D3DResourceFactory.createStockShader 用 getMethod(\"loadShader\", ShaderFactory, String, InputStream)"
                        + " 反射调用；签名写错（例如漏掉 String）时运行期抛 MissingReflectionRegistrationError")
                .contains("com.sun.prism.ps.ShaderFactory")
                .contains("java.lang.String")
                .contains("java.io.InputStream");
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
                            + "必须注册构造器与字段，否则存 / 读配置与会话时运行期失败", type)
                    .contains("allDeclaredConstructors");
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
        assertThat(METADATA).as("原生镜像可达性元数据").exists();
        return Files.readString(METADATA);
    }

    /**
     * 截取某个 {@code type} 对应的完整 JSON 对象（含嵌套的 methods 对象）。
     *
     * <p>不能像 issue #26 那版直接用 {@code indexOf('}')}：带 {@code methods}
     * 的条目里有嵌套对象，那样只会截到内层的右花括号。这里做大括号配平。</p>
     */
    private static String entryFor(String json, String type) {
        int at = json.indexOf("\"" + type + "\"");
        assertThat(at)
                .as("必须注册 %s（JavaFX 用 Class.forName 加载它）", type)
                .isGreaterThanOrEqualTo(0);

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
