package com.acanx.module.aha.desktop.view;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FXML 视图的**可复用静态对账**（不启 JavaFX，纯文本 + 反射）。
 *
 * <p>单独抽出来是因为 FXML 分四批迁移（见 issue #133）：如果每个批次各写一套，
 * 四条规则会长出四份逐渐漂移的副本。这里集中维护，各视图的测试类只提供
 * 「资源名 + 控制器类」，再补自己特有的对账。</p>
 *
 * <p>挡的是**编译期完全看不见**的四类错配（都真实踩过，见 TS-2610-032）：</p>
 * <ol>
 *   <li>{@code fx:id} 与字段名不一致 ⇒ 注入静默失效，运行期是 {@code null}；</li>
 *   <li>元素类型没 {@code <?import?>} ⇒ 运行期 {@code LoadException: Xxx is not a valid type.}；</li>
 *   <li>实例化的类型没登记构造器 ⇒ 原生镜像 {@code MissingReflectionRegistrationError}；</li>
 *   <li>fxml 没进镜像 / {@code Trampoline.class} 被当资源收进去 ⇒ 构建全绿、启动即崩。</li>
 * </ol>
 *
 * @since 0.1.3
 */
final class FxmlViewChecks {

    /** 注入的 {@code fx:id}。 */
    private static final Pattern FX_ID = Pattern.compile("fx:id=\"([^\"]+)\"");

    /** 属性位置的 {@code id="…"}；{@code fx:id="…"} 里那个前面是冒号，不会命中。 */
    private static final Pattern NODE_ID = Pattern.compile("\\sid=\"([^\"]+)\"");

    /** {@code <?import javafx.scene.layout.BorderPane?>} 里的全限定类名。 */
    private static final Pattern IMPORT = Pattern.compile("<\\?import\\s+([\\w.]+)\\s*\\?>");

    /**
     * 元素开始标签里的**类型名**（首字母大写）。
     *
     * <p>只取大写开头是有意的：FXML 里还有一类小写标签是**属性**
     * （{@code <children>} / {@code <items>} / {@code <content>} / {@code <top>} …），
     * 它们由父元素的 getter / setter 解析，不走 import 表。Java 命名惯例下二者不会混淆。</p>
     */
    private static final Pattern ELEMENT = Pattern.compile("<([A-Z][\\w.]*)[\\s/>]");

    private FxmlViewChecks() {
    }

    // ------------------------------------------------------------------ 读取

