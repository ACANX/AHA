package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CodeHighlighter} 测试（issue #336）。
 *
 * <p>断言集中在「能区分」与「不破坏内容」：关键字 / 字符串 / 注释 / 数字有颜色，
 * 未知语言与无色终端原样返回，行数与原文一致。</p>
 *
 * @since 0.1.5
 */
class CodeHighlighterTest {

    private static final Style COLOR = Style.of(ColorDepth.ANSI256);

    @Test
    void javaHighlightsKeywordsStringsCommentsNumbers() {
        assertThat(CodeHighlighter.highlight("public class A {", "java", COLOR))
                .contains("public").contains("class").contains("\u001B[");
        assertThat(CodeHighlighter.highlight("String s = \"x\";", "java", COLOR))
                .contains("\"x\"").contains("\u001B[");
        assertThat(CodeHighlighter.highlight("// note", "java", COLOR))
                .contains("// note").contains("\u001B[");
        assertThat(CodeHighlighter.highlight("int n = 42;", "java", COLOR))
                .contains("42").contains("\u001B[");
    }

    @Test
    void eachSupportedLanguageHighlightsSomething() {
        assertThat(CodeHighlighter.highlight("def f():", "python", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("const x = 1;", "javascript", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("interface A {}", "typescript", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("{\"a\": true}", "json", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("key: true", "yaml", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("echo hi", "shell", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("SELECT * FROM t", "sql", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("<a href=\"x\">", "xml", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("func main() {}", "go", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("fn main() {}", "rust", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("public class A {}", "csharp", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("int main() { return 0; }", "c", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("class A {};", "cpp", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("<?php echo 1;", "php", COLOR)).contains("\u001B[");
        assertThat(CodeHighlighter.highlight("def hello", "ruby", COLOR)).contains("\u001B[");
    }

    @Test
    void unknownLanguageFallsBackToPlainText() {
        String line = "fun main() { println(\"hi\") }";

        assertThat(CodeHighlighter.highlight(line, "kotlin", COLOR)).isEqualTo(line);
        assertThat(CodeHighlighter.highlight(line, null, COLOR)).isEqualTo(line);
        assertThat(CodeHighlighter.highlight(line, "", COLOR)).isEqualTo(line);
    }

    @Test
    void noColorProducesNoAnsi() {
        String line = "public class A";

        assertThat(CodeHighlighter.highlight(line, "java", Style.of(false))).isEqualTo(line);
    }

    @Test
    void lineContentAndCountArePreserved() {
        String line = "public class A { // x";

        String highlighted = CodeHighlighter.highlight(line, "java", COLOR);

        assertThat(highlighted).doesNotContain("\n");
        assertThat(highlighted.replaceAll("\u001B\\[[0-9;]*m", "")).isEqualTo(line);
    }
}
