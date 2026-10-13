package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FXML 骨架与控制器的一致性对账。
 *
 * <p><strong>不启动 JavaFX</strong>：这里做的全是静态检查——FXML 是文本，控制器是反射可见的类。
 * 单独钉这一条的理由是：FXML 的三类错配在**编译期完全看不出来**——</p>
 *
 * <ol>
 *   <li>{@code fx:id} 与字段名不一致 ⇒ 注入静默失效，运行期该部件是 {@code null}；</li>
 *   <li>节点 {@code id} 与常量不一致 ⇒ 冒烟测试与真机排查的 {@code lookup} 全部落空；</li>
 *   <li>fxml 没被收进原生镜像（或反过来把 {@code .class} 当资源收进去）⇒
 *       「构建全绿、启动即崩」。</li>
 * </ol>
 *
 * <p>GUI 冒烟测试在 CI 上是关闭的（要 {@code -Daha.ui.tests=true}），原生 GUI 冒烟又慢；
 * 把这三类错配压进单元测试，反馈是以秒计的。</p>
 *
 * @since 0.1.3
 */
class DesktopShellFxmlTest {

    /** 骨架资源名（与 {@link DesktopShell} 同包）。 */
    private static final String FXML_RESOURCE = "desktop-shell.fxml";

    private static final Pattern FX_ID = Pattern.compile("fx:id=\"([^\"]+)\"");

    /** 只匹配属性位置的 {@code id="…"}：{@code fx:id="…"} 里那个 {@code id} 前面是冒号，不会命中。 */
    private static final Pattern NODE_ID = Pattern.compile("\\sid=\"([^\"]+)\"");

    @Test
    void fxmlShipsNextToTheController() throws IOException {
        assertThat(readFxml())
                .as("%s 必须与 DesktopShell 同包（FXMLLoader 用相对资源名装载）", FXML_RESOURCE)
                .contains("<BorderPane")
                .contains("fx:id=\"root\"");
    }

    @Test
    void everyFxIdHasAnInjectedField() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String id : fxIds()) {
            Field field = fieldNamed(id);
            if (field == null || field.getAnnotation(javafx.fxml.FXML.class) == null) {
                missing.add(id);
            }
        }
        assertThat(missing)
                .as("FXML 的每个 fx:id 都要有同名的 @FXML 字段——字段名不一致时注入静默失效，"
                        + "该部件在运行期是 null，而编译期一切正常")
                .isEmpty();
    }

    @Test
    void everyInjectedFieldIsUsedByTheFxml() throws IOException {
        Set<String> ids = new TreeSet<>(fxIds());
        Set<String> dead = new TreeSet<>();
        for (Field field : DesktopShell.class.getDeclaredFields()) {
            if (field.getAnnotation(javafx.fxml.FXML.class) != null && !ids.contains(field.getName())) {
                dead.add(field.getName());
            }
        }
        assertThat(dead)
                .as("标了 @FXML 却没有对应 fx:id 的字段是死字段：要么 fxml 里漏了声明，"
                        + "要么字段该删（留着会让人误以为它被注入过）")
                .isEmpty();
    }

    @Test
    void everyNodeIdMatchesAPublicConstant() throws IOException {
        Set<String> allowed = constantValues();
        List<String> unknown = new ArrayList<>();
        for (String id : nodeIds()) {
            if (!allowed.contains(id)) {
                unknown.add(id);
            }
        }
        assertThat(unknown)
                .as("节点 id 必须取自 DesktopShell 的公开常量（冒烟测试与真机排查都按它 lookup）；"
                        + "新增栏 / 部件时先在 DesktopShell 里定义常量，再写进 fxml")
                .isEmpty();
    }

    @Test
    void nativeImageArgsIncludeFxmlWithoutPullingClassesInAsResources() throws IOException {
        Path args = nativeImageArgs();
        assertThat(args).as("native-image 参数清单").exists();

        List<String> includePatterns = new ArrayList<>();
        for (String line : Files.readAllLines(args, StandardCharsets.UTF_8)) {
            String stripped = line.strip();
            if (!stripped.startsWith("#") && stripped.startsWith("-H:IncludeResources=")) {
                includePatterns.add(stripped.substring("-H:IncludeResources=".length()));
            }
        }
        assertThat(includePatterns).as("资源白名单不能为空").isNotEmpty();

        // ① fxml 必须被收进镜像：漏掉的症状是「启动即崩（IOException: Location is not set）」，
        //    而构建全绿——与 .yaml / .obj 那几次是同一类静默失败
        assertThat(matchesAny(includePatterns, "com/acanx/module/aha/desktop/view/" + FXML_RESOURCE))
                .as("desktop-shell.fxml 必须命中某条 -H:IncludeResources，否则原生包启动即崩")
                .isTrue();

        // ② 反过来：绝不能把 JavaFX 的 .class 当资源编进镜像。
        //    com/sun/javafx/reflect/Trampoline.class 一旦可见，JavaFX 的私有加载器
        //    读到字节后会去运行期 defineClass，而 native-image 默认禁止 ⇒ 启动即崩
        //    （姊妹项目 LegionFanControl 的 TS-2610-003 踩的正是这一条）。
        assertThat(matchesAny(includePatterns, "com/sun/javafx/reflect/Trampoline.class"))
                .as("资源白名单不得把 Trampoline.class 收进镜像：JavaFX 会因此走运行期 defineClass，"
                        + "原生镜像默认禁止（TS-2610-003）。要加宽资源规则时，先看这条断言")
                .isFalse();
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (Pattern.compile(pattern).matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> constantValues() {
        Set<String> values = new TreeSet<>();
        for (Field field : DesktopShell.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
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

    private static Field fieldNamed(String name) {
        try {
            return DesktopShell.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static List<String> fxIds() throws IOException {
        return group(FX_ID, readFxml());
    }

    private static List<String> nodeIds() throws IOException {
        return group(NODE_ID, readFxml());
    }

    private static List<String> group(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String readFxml() throws IOException {
        try (InputStream stream = DesktopShell.class.getResourceAsStream(FXML_RESOURCE)) {
            assertThat(stream)
                    .as("%s 必须在 DesktopShell 的同包里（src/main/resources/.../desktop/view/）",
                            FXML_RESOURCE)
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path nativeImageArgs() {
        Path base = Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        return base.getParent().resolve(
                Path.of("aha-desktop-native", "src", "native", "native-image-args.txt"));
    }
}