    static String read(Class<?> owner, String resource) throws IOException {
        try (InputStream stream = owner.getResourceAsStream(resource)) {
            assertThat(stream)
                    .as("%s 必须与 %s 同包（src/main/resources/.../desktop/view/）",
                            resource, owner.getSimpleName())
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<String> fxIds(String fxml) {
        return group(FX_ID, fxml);
    }

    static List<String> nodeIds(String fxml) {
        return group(NODE_ID, fxml);
    }

    /** 简单类名 → 全限定名（来自 {@code <?import?>} 表）。 */
    static Map<String, String> imports(String fxml) {
        Map<String, String> bySimpleName = new TreeMap<>();
        Matcher matcher = IMPORT.matcher(fxml);
        while (matcher.find()) {
            String qualified = matcher.group(1);
            bySimpleName.put(qualified.substring(qualified.lastIndexOf('.') + 1), qualified);
        }
        return bySimpleName;
    }

    /** fxml 里出现的元素类型（简单名，去重）。 */
    static Set<String> elementTypes(String fxml) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = ELEMENT.matcher(fxml);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!name.contains(".")) {
                names.add(name);
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ 对账

    /** 每个元素类型都必须能在 {@code <?import?>} 表里找到。 */
    static void assertElementsImported(String view, String fxml) {
        Map<String, String> imported = imports(fxml);
        assertThat(imported).as("%s 必须有 import 表", view).isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String name : elementTypes(fxml)) {
            if (!imported.containsKey(name)) {
                missing.add(name);
            }
        }
        assertThat(missing)
                .as("%s：FXML 的元素类型名**不走 Class.forName 猜包**，而是查本文件的 import 表；"
                        + "漏一个就是运行期 `LoadException: Xxx is not a valid type.`。"
                        + "实测（TS-2610-032）：原生包构建成功、冒烟还判了通过，双击才报", view)
                .isEmpty();
    }

    /** 每个实例化的类型都必须在元数据里登记构造器。 */
    static void assertConstructorsRegistered(String view, String fxml) throws IOException {
        Map<String, String> imported = imports(fxml);
        String metadata = Files.readString(metadataPath(), StandardCharsets.UTF_8);

        List<String> missing = new ArrayList<>();
        for (String simple : elementTypes(fxml)) {
            String qualified = imported.get(simple);
            if (qualified == null) {
                continue; // 缺 import 由 assertElementsImported 负责报
            }
            String entry = entryForType(metadata, qualified);
            if (entry == null || !entry.contains("allDeclaredConstructors")) {
                missing.add(qualified);
            }
        }
        assertThat(missing)
                .as("%s：FXML 实例化元素类型走的是**反射构造**，native-image 下必须登记构造器；"
                        + "只登记 type / methods / fields 不够。实测（TS-2610-032 第二轮）："
                        + "`MissingReflectionRegistrationError: Cannot reflectively invoke constructor "
                        + "'public javafx.scene.layout.Region()'`", view)
                .isEmpty();
    }

    /** 每个 {@code fx:id} 都要有同名的 {@code @FXML} 字段。 */
    static void assertFxIdsInjected(String view, String fxml, Class<?> controller) {
        List<String> missing = new ArrayList<>();
        for (String id : fxIds(fxml)) {
            Field field = fieldNamed(controller, id);
            if (field == null || field.getAnnotation(javafx.fxml.FXML.class) == null) {
                missing.add(id);
            }
        }
        assertThat(missing)
                .as("%s：每个 fx:id 都要有同名的 @FXML 字段——字段名不一致时注入静默失效，"
                        + "该部件在运行期是 null，而编译期一切正常", view)
                .isEmpty();
    }

    /** 反向：标了 {@code @FXML} 却没被 fxml 用到的字段是死字段。 */
    static void assertInjectedFieldsUsed(String view, String fxml, Class<?> controller) {
        Set<String> ids = new TreeSet<>(fxIds(fxml));
        Set<String> dead = new TreeSet<>();
        for (Field field : controller.getDeclaredFields()) {
            if (field.getAnnotation(javafx.fxml.FXML.class) != null && !ids.contains(field.getName())) {
                dead.add(field.getName());
            }
        }
        assertThat(dead)
                .as("%s：标了 @FXML 却没有对应 fx:id 的字段是死字段——要么 fxml 里漏了声明，"
                        + "要么字段该删（留着会让人误以为它被注入过）", view)
                .isEmpty();
    }

    /**
     * 每个属性位置的 {@code id} 都必须取自 {@code holder} 的公开 String 常量。
     *
     * <p>冒烟测试与真机排查都按 {@code lookup("#" + CONSTANT)} 定位；id 写成字面量时，
     * 常量改了、fxml 没改，所有 lookup 会**静默落空**（不报错，只是什么也找不到）。</p>
     */
    static void assertNodeIdsArePublicConstants(String view, String fxml, Class<?> holder) {
        Set<String> allowed = publicStringConstants(holder);
        Set<String> unknown = new TreeSet<>();
        for (String id : nodeIds(fxml)) {
            if (!allowed.contains(id)) {
                unknown.add(id);
            }
        }
        assertThat(unknown)
                .as("%s：节点 id 必须取自 %s 的公开常量；新增栏 / 部件时先定义常量，再写进 fxml",
                        view, holder.getSimpleName())
                .isEmpty();
    }

    /** {@code holder} 的公开 String 常量取值集合。 */
    static Set<String> publicStringConstants(Class<?> holder) {
        Set<String> values = new TreeSet<>();
        for (Field field : holder.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isPublic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    if (field.get(null) instanceof String text) {
                        values.add(text);
                    }
                } catch (IllegalAccessException e) {
                    throw new AssertionError("读取常量失败：" + field.getName(), e);
                }
            }
        }
        return values;
    }

    /** {@code .fxml} 必须被资源白名单收进镜像，且 {@code Trampoline.class} 不得被收进去。 */
    static void assertResourceWhitelisted(String resourcePath) throws IOException {
        Path args = nativeImageArgs();
        assertThat(args).as("native-image 参数清单").exists();

        List<String> patterns = new ArrayList<>();
        for (String line : Files.readAllLines(args, StandardCharsets.UTF_8)) {
            String stripped = line.strip();
            if (!stripped.startsWith("#") && stripped.startsWith("-H:IncludeResources=")) {
                patterns.add(stripped.substring("-H:IncludeResources=".length()));
            }
        }
        assertThat(patterns).as("资源白名单不能为空").isNotEmpty();

        assertThat(matchesAny(patterns, resourcePath))
                .as("%s 必须命中某条 -H:IncludeResources，否则原生包里读不到它（启动即崩，构建却全绿）",
                        resourcePath)
                .isTrue();

        // com/sun/javafx/reflect/Trampoline.class 一旦作为**资源**可见，JavaFX 的私有加载器
        // 读到字节后会去运行期 defineClass，而 native-image 默认禁止 ⇒ 启动即崩
        // （姊妹项目 LegionFanControl 的 TS-2610-003）。
        assertThat(matchesAny(patterns, "com/sun/javafx/reflect/Trampoline.class"))
                .as("资源白名单不得把 Trampoline.class 收进镜像：JavaFX 会因此走运行期 defineClass，"
                        + "原生镜像默认禁止（TS-2610-003）。要加宽资源规则时，先看这条断言")
                .isFalse();
    }

    // ------------------------------------------------------------------ 工具

    static boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (Pattern.compile(pattern).matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    static Field fieldNamed(Class<?> owner, String name) {
        try {
            return owner.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** 元数据路径（以模块 basedir 为基准）。 */
    static Path metadataPath() {
        return basedir().resolve(Path.of("src", "main", "resources", "META-INF", "native-image",
                "com.acanx.module.aha", "aha-desktop", "reachability-metadata.json"));
    }

    static Path nativeImageArgs() {
        return basedir().getParent().resolve(
                Path.of("aha-desktop-native", "src", "native", "native-image-args.txt"));
    }

    private static Path basedir() {
        return Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
    }

    /** 截取某个 {@code type} 对应的完整 JSON 对象（大括号配平，能处理嵌套的 methods）。 */
    static String entryForType(String metadata, String type) {
        int at = metadata.indexOf("\"" + type + "\"");
        if (at < 0) {
            return null;
        }
        int start = metadata.lastIndexOf('{', at);
        int depth = 0;
        for (int i = start; i < metadata.length(); i++) {
            char c = metadata.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return metadata.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static List<String> group(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
