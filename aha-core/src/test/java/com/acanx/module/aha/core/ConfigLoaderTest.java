package com.acanx.module.aha.core;

import com.acanx.module.aha.common.exception.ConfigException;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConfigLoader} 测试。
 *
 * @since 0.1.0
 */
class ConfigLoaderTest {

    /**
     * 不存在的模型配置路径。
     *
     * <p>{@code ConfigLoader.load(Path)} 会合并模型配置，而默认路径指向
     * <b>开发者用户级</b>的 {@code ~/.aha/Model.yml}——测试若依赖它，就会随
     * 本机配置（甚至残留的旧供应商 ID）而变。此处显式传入不存在的路径，
     * 使配置回退到 classpath 的 {@code ModelDefault.yml}，从而与环境无关。</p>
     */
    private static final Path NO_MODEL = Path.of("target", "no-such-Model.yml");

    private static Path resource(String name) {
        return Path.of("src/test/resources/Configs", name);
    }

    @Test
    void loadsValidPascalCaseConfig() {
        AhaConfig config = ConfigLoader.load(resource("ValidConfig.yaml"), NO_MODEL);
        assertThat(config.llm().defaultProvider()).isEqualTo("OpenAI");
        assertThat(config.llm().providers()).containsKey("OpenAI");
        // 下列取自主配置，不受模型配置合并影响
        assertThat(config.memory().storage()).isEqualTo("sqlite");
        assertThat(config.memory().path()).isEqualTo("./target/test/Aha.db");
        assertThat(config.memory().maxContextEntries()).isEqualTo(100);
    }

    @Test
    void rejectsNonPascalCaseFieldName() {
        assertThatThrownBy(() -> ConfigLoader.load(resource("InvalidFieldName.yaml"), NO_MODEL))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("PascalCase");
    }
}
