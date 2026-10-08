package com.acanx.module.aha.desktop.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 线程契约的静态扫描：主源码中只允许 {@link PlatformFxDispatcher} 触碰 JavaFX 线程 API。
 *
 * <p>这条规则靠“约定”是守不住的——真正的守护方式是一次失败就会报出来的检查。
 * 扫描前先去掉注释，避免 javadoc 里的说明被误判；同时断言
 * {@code PlatformFxDispatcher} 里确实存在调用，防止扫描范围写错导致“假通过”。</p>
 *
 * @since 0.2.0
 */
class FxThreadContractTest {

    /** 禁止出现在别处的调用形态。 */
    private static final List<String> FORBIDDEN = List.of(
            "Platform.runLater(", "Platform.startup(", "Platform.isFxApplicationThread(");

    /** 唯一允许触碰它们的文件。 */
    private static final String ALLOWED_FILE = "PlatformFxDispatcher.java";

    @Test
    void onlyPlatformFxDispatcherTouchesFxThreadApi() throws IOException {
        Path mainSources = Path.of(System.getProperty("basedir", System.getProperty("user.dir")),
                "src", "main", "java");
        // 必须断言目录存在：否则“扫不到文件”会伪装成通过
        assertThat(mainSources).as("主源码目录").exists();

        List<String> offenders = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(mainSources)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(files).isNotEmpty();

        boolean allowedFileSeen = false;
        for (Path file : files) {
            String code = stripComments(Files.readString(file));
            boolean hits = FORBIDDEN.stream().anyMatch(code::contains);
            if (!hits) {
                continue;
            }
            if (ALLOWED_FILE.equals(file.getFileName().toString())) {
                allowedFileSeen = true;
            } else {
                offenders.add(mainSources.relativize(file).toString());
            }
        }

        assertThat(offenders)
                .as("只有 %s 可以直接调用 JavaFX 线程 API；其它位置请经 FxDispatcher 投递", ALLOWED_FILE)
                .isEmpty();
        assertThat(allowedFileSeen)
                .as("%s 里应当存在 JavaFX 线程 API 调用（否则本检查是空转）", ALLOWED_FILE)
                .isTrue();
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
