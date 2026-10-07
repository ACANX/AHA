package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.cli.tty.ExternalEditor;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.MemoryConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import com.acanx.module.aha.core.service.LocalAgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话内命令 {@code /memory} 测试。
 *
 * <p>身份文件的查找以工作目录为起点，因此这里把 {@code user.dir} / {@code AHA_HOME}
 * 指向临时目录——否则会走到真实用户目录、甚至本仓库根（本仓库就有 {@code AGENTS.md}），
 * 测试会依赖运行环境。</p>
 *
 * <p>重点钉住两条容易回退的约定：<b>默认写用户级</b>（个人偏好不该落进版本库）、
 * <b>用户级与项目级叠加生效</b>（写用户级不该被项目级遮蔽）。</p>
 *
 * @since 0.1.0
 */
class MemorySessionCommandTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private LocalAgentService service;
    private AhaConfig config;
    private String savedUserDir;
    private String savedUserHome;
    private String savedAhaHome;
    private String savedEditor;

    @BeforeEach
    void setUp() {
        savedUserDir = System.getProperty("user.dir");
        savedUserHome = System.getProperty("user.home");
        savedAhaHome = System.getProperty("AHA_HOME");
        savedEditor = System.getProperty(ExternalEditor.EDITOR_PROPERTY);

        System.setProperty("user.dir", tempDir.toString());
        System.setProperty("user.home", tempDir.resolve("home").toString());
        System.setProperty("AHA_HOME", tempDir.resolve("home").resolve(".aha").toString());

        config = new AhaConfig(
                new LlmConfig("DeepSeek",
                        Map.of("DeepSeek", new ProviderConfig("openai-compatible",
                                "https://example.com/v1", "${AHA_API_KEY_X}", "deepseek-chat",
                                60, 2, new RateLimitConfig(10, 0), Map.of())),
                        null, null),
                new MemoryConfig("sqlite", tempDir.resolve("s.db").toString(), 50),
                null, null, null, null);
        service = new LocalAgentService(config);
    }

    @AfterEach
    void tearDown() {
        restore("user.dir", savedUserDir);
        restore("user.home", savedUserHome);
        restore("AHA_HOME", savedAhaHome);
        restore(ExternalEditor.EDITOR_PROPERTY, savedEditor);
        if (service != null) {
            service.shutdown();
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private SessionContext context(String sessionId) {
        return new SessionContext(sessionId, service, config,
                new PrintStream(buffer, true, StandardCharsets.UTF_8), "builtin", 0);
    }

    private SessionCommand.Outcome dispatch(String sessionId, String input) {
        return new SessionCommandRegistry().dispatch(context(sessionId), input).orElseThrow();
    }

    private String newSession() {
        return service.createSession(new SessionConfig("deepseek-chat", "你是 AHA。", Map.of()));
    }

    private String sessionPrompt(String sessionId) {
        return service.sessionManager().get(sessionId).orElseThrow().systemPrompt();
    }

    /** 用户级身份文件（默认写入目标）。 */
    private Path userFile() {
        return tempDir.resolve("home").resolve(".aha").resolve("AHA.md");
    }

    /** 项目级身份文件名（取候选列表第一个，与 Agent.PromptFiles 保持一致）。 */
    private static final String PROJECT_NAME = "AHA.md";

    /** 项目级身份文件（工作目录 = tempDir）。 */
    private Path projectFile() {
        return tempDir.resolve(PROJECT_NAME);
    }

    // ── view ────────────────────────────────────────────────────────────

    @Test
    void viewShowsSourceAndInjectedText() throws Exception {
        String sessionId = newSession();
        // 自己写一份用户级身份，而不是指望「环境里恰好没有身份文件」：
        // Windows 的临时目录位于主目录之下，开发机主目录里的 AHA.md 会被“就近查找”命中，
        // 而 /tmp 在 Linux 上不居于 /root 之下，同一个用例因此只在 Windows 上红。
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "你是 AHA。");

        dispatch(sessionId, "/memory");

        assertThat(output())
                .contains("身份")
                .contains("内置默认身份")
                .contains("你是 AHA。");
    }

    // ── files ───────────────────────────────────────────────────────────

    @Test
    void filesListsUserLevelFirst() throws IOException {
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "用户级身份");
        String sessionId = newSession();

        dispatch(sessionId, "/memory files");

        assertThat(output())
                .contains("候选")
                .contains("用户级 + 项目级均注入")
                .contains(userFile() + "（生效，5 字符）");
    }

    // ── append：默认用户级 ───────────────────────────────────────────────

    @Test
    void appendWritesUserLevelByDefault() {
        String sessionId = newSession();

        dispatch(sessionId, "/memory append 记住：所有回复用中文");

        // 默认必须落用户级：写进项目 AGENTS.md 会被提交、影响协作者
        assertThat(Files.exists(userFile())).isTrue();
        assertThat(Files.exists(projectFile())).isFalse();
        assertThat(read(userFile())).contains("记住：所有回复用中文");
        assertThat(output()).contains("已新建").contains("用户级").contains("下一轮请求即生效");
        assertThat(sessionPrompt(sessionId)).isEqualTo("记住：所有回复用中文");
        assertThat(service.sessionManager().get(sessionId).orElseThrow().extras())
                .containsEntry(MemorySessionCommand.SOURCE_KEY, "file:" + userFile());
    }

    @Test
    void appendCreatesUserLevelFileWhenMissing() {
        assertThat(Files.exists(userFile())).isFalse();

        dispatch(newSession(), "/memory append 个人偏好");

        assertThat(Files.exists(userFile())).isTrue();
        assertThat(output()).contains("已新建");
    }

    @Test
    void appendWithProjectFlagUsesProjectFile() {
        String sessionId = newSession();

        dispatch(sessionId, "/memory append --project 项目约定");

        assertThat(read(projectFile())).contains("项目约定");
        // 默认文件名跟随 Agent.PromptFiles 的第一个候选，不再硬编码 AGENTS.md
        assertThat(Files.exists(userFile())).isFalse();
        assertThat(output()).contains("项目级");
    }

    @Test
    void projectTargetPrefersExistingCandidate() throws IOException {
        // 项目里已有 AGENTS.md 就继续用那个，不新建 AHA.md
        Files.writeString(tempDir.resolve("AGENTS.md"), "已有项目约定");
        String sessionId = newSession();

        dispatch(sessionId, "/memory append --project 追加一句");

        assertThat(read(tempDir.resolve("AGENTS.md"))).contains("追加一句");
        assertThat(Files.exists(projectFile())).isFalse();
    }

    @Test
    void appendWithNameFlagPicksTheFileName() {
        String sessionId = newSession();

        dispatch(sessionId, "/memory append --name AGENTS.md 写进 AGENTS 而非 AHA");

        // 用户要能明确控制落到 AGENTS.md 还是 AHA.md
        assertThat(read(tempDir.resolve("home").resolve(".aha").resolve("AGENTS.md")))
                .contains("写进 AGENTS 而非 AHA");
        assertThat(Files.exists(userFile())).isFalse();
    }

    @Test
    void appendWithFileFlagUsesGivenPath() {
        Path custom = tempDir.resolve("custom/notes.md");
        String sessionId = newSession();

        dispatch(sessionId, "/memory append --file " + custom + " 自定义位置");

        assertThat(read(custom)).contains("自定义位置");
        assertThat(output()).contains("指定路径");
    }

    @Test
    void appendWithoutTextIsRejected() {
        String sessionId = newSession();

        dispatch(sessionId, "/memory append");

        assertThat(output()).contains("[error]").contains("用法 /memory append");
        assertThat(Files.exists(userFile())).isFalse();
    }

    // ── 用户级 + 项目级叠加 ──────────────────────────────────────────────

    @Test
    void userLevelAndProjectLevelAreBothInjected() throws IOException {
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "个人偏好：一律用简体中文");
        Files.writeString(projectFile(), "项目约定：用 Maven 构建");
        String sessionId = newSession();

        dispatch(sessionId, "/memory reload");

        String prompt = sessionPrompt(sessionId);
        // 写用户级不该被项目级遮蔽——这是「默认写全局」能成立的前提
        assertThat(prompt).contains("个人偏好：一律用简体中文").contains("项目约定：用 Maven 构建");
        assertThat(prompt.indexOf("个人偏好")).isLessThan(prompt.indexOf("项目约定"));
        // 来源必须如实反映「两份叠加」，只报项目级会让用户以为自己的全局记忆没生效
        assertThat(output()).contains("用户级 + 项目级");
    }

    @Test
    void appendingToUserLevelStillTakesEffectWhenProjectFileExists() throws IOException {
        Files.writeString(projectFile(), "项目约定");
        String sessionId = newSession();

        dispatch(sessionId, "/memory append 个人偏好");

        assertThat(sessionPrompt(sessionId))
                .contains("个人偏好")
                .contains("项目约定");
    }

    // ── reload ──────────────────────────────────────────────────────────

    @Test
    void reloadPicksUpManualEdits() throws IOException {
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "手改后的身份文本");
        String sessionId = newSession();

        dispatch(sessionId, "/memory reload");

        assertThat(output()).contains("刷新").contains("生效");
        assertThat(sessionPrompt(sessionId)).isEqualTo("手改后的身份文本");
    }

    @Test
    void reloadReportsWhenContentIsUnchanged() throws IOException {
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "同样的内容");
        String sessionId = newSession();
        dispatch(sessionId, "/memory reload");

        buffer.reset();
        dispatch(sessionId, "/memory reload");

        assertThat(output()).contains("内容无变化");
    }

    @Test
    void reloadWithoutAnyFileFallsBackToBuiltin() {
        String sessionId = newSession();

        dispatch(sessionId, "/memory reload");

        // 没有任何身份文件时退回内置身份，而不是把会话清空
        assertThat(sessionPrompt(sessionId)).contains("AHA");
    }

    // ── edit ────────────────────────────────────────────────────────────

    @Test
    void editCreatesSkeletonAndRefreshesSession() throws IOException {
        System.setProperty(ExternalEditor.EDITOR_PROPERTY, noopEditor().toString());
        String sessionId = newSession();

        dispatch(sessionId, "/memory edit");

        assertThat(output()).contains("新建").contains("编辑器已退出（退出码 0）").contains("刷新");
        assertThat(read(userFile())).contains("# Agent 身份");
        assertThat(sessionPrompt(sessionId)).contains("# Agent 身份");
    }

    @Test
    void editUsesExistingUserLevelFile() throws IOException {
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "已有身份");
        System.setProperty(ExternalEditor.EDITOR_PROPERTY, noopEditor().toString());
        String sessionId = newSession();

        dispatch(sessionId, "/memory edit");

        assertThat(output()).doesNotContain("新建");
        assertThat(sessionPrompt(sessionId)).isEqualTo("已有身份");
    }

    // ── 与其它命令的一致性 ──────────────────────────────────────────────

    @Test
    void sourceIsReflectedBySessionAndContextCommands() {
        String sessionId = newSession();
        dispatch(sessionId, "/memory append 补充约定");

        buffer.reset();
        dispatch(sessionId, "/memory view");
        assertThat(output()).contains(userFile().toString());

        buffer.reset();
        dispatch(sessionId, "/session");
        assertThat(output()).contains("身份");

        buffer.reset();
        dispatch(sessionId, "/context");
        assertThat(output()).contains("身份");
    }

    @Test
    void viewListsGlobalFirstEvenWhenProjectFileExists() throws IOException {
        Files.createDirectories(userFile().getParent());
        Files.writeString(userFile(), "个人偏好");
        Files.writeString(projectFile(), "项目约定");
        String sessionId = newSession();
        dispatch(sessionId, "/memory reload");

        buffer.reset();
        dispatch(sessionId, "/memory view");

        // 回归：此前只打印合并后的正文且来源只报优先级最高那份，
        // 看起来就像「记忆只有项目的 AGENTS.md」，全局那份完全看不见
        assertThat(output()).contains("── 用户级").contains("── 项目级");
        assertThat(output().indexOf("── 用户级")).isLessThan(output().indexOf("── 项目级"));
    }

    @Test
    void unknownSubcommandIsRejected() {
        String sessionId = newSession();

        dispatch(sessionId, "/memory bogus");

        assertThat(output()).contains("无法识别的子命令").contains("/memory [view|files|edit|append");
    }

    /**
     * 读取文件文本。
     *
     * @param file 文件
     * @return 文本
     */
    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("读取失败: " + file, e);
        }
    }

    /**
     * 造一个立刻退出的“编辑器”，避免单测真的打开交互式编辑器。
     *
     * @return 脚本路径
     * @throws IOException 写入失败
     */
    private Path noopEditor() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path script = tempDir.resolve(windows ? "noop-editor.bat" : "noop-editor.sh");
        Files.writeString(script,
                windows ? "@echo off\r\nexit /b 0\r\n" : "#!/bin/sh\nexit 0\n",
                StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }
}
