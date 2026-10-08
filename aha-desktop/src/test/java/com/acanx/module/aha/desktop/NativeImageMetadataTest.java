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
 * 原生镜像可达性元数据守卫（issue #26、issue #35、issue #37、issue #39、issue #41）。
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

    /**
     * Prism 效果 peer 类（issue #41）。
     *
     * <p>{@code Renderer.getPeerInstance} 用**动态拼出来的类名**反射加载效果 peer：
     * {@code Class.forName(rootPkg + ".impl.prism.Pr" + name + "Peer")} 与
     * {@code Class.forName(rootPkg + ".impl.prism.ps.PPS" + name + "Peer")}；软件回退走
     * {@code sw.java.JSW<name>Peer} / {@code sw.sse.SSE<name>Peer}（{@code RendererDelegate.getPlatformPeerName}）。
     * 类名在构建期不可见，native-image 的 closed-world 看不到，未登记时渲染到该效果的控件会抛
     * {@code Could not create peer <name> for renderer …}（issue #41 的直接报错点）。</p>
     *
     * <p>完整清单（99 个具体 peer，来自对 javafx-graphics 25 jar 的扫描）在
     * {@code reachability-metadata.json}；这里守护每个包与回退路径的代表。</p>
     */
    private static final List<String> PRISM_EFFECT_PEERS = List.of(
            // 直接报错点（prism.ps）
            "com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolveShadowPeer",
            "com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolvePeer",
            "com.sun.scenario.effect.impl.prism.ps.PPSBlend_SRC_OVERPeer",
            "com.sun.scenario.effect.impl.prism.ps.PPSColorAdjustPeer",
            "com.sun.scenario.effect.impl.prism.ps.PPSPhongLighting_DISTANTPeer",
            // Prism 内在 peer（prism）
            "com.sun.scenario.effect.impl.prism.PrCropPeer",
            "com.sun.scenario.effect.impl.prism.PrReflectionPeer",
            // 软件回退：JSW（sw.java）与 SSE（sw.sse）
            "com.sun.scenario.effect.impl.sw.java.JSWLinearConvolveShadowPeer",
            "com.sun.scenario.effect.impl.sw.java.JSWBoxShadowPeer",
            "com.sun.scenario.effect.impl.sw.sse.SSELinearConvolveShadowPeer");

    private static final Path METADATA = Path.of(
            System.getProperty("basedir", System.getProperty("user.dir")),
            "src", "main", "resources", "META-INF", "native-image",
            "com.acanx.module.aha", "aha-desktop", "reachability-metadata.json");

    /**
     * JNI 可达类清单（issue #37）。
     *
     * <p>与反射不同：Glass / 字体 / 几何的 native 代码用 {@code JNIEnv->FindClass}
     * 按名字查类，而 native-image 只允许「JNI accessible」的类被查到——
     * 未注册时抛 {@code NoClassDefFoundError: java/lang/Runnable}（栈顶在
     * {@code JNIFunctions$Support.findClassInClassRegistries}），随后 native 层
     * 拿到空引用继续跑，直接 segfault。</p>
     *
     * <p>清单不是猜的：是对 openjfx 三平台 native 源码里所有 {@code FindClass}
     * 字面量的静态扫描（含 font / fontpath_linux 的动态名）。每个类的成员访问
     * 都在它自己身上声明，故统一用 {@code allDeclared*} 全量，避免签名跨版本漂移。</p>
     */
    private static final Path JNI_CONFIG = Path.of(
            System.getProperty("basedir", System.getProperty("user.dir")),
            "src", "main", "resources", "META-INF", "native-image",
            "com.acanx.module.aha", "aha-desktop", "jni-config.json");

    /**
     * 平台实现类的 JNI 成员查找（issue #39）。
     *
     * <p>与 issue #37 不同：#37 是 native 用 {@code FindClass} 按名字查**类**；
     * 这一类是 native 拿到 Java 侧传入的 {@code jclass}（平台子类本身），
     * 再用 {@code GetMethodID} / {@code GetFieldID} 查它**自己声明**的成员。
     * 基础类（{@code Window} / {@code View} / {@code Pixels} …）早就在册，
     * 但平台子类（{@code WinWindow} …）从未登记——于是
     * {@code WinWindow._initIDs} 在
     * {@code GetMethodID(cls, "notifyMoving", "(IIIIFFIIIIIII)[I")} 处抛
     * {@code NoSuchMethodError}，启动即崩。</p>
     *
     * <p>清单来自对 openjfx 三平台 native 源码里所有 {@code GetMethodID} /
     * {@code GetFieldID} 的静态扫描：凡是 native 以「本类 jclass」为参数查成员的类型，
     * 逐个登记；三平台共用一份清单，缺席平台只产生无害 warning。</p>
     */
    private static final List<String> PLATFORM_JNI_INIT_CLASSES = List.of(
            // Windows（issue #39 的直接报错点：WinWindow.notifyMoving）
            "com.sun.glass.ui.win.WinWindow",
            "com.sun.glass.ui.win.WinView",
            "com.sun.glass.ui.win.WinPixels",
            "com.sun.glass.ui.win.WinCursor",
            "com.sun.glass.ui.win.WinSystemClipboard",
            "com.sun.glass.ui.win.WinDnDClipboard",
            "com.sun.glass.ui.win.WinMenuImpl",
            "com.sun.glass.ui.win.WinGestureSupport",
            "com.sun.glass.ui.win.WinCommonDialogs",
            "com.sun.glass.ui.win.WinAccessible",
            "com.sun.glass.ui.win.WinTextRangeProvider",
            // macOS
            "com.sun.glass.ui.mac.MacWindow",
            "com.sun.glass.ui.mac.MacView",
            "com.sun.glass.ui.mac.MacPixels",
            "com.sun.glass.ui.mac.MacCursor",
            "com.sun.glass.ui.mac.MacCommonDialogs",
            "com.sun.glass.ui.mac.MacFileNSURL",
            "com.sun.glass.ui.mac.MacGestureSupport",
            "com.sun.glass.ui.mac.MacMenuDelegate",
            "com.sun.glass.ui.mac.MacPasteboard",
            "com.sun.glass.ui.mac.MacTimer",
            "com.sun.glass.ui.mac.MacAccessible",
            // 三平台共用的嵌套事件循环（native 用 Class.forName + GetMethodID 查）
            "com.sun.glass.ui.EventLoop");

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
    void reachabilityMetadataRegistersPrismEffectPeers() throws IOException {
        String json = readMetadata();
        for (String type : PRISM_EFFECT_PEERS) {
            assertThat(json)
                    .as("%s 由 Renderer.getPeerInstance 用动态类名反射加载（PPS/Pr/JSW/SSE<name>Peer）；"
                            + "未登记时渲染到该效果的控件会抛 Could not create peer（issue #41）", type)
                    .contains("\"" + type + "\"");
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
    void reachabilityMetadataRegistersTracingAgentDiscoveredTypes() throws IOException {
        String json = readMetadata();
        // 这些类型是 tracing agent 在真实运行（WSLg + GraalVM）中采集到的，
        // 是「只覆盖启动链路」时遗漏的一类：运行期反射 + 平台实现类 + ServiceLoader provider。
        for (String type : List.of(
                "javafx.scene.Node",
                "com.sun.glass.ui.gtk.GtkView",
                "com.acanx.module.aha.tool.FileToolProvider")) {
            assertThat(json)
                    .as("%s 由 tracing agent 在真实运行中采集到（启动链路清单曾漏掉），必须注册", type)
                    .contains("\"" + type + "\"");
        }
    }

    @Test
    void jniConfigRegistersClassLookupsFromJavafxNativeCode() throws IOException {
        assertThat(JNI_CONFIG).as("JNI 可达类清单（issue #37）").exists();
        String json = Files.readString(JNI_CONFIG);
        for (String type : List.of(
                // Glass 启动链路（Windows 的报错点）
                "java.lang.Runnable",
                "java.lang.Object",
                "java.util.Collections",
                "java.util.HashMap",
                "javafx.scene.paint.Color",
                // 平台实现类（三平台各一份）
                "com.sun.glass.ui.win.WinVariant",
                "com.sun.glass.ui.gtk.GtkView",
                "com.sun.glass.ui.mac.MacVariant",
                // 字体（一显示文字就会用到）
                "com.sun.javafx.font.directwrite.DWRITE_MATRIX",
                "com.sun.javafx.font.freetype.FT_GlyphSlotRec",
                "com.sun.javafx.font.FontConfigManager$FcCompFont",
                // 几何
                "com.sun.javafx.geom.Path2D")) {
            assertThat(json)
                    .as("%s 由 JavaFX native 代码用 FindClass 查找；未注册时原生镜像报"
                            + " NoClassDefFoundError 并随后 segfault（issue #37）", type)
                    .contains("\"" + type + "\"");
        }
    }

    @Test
    void jniConfigRegistersPlatformInitClasses() throws IOException {
        String json = Files.readString(JNI_CONFIG);
        for (String type : PLATFORM_JNI_INIT_CLASSES) {
            assertThat(json)
                    .as("%s 的 native _initIDs 用 GetMethodID / GetFieldID 查它自己声明的成员；"
                            + "未登记时原生镜像一启动就 NoSuchMethodError（issue #39）", type)
                    .contains("\"" + type + "\"");
            assertThat(entryFor(json, type))
                    .as("%s 必须用 allDeclared* 全量登记（native 按名字查成员，签名跨版本会漂移）", type)
                    .contains("allDeclaredMethods");
        }
    }

    @Test
    void jniConfigIsAnArrayWithoutDuplicateEntries() throws IOException {
        assertThat(JNI_CONFIG).as("JNI 可达类清单（issue #37）").exists();
        String json = Files.readString(JNI_CONFIG);
        assertThat(json.stripLeading()).as("jni-config.json 顶层必须是数组").startsWith("[");
        Matcher matcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        assertThat(names).as("jni-config.json 不能是空数组").isNotEmpty();
        assertThat(new HashSet<>(names))
                .as("jni 条目重复注册是无意义噪声（后一条覆盖前一条），请合并")
                .hasSameSizeAs(names);
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
