package com.acanx.module.aha.desktop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 原生镜像可达性元数据守卫（issue #26）。
 *
 * <p>JavaFX 的启动路径有两处反射，native-image 的 closed-world 分析看不到，
 * 必须在 {@code reachability-metadata.json} 里显式注册，否则运行期直接
 * {@code ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp}：</p>
 *
 * <ol>
 *   <li>{@code Application.launch(String...)} 用 {@code Class.forName(调用类名)} 加载主类
 *       （{@code main} 现已改为显式传 {@code Class}，但元数据仍要注册）；</li>
 *   <li>{@code LauncherImpl} 用 {@code appClass.getConstructor().newInstance()}
 *       实例化 {@code Application} 子类——这条在两种 {@code launch} 写法下都存在。</li>
 * </ol>
 *
 * <p>本测试在 Build / Gate 阶段就能拦住「元数据被删或被改坏」，
 * 不必等可选的原生镜像管线真跑起来（那条管线不上门禁）。</p>
 *
 * @since 0.2.0
 */
class NativeImageMetadataTest {

    private static final String MAIN_CLASS = AhaDesktopApp.class.getName();

    private static final Path METADATA = Path.of(
            System.getProperty("basedir", System.getProperty("user.dir")),
            "src", "main", "resources", "META-INF", "native-image",
            "com.acanx.module.aha", "aha-desktop", "reachability-metadata.json");

    @Test
    void reachabilityMetadataRegistersTheJavafxApplicationClass() throws IOException {
        assertThat(METADATA).as("原生镜像可达性元数据").exists();
        String json = Files.readString(METADATA);

        int at = json.indexOf("\"" + MAIN_CLASS + "\"");
        assertThat(at)
                .as("必须注册主类 %s：JavaFX 用 Class.forName 加载它，"
                        + "LauncherImpl 还用 getConstructor().newInstance() 实例化它——"
                        + "漏注册时原生镜像启动即报 ClassNotFoundException（issue #26）", MAIN_CLASS)
                .isGreaterThanOrEqualTo(0);

        // 只截主类那一个 JSON 对象：构造器注册必须落在它自己的条目里，
        // 不能被别的条目的 allPublicConstructors 蒙混过关。
        int end = json.indexOf('}', at);
        assertThat(end).as("主类条目应当是完整的 JSON 对象").isGreaterThan(at);
        String entry = json.substring(at, end + 1);

        assertThat(entry)
                .as("主类条目必须注册构造器（allDeclaredConstructors 或 allPublicConstructors）")
                .containsAnyOf("allDeclaredConstructors", "allPublicConstructors");
    }
}
