package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileMentions} 测试：候选扫描与插入。
 *
 * <p>用临时目录造一棵小树，不依赖真实项目内容——这样断言可以精确到「哪些该出现、哪些不该」。</p>
 *
 * @since 0.2.0
 */
class FileMentionsTest {

    private static Path tree(Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/java"));
        Files.createDirectories(root.resolve("target/classes"));
        Files.createDirectories(root.resolve(".git"));
        Files.createDirectories(root.resolve("node_modules/pkg"));
        Files.writeString(root.resolve("src/main/java/AgentEngine.java"), "class A {}");
        Files.writeString(root.resolve("src/main/java/AgentConfig.java"), "class B {}");
        Files.writeString(root.resolve("README.md"), "# readme");
        Files.writeString(root.resolve("target/classes/AgentEngine.class"), "junk");
        Files.writeString(root.resolve(".git/config"), "git");
        Files.writeString(root.resolve("node_modules/pkg/AgentEngine.js"), "junk");
        return root;
    }

    @Test
    void candidatesSkipNoiseDirectories(@TempDir Path root) throws Exception {
        tree(root);

        List<String> all = FileMentions.candidates(root, "", 0);

        assertThat(all).contains("README.md", "src/main/java/AgentEngine.java");
        assertThat(all).noneSatisfy(path -> assertThat(path).contains("target/"));
        assertThat(all).noneSatisfy(path -> assertThat(path).contains(".git/"));
        assertThat(all).noneSatisfy(path -> assertThat(path).contains("node_modules/"));
    }

    @Test
    void candidatesMatchBySubstringAndPreferShortPaths(@TempDir Path root) throws Exception {
        tree(root);

        List<String> hits = FileMentions.candidates(root, "agent", 0);

        // 两条路径等长，于是按字母序：Config 在 Engine 前（确定性，不依赖遍历顺序）
        assertThat(hits).containsExactly(
                "src/main/java/AgentConfig.java",
                "src/main/java/AgentEngine.java");
        // 短路径优先这条要单独验：加一个更深的同名文件
        Files.createDirectories(root.resolve("src/main/java/deep/deeper"));
        Files.writeString(root.resolve("src/main/java/deep/deeper/AgentDeep.java"), "x");
        assertThat(FileMentions.candidates(root, "AgentDeep.java", 0))
                .containsExactly("src/main/java/deep/deeper/AgentDeep.java");
    }

    @Test
    void candidatesRespectLimitAndMissingRoot(@TempDir Path root) throws Exception {
        tree(root);

        assertThat(FileMentions.candidates(root, "", 2)).hasSize(2);
        assertThat(FileMentions.candidates(null, "", 0)).isEmpty();
        assertThat(FileMentions.candidates(root.resolve("不存在"), "", 0)).isEmpty();
    }

    @Test
    void tokenFindsTheAtFragmentUnderTheCaret() {
        assertThat(FileMentions.token("看看 @Age", 7)).as("光标紧贴片段末尾").isEqualTo("@Age");
        assertThat(FileMentions.token("看看 @Age", 6)).as("光标在片段中间").isEqualTo("@Ag");
        assertThat(FileMentions.token("@src", 4)).isEqualTo("@src");
        assertThat(FileMentions.token("邮箱 a@b", 7)).isEqualTo("@b");
        // 光标已经越过片段（中间隔着空格）就不再是补全上下文，否则会补到别处
        assertThat(FileMentions.token("看看 @Age 再改", 8)).isNull();
        assertThat(FileMentions.token("没有 at", 7)).isNull();
        assertThat(FileMentions.token(null, 0)).isNull();
        // 只打了一个 @ 也是补全上下文（此时应当把文件都列出来，而不是什么都不弹）
        assertThat(FileMentions.token("@", 1)).isEqualTo("@");
    }

    @Test
    void insertReplacesTheFragmentAndMovesCaret() {
        // 光标在片段末尾（7），于是替换掉 "@Age"
        FileMentions.Insertion result =
                FileMentions.insert("看看 @Age 再改", 7, "src/main/java/AgentEngine.java");

        // 两个空格是对的：mention 自带一个尾空格，原文片段后面原本也还有一个空格
        assertThat(result.text())
                .isEqualTo("看看 @src/main/java/AgentEngine.java  再改");
        assertThat(result.caret()).isEqualTo(result.text().indexOf(" 再改"));
    }

    @Test
    void insertWithoutFragmentInsertsAtCaret() {
        FileMentions.Insertion result = FileMentions.insert("看看 ", 3, "README.md");

        assertThat(result.text()).isEqualTo("看看 @README.md ");
        assertThat(result.caret()).isEqualTo(result.text().length());
    }
}
