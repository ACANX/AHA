package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 界面静态资源路径的「**真的能取到**」对账。
 *
 * <p>为什么需要它：把资源从「与 Java 类同包的平面目录」改成 `fxml/` / `css/` / `image/`
 * 子目录时，要改十来处路径字符串。漏改一处**不会有编译错误**——{@code Class.getResource}
 * 拿到字符串、找不到就返回 {@code null}，只有运行期才炸（`FXMLLoader` 抛
 * {@code Location is not set}、图片静默缺失）。</p>
 *
 * <p>更关键的是：**资源白名单是按扩展名匹配的**（`.*\.(png|…|css|fxml|…)$`），
 * 所以「路径写错了」在 {@code FxmlViewChecks#assertResourceWhitelisted} 里**照样通过**。
 * 于是本用例补上它拦不住的那一半：真的去 classpath 上取一次。</p>
 *
 * <p>本用例**不需要 JavaFX 工具包**（只做 classpath 查找），所以能进 CI。</p>
 *
 * @since 0.1.3
 */
class UiResourcesTest {

    @Test
    void everyDeclaredResourceResolvesOnTheClasspath() {
        List<String> missing = new ArrayList<>();
        for (String path : UiResources.all()) {
            if (UiResources.class.getResource(path) == null) {
                missing.add(path);
            }
        }
        assertThat(missing)
                .as("声明在 UiResources 里的每条路径都必须能在 classpath 上取到；"
                        + "取不到说明路径写错或文件没跟着移动（getResource 对写错的路径只返回 null，"
                        + "不抛异常、编译也过）")
                .isEmpty();
    }

    @Test
    void everyDeclaredResourceIsWhitelistedForNativeImage() throws IOException {
        for (String path : UiResources.all()) {
            FxmlViewChecks.assertResourceWhitelisted(UiResources.relative(path));
        }
    }

    @Test
    void everyResourceSitsUnderItsOwnDirectory() {
        for (String path : UiResources.all()) {
            assertThat(path)
                    .as("资源必须落在 fxml/ 或 css/ 或 image/ 之下（2026-10-13 起的目录约定）：%s", path)
                    .matches(".*/(fxml|css|image)/[^/]+$");
        }
    }

    @Test
    void relativeStripsExactlyOneLeadingSlash() {
        assertThat(UiResources.relative("/a/b")).isEqualTo("a/b");
        assertThat(UiResources.relative("a/b")).as("已经是 jar 内形式时保持不变").isEqualTo("a/b");
        assertThat(UiResources.relative("/com/x")).doesNotStartWith("/");
    }

    @Test
    void everyFxmlIsDeclaredHere() {
        List<String> fxml = UiResources.all().stream()
                .filter(path -> path.endsWith(".fxml"))
                .toList();
        assertThat(fxml)
                .as("当前有 7 个 FXML 骨架；新增视图时必须同时加进 UiResources.all()，"
                        + "否则它不会被上面两条对账覆盖")
                .hasSize(7)
                .allSatisfy(path -> assertThat(path).startsWith(UiResources.BASE + "/fxml/"));
    }
}
