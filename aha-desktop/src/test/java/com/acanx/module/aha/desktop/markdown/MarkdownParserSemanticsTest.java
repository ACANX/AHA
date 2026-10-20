package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples;
import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples.Sample;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享语义基线的桌面端消费方（issue #409 / tracking #408）。
 *
 * <p>解析器要到 S1.1 才实现，所以本类暂时 {@code @Disabled}；解除时把反射换成直接调用，
 * 并把「断言非空」升级为「断言 AST 类型与文本」。<b>用反射而不是直接引用</b>，
 * 是因为类还不存在时直接引用会编译失败——本类要能先合入、后启用。</p>
 *
 * <p>CLI 侧已有同一份样本的可执行断言（{@code MarkdownSemanticsTest}），
 * 两端因此共用一份语义契约。</p>
 *
 * @since 0.2.0
 */
@Disabled("S1.1 实现 MarkdownParser 后解除：改为直接调用并断言 AST 节点类型")
class MarkdownParserSemanticsTest {

    @Test
    void parserConsumesEveryActiveSample() throws Exception {
        Class<?> parserClass = Class.forName("com.acanx.module.aha.desktop.markdown.MarkdownParser");
        Object parser = parserClass.getDeclaredConstructor().newInstance();
        Method parse = parserClass.getMethod("parse", String.class);

        for (Sample sample : MarkdownSemanticsSamples.active()) {
            Object blocks = parse.invoke(parser, sample.input());
            assertThat(blocks).as(sample.id()).isNotNull();
        }
    }
}
