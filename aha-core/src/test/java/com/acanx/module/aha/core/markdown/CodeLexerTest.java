package com.acanx.module.aha.core.markdown;

import com.acanx.module.aha.core.markdown.CodeToken.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CodeLexer} 测试（issue #420 / S2.2）。
 *
 * <p>两组断言：**分类正确**（关键字 / 字符串 / 数字 / 注释各归其类）与**原文不变**
 * （所有记号拼接后与输入行逐字节一致）。后者是 CLI 与桌面端共用的底线——
 * 分类错了只是颜色不对，文本错了就是把代码改坏了。</p>
 *
 * @since 0.2.0
 */
class CodeLexerTest {

    private static List<Kind> kinds(String line, String language) {
        return CodeLexer.tokenize(line, language).stream().map(CodeToken::kind).toList();
    }

    private static String join(String line, String language) {
        StringBuilder text = new StringBuilder();
        CodeLexer.tokenize(line, language).forEach(token -> text.append(token.text()));
        return text.toString();
    }

    @Test
    void javaKeywordsStringsCommentsNumbers() {
        assertThat(kinds("public class A {", "java")).contains(Kind.KEYWORD);
        assertThat(kinds("String s = \"x\";", "java")).contains(Kind.STRING);
        assertThat(kinds("// note", "java")).contains(Kind.COMMENT);
        assertThat(kinds("int n = 42;", "java")).contains(Kind.NUMBER);
    }

    @Test
    void jsonBashAndShell() {
        assertThat(kinds("{\"a\": true}", "json")).contains(Kind.STRING, Kind.KEYWORD);
        assertThat(kinds("# note", "bash")).contains(Kind.COMMENT);
        assertThat(kinds("echo hi", "shell")).contains(Kind.KEYWORD);
    }

    @Test
    void unknownLanguageIsASinglePlainToken() {
        assertThat(CodeLexer.tokenize("fun main() {}", "kotlin"))
                .containsExactly(new CodeToken(Kind.PLAIN, "fun main() {}"));
        assertThat(CodeLexer.isSupported("kotlin")).isFalse();
        assertThat(CodeLexer.isSupported("java")).isTrue();
    }

    @Test
    void textIsPreservedByteForByte() {
        String line = "public class A { // x \"y\" 42";

        assertThat(join(line, "java")).isEqualTo(line);
        assertThat(join(line, "kotlin")).isEqualTo(line);
    }

    @Test
    void emptyLineHasNoTokens() {
        assertThat(CodeLexer.tokenize("", "java")).isEmpty();
        assertThat(CodeLexer.tokenize(null, "java")).isEmpty();
    }

    @Test
    void aliasesAndFenceInfoAreAccepted() {
        assertThat(kinds("def f():", "python")).contains(Kind.KEYWORD);
        assertThat(CodeLexer.isSupported("java title=\"x\"")).isTrue();
        assertThat(CodeLexer.isSupported("yml")).isTrue();
    }
}
