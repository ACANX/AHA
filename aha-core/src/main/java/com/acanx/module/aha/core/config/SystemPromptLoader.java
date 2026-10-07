package com.acanx.module.aha.core.config;

import com.acanx.module.aha.common.exception.ConfigException;
import com.acanx.module.aha.common.runtime.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 系统提示词加载器：从约定文件解析 Agent 的身份与角色定义。
 *
 * <p>提示词来源按优先级从高到低：</p>
 * <ol>
 *   <li>显式文本（CLI {@code --system}）</li>
 *   <li>显式文件（CLI {@code --system-file}）</li>
 *   <li>项目根下的提示词文件（默认 {@code AHA.md} → {@code AGENTS.md}）</li>
 *   <li>用户级目录下的同名文件（{@code $AHA_HOME} 或 {@code ~/.aha}）</li>
 *   <li>配置内联文本（{@code Aha.Agent.SystemPrompt}）</li>
 *   <li>内置默认身份声明</li>
 * </ol>
 *
 * <p>每个身份来源<b>各自作为一条独立的 system 消息</b>注入（不拼接成一整段），
 * 与 {@code CLAUDE.md} / {@code .cursorrules}
 * 等工具的惯例一致），用户可以自行编辑文件来定义 Agent 角色。</p>
 *
 * @since 0.1.0
 */
public final class SystemPromptLoader {

    /**
     * 默认提示词文件名，按顺序查找；**同一层级内全部生效**。
     *
     * <p>必须与 {@code AhaDefault.yaml} 的 {@code Agent.PromptFiles} 保持一致——两处不一致时，
     * 「已加载哪些」与「哪些没加载」会各说一套（这个 bug 真实发生过）。有测试钉住这一点。</p>
     */
    public static final List<String> DEFAULT_PROMPT_FILES =
            List.of("AHA.md", "AGENTS.md", "CLAUDE.md");

    /** 项目根判定标志。提示词文件本身也是标志：存在 AHA.md / AGENTS.md 即视为 Agent 工作目录。 */
    private static final List<String> PROJECT_MARKERS =
            List.of(".git", ".aha", "Aha.yaml", "AHA.md", "AGENTS.md");

    /** 新建身份文件时的骨架。 */
    public static final String IDENTITY_SKELETON = """
            # Agent 身份

            在这里定义 AHA 的身份与行为约定。本文件内容会作为一条独立的 system 消息注入。

            ## 约定

            - 
            """;

    /** 会话配置 Extras 中固化身份片段的键名。 */
    public static final String SEGMENTS_KEY = "PromptSegments";

    /** 单个文件来源的前缀。 */
    public static final String FILE_PREFIX = "file:";

    /** 多文件来源的前缀。 */
    public static final String FILES_PREFIX = "files:";

    /** 多文件来源的分隔符。 */
    public static final String FILE_SEPARATOR = ";";

    /** 内置兜底身份声明。 */
    public static final String BUILTIN_IDENTITY = """
            你是 AHA（Agent Harness）驱动的编码助手，在用户的本地工作区中协助完成软件工程任务。

            工作原则：
            - 先理解上下文再行动；不确定时先查阅项目文件，而不是猜测
            - 修改代码后应运行构建或测试验证，不要假设改动一定正确
            - 涉及文件写入、命令执行等有副作用的操作前，先说明意图
            - 遇到与用户既有约定冲突时，指出冲突并询问，不要擅自覆盖
            """;

    /**
     * 运行环境说明块。
     *
     * <p>内置身份声明里没有平台信息，模型只能靠猜。实测它会发出
     * {@code grep ... 2>/dev/null | head -200} 这类 Unix 写法，在 Windows 的
     * {@code cmd.exe} 下必然失败，而报错是
     * 「{@code 'X' is not recognized as an internal or external command}」——
     * 从报错完全看不出「平台不匹配」，排查成本很高。</p>
     *
     * <p>与身份声明分开、由 {@code AgentEngine} 每轮拼装时追加，好处是
     * <b>用户自定义的身份文件不必自己维护这些事实</b>，且内容始终与真实运行环境一致。</p>
     *
     * @return 环境说明块（多行文本）
     */
    public static String environmentBlock() {
        StringBuilder text = new StringBuilder("运行环境（以下均为事实，不必再向用户确认）：");
        text.append("\n- 操作系统：").append(Platform.describe());
        if (Platform.isWindows()) {
            text.append("\n- 命令执行方式：cmd.exe /c（不是 bash / sh）");
            text.append("\n- 可用命令：dir / type / findstr / where / powershell；")
                    .append("没有 head、grep、sed、awk、/dev/null");
            text.append("\n- 路径分隔符：\\（反斜杠）");
            text.append("\n- 需要更强能力时显式调用：powershell -NoProfile -Command \"...\"");
        } else {
            text.append("\n- 命令执行方式：/bin/sh -c");
            text.append("\n- 路径分隔符：/（正斜杠）");
        }
        text.append("\n- 工作目录：").append(Platform.workingDirectory());
        return text.toString();
    }

    private SystemPromptLoader() {
    }

    /**
     * 解析后的系统提示词。
     *
     * @param text   提示词全文
     * @param source 来源描述，用于诊断展示
     */
    public record ResolvedPrompt(String text, String source, java.util.List<String> segments) {

        /**
         * 兼容构造：只有一个片段。
         *
         * @param text   提示词全文
         * @param source 来源描述
         */
        public ResolvedPrompt(String text, String source) {
            this(text, source, text == null || text.isBlank()
                    ? java.util.List.of() : java.util.List.of(text));
        }

        /**
         * 来源是否为实际文件。
         *
         * @return 是否来自文件
         */
        public boolean fromFile() {
            return source != null && source.startsWith("file:");
        }
    }

    /**
     * 按优先级解析系统提示词。
     *
     * @param explicitText    显式文本，可为 {@code null}
     * @param explicitFile    显式文件，可为 {@code null}
     * @param workingDir      工作目录，可为 {@code null}（默认当前目录）
     * @param promptFiles     提示词文件名列表，为空时使用 {@link #DEFAULT_PROMPT_FILES}
     * @param inlineFallback  配置内联文本，可为 {@code null}
     * @return 解析结果，永不为 {@code null}
     */
    public static ResolvedPrompt resolve(String explicitText, Path explicitFile, Path workingDir,
                                         List<String> promptFiles, String inlineFallback) {
        if (explicitText != null && !explicitText.isBlank()) {
            return new ResolvedPrompt(explicitText, "--system");
        }
        if (explicitFile != null) {
            return new ResolvedPrompt(readFile(explicitFile), "file:" + explicitFile);
        }

        List<String> names = promptFiles == null || promptFiles.isEmpty()
                ? DEFAULT_PROMPT_FILES
                : promptFiles;
        Path working = workingDir == null ? Path.of("").toAbsolutePath() : workingDir;

        // 组装规则（顺序即优先级，后加载者更具体）：
        //   1. 用户级目录 —— **该层级的全部候选**，按 PromptFiles 顺序
        //   2. 项目级 —— 从工作目录逐级向上，取第一个命中目录，该目录的**全部候选**
        // 用户级与项目级**叠加**而不是二选一：个人全局偏好（「一律用简体中文」这类）
        // 不该因为项目里恰好有个 AGENTS.md 就整段失效。
        java.util.List<ResolvedPrompt> parts = new java.util.ArrayList<>();
        parts.addAll(readAllNonBlank(resolveUserHome(), names));

        Path cursor = working.toAbsolutePath().normalize();
        while (cursor != null && withinProjectScope(cursor)) {
            java.util.List<ResolvedPrompt> hits = readAllNonBlank(cursor, names);
            if (!hits.isEmpty()) {
                parts.addAll(hits);
                break;
            }
            cursor = cursor.getParent();
        }
        if (!parts.isEmpty()) {
            return join(dedupe(parts));
        }

        if (inlineFallback != null && !inlineFallback.isBlank()) {
            // strip 掉块标量（|）带来的首尾空行：否则 /config 的字数与 /memory view 的对不上
            return new ResolvedPrompt(inlineFallback.strip(), "Aha.Agent.SystemPrompt");
        }
        return new ResolvedPrompt(BUILTIN_IDENTITY, "builtin");
    }

    /**
     * 去重：内容完全相同的文件只保留第一份。
     *
     * <p>同一份约定被复制成 {@code AGENTS.md} 与 {@code CLAUDE.md}（或软链）是常见情况，
     * 不去重会让 system 消息里出现两遍同样的指令。</p>
     *
     * @param parts 候选项
     * @return 去重后的列表，保持原顺序
     */
    private static java.util.List<ResolvedPrompt> dedupe(java.util.List<ResolvedPrompt> parts) {
        java.util.List<ResolvedPrompt> result = new java.util.ArrayList<>(parts.size());
        for (ResolvedPrompt part : parts) {
            boolean duplicated = result.stream().anyMatch(kept -> kept.text().equals(part.text()));
            if (!duplicated) {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * 已知的兼容身份文件名（不在候选列表时给出提示用）。
     *
     * <p>这些是其它 Agent 工具的约定文件名。AHA 不会自动加载它们——{@code Agent.PromptFiles}
     * 才是唯一来源——但<b>存在却没被加载时必须说出来</b>，否则用户会以为已经生效。</p>
     */
    public static final java.util.List<String> KNOWN_COMPAT_FILES =
            java.util.List.of("CLAUDE.md", "CLAUDE.local.md", ".cursorrules", "GEMINI.md", "CODEX.md");

    /**
     * 探测「存在但未列入候选」的兼容身份文件。
     *
     * @param workingDir  工作目录，可为 {@code null}
     * @param promptFiles 实际候选文件名；为空时用 {@link #DEFAULT_PROMPT_FILES}
     * @return 未加载的兼容文件路径（先用户级、后项目级）
     */
    public static java.util.List<Path> unlistedCompatFiles(Path workingDir,
                                                          java.util.List<String> promptFiles) {
        java.util.List<String> names = promptFiles == null || promptFiles.isEmpty()
                ? DEFAULT_PROMPT_FILES
                : promptFiles;
        java.util.List<Path> found = new java.util.ArrayList<>();
        collectCompat(resolveUserHome(), names, found);
        Path cursor = (workingDir == null ? Path.of("") : workingDir).toAbsolutePath().normalize();
        while (cursor != null && withinProjectScope(cursor)) {
            java.util.List<Path> hits = new java.util.ArrayList<>();
            collectCompat(cursor, names, hits);
            if (!hits.isEmpty()) {
                found.addAll(hits);
                break;
            }
            cursor = cursor.getParent();
        }
        return found;
    }

    private static void collectCompat(Path directory, java.util.List<String> names,
                                     java.util.List<Path> found) {
        if (directory == null) {
            return;
        }
        for (String name : KNOWN_COMPAT_FILES) {
            if (names.contains(name)) {
                continue;
            }
            Path candidate = directory.resolve(name);
            if (Files.isRegularFile(candidate)) {
                found.add(candidate);
            }
        }
    }

    /**
     * 合并多份提示词。
     *
     * <p>来源要列出<b>全部</b>参与的文件，不能只报优先级最高那份：否则「用户级 + 项目级叠加」
     * 在界面上会显示成「只有项目级」，用户完全看不到自己那份全局记忆到底有没有生效。</p>
     *
     * @param parts 按优先级从低到高排列
     * @return 合并结果
     */
    private static ResolvedPrompt join(java.util.List<ResolvedPrompt> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        StringBuilder text = new StringBuilder();
        StringBuilder source = new StringBuilder(FILES_PREFIX);
        java.util.List<String> segments = new java.util.ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(parts.get(i).text());
            segments.add(parts.get(i).text());
            if (i > 0) {
                source.append(FILE_SEPARATOR);
            }
            source.append(parts.get(i).source().substring(FILE_PREFIX.length()));
        }
        return new ResolvedPrompt(text.toString(), source.toString(), segments);
    }

    /**
     * 来源描述里是否包含多个身份文件。
     *
     * @param source 来源描述
     * @return 多份时为 {@code true}
     */
    public static boolean isMerged(String source) {
        return source != null && source.startsWith(FILES_PREFIX);
    }

    /**
     * 解析来源描述中的文件路径（按优先级从低到高）。
     *
     * @param source 来源描述
     * @return 文件路径列表；来源不是文件时为空
     */
    public static java.util.List<Path> filePaths(String source) {
        if (source == null) {
            return java.util.List.of();
        }
        String body;
        if (source.startsWith(FILES_PREFIX)) {
            body = source.substring(FILES_PREFIX.length());
        } else if (source.startsWith(FILE_PREFIX)) {
            body = source.substring(FILE_PREFIX.length());
        } else {
            return java.util.List.of();
        }
        java.util.List<Path> paths = new java.util.ArrayList<>();
        for (String part : body.split(FILE_SEPARATOR)) {
            if (!part.isBlank()) {
                paths.add(Path.of(part));
            }
        }
        return paths;
    }

    /**
     * 确保用户级身份文件存在（不存在则按骨架创建）。
     *
     * <p>全局记忆是 AHA 的既定机制，文件不存在时用户既看不到也改不了；这里主动建一个，
     * 让「个人全局偏好」有落点。只动用户级目录，<b>绝不碰项目目录</b>。</p>
     *
     * @param promptFiles 候选文件名，为空时用 {@link #DEFAULT_PROMPT_FILES}
     * @return 用户级身份文件路径；无法确定用户目录或创建失败时返回 {@code null}
     */
    public static Path ensureUserIdentity(java.util.List<String> promptFiles) {
        Path home = resolveUserHome();
        if (home == null) {
            return null;
        }
        java.util.List<String> names = promptFiles == null || promptFiles.isEmpty()
                ? DEFAULT_PROMPT_FILES
                : promptFiles;
        for (String name : names) {
            Path candidate = home.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path file = home.resolve(names.get(0));
        try {
            Files.createDirectories(home);
            Files.writeString(file, IDENTITY_SKELETON, java.nio.charset.StandardCharsets.UTF_8);
            return file;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * 读取目录下全部非空提示词文件（按候选名顺序）。
     *
     * <p>同一层级允许并存多个候选：`AHA.md` 表达 AHA 自己的约定、`AGENTS.md` 表达跨工具约定，
     * 二者并存时都注入，顺序按 {@code PromptFiles}；只取一个会让用户以为写了却没生效。</p>
     *
     * @param directory   目录，可为 {@code null}
     * @param promptFiles 文件名列表
     * @return 命中的文件（按候选名顺序）；无命中时为空列表
     */
    private static java.util.List<ResolvedPrompt> readAllNonBlank(Path directory,
                                                                 java.util.List<String> promptFiles) {
        if (directory == null) {
            return java.util.List.of();
        }
        java.util.List<ResolvedPrompt> hits = new java.util.ArrayList<>();
        for (String name : promptFiles) {
            Path candidate = directory.resolve(name);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            String text = readFile(candidate);
            if (!text.isBlank()) {
                hits.add(new ResolvedPrompt(text, FILE_PREFIX + candidate));
            }
        }
        return hits;
    }

    /**
     * 判断文件是否位于用户级目录下。
     *
     * @param file 文件
     * @return 用户级时为 {@code true}
     */
    public static boolean isUserLevel(Path file) {
        Path home = resolveUserHome();
        if (home == null || file == null) {
            return false;
        }
        return file.toAbsolutePath().normalize().startsWith(home.toAbsolutePath().normalize());
    }

    /**
     * 在目录中查找并读取首个非空提示词文件。
     *
     * <p>空文件（仅空白字符）视为无效来源，继续尝试后续候选。</p>
     *
     * @param directory   目录，可为 {@code null}
     * @param promptFiles 文件名列表
     * @return 解析结果；无有效命中时返回 {@code null}
     */
    private static ResolvedPrompt readFirstNonBlank(Path directory, List<String> promptFiles) {
        if (directory == null) {
            return null;
        }
        for (String name : promptFiles) {
            Path candidate = directory.resolve(name);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            String text = readFile(candidate);
            if (!text.isBlank()) {
                return new ResolvedPrompt(text, "file:" + candidate);
            }
        }
        return null;
    }

    /**
     * 从起始目录向上查找项目根。
     *
     * <p>判定标志：{@code .git}、{@code .aha} 或 {@code Aha.yaml}。</p>
     *
     * @param start 起始目录，可为 {@code null}
     * @return 项目根路径；未找到时返回 {@code null}
     */
    public static Path findProjectRoot(Path start) {
        Path current = (start == null ? Path.of("") : start).toAbsolutePath().normalize();
        while (current != null && withinProjectScope(current)) {
            for (String marker : PROJECT_MARKERS) {
                if (Files.exists(current.resolve(marker))) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 当前目录是否仍在「项目级」查找范围内。
     *
     * <p>向上的项目级查找必须止步于<b>用户主目录</b>：{@code ~/AHA.md} 既绕过了
     * 用户级目录（{@code ~/.aha}），又会让「用户级 / 项目级」的来源标注失真
     * （同一个文件在启动信息里被当成项目级、在 {@code /memory} 里却是别的含义）。</p>
     *
     * <p>这条边界在 Windows 上尤其关键：临时目录位于 {@code %LOCALAPPDATA%}（属于主目录），
     * 因此任何在临时目录里跑的测试都会沿路读到开发机主目录里的身份文件，
     * 甚至把「项目级」文件写到那里去。</p>
     *
     * @param dir 候选目录
     * @return 仍在项目级范围内返回 {@code true}
     */
    private static boolean withinProjectScope(Path dir) {
        return !userHomeDirectories().contains(dir);
    }

    /**
     * 用户主目录的候选集合（已规范化）。
     *
     * <p>同时取 JVM 属性与环境变量：嵌入场景或测试会把 {@code user.home} 指到别处，
     * 而操作系统真正的主目录仍在环境里（{@code USERPROFILE} / {@code HOME}），
     * 后者往往正好是当前目录的祖先，漏掉就会继续向上走出项目范围。</p>
     */
    private static java.util.Set<Path> userHomeDirectories() {
        java.util.Set<Path> homes = new java.util.LinkedHashSet<>();
        for (String value : new String[]{
                System.getProperty("user.home"),
                System.getenv("USERPROFILE"),
                System.getenv("HOME")}) {
            if (value != null && !value.isBlank()) {
                try {
                    homes.add(Path.of(value).toAbsolutePath().normalize());
                } catch (InvalidPathException e) {
                    // 环境变量不是合法路径时忽略
                }
            }
        }
        return homes;
    }

    /**
     * 用户级配置目录：{@code AHA_HOME}，未设置时为 {@code ~/.aha}。
     *
     * <p>取值顺序：系统属性 {@code -DAHA_HOME} → 环境变量 {@code AHA_HOME} → {@code ~/.aha}。
     * 系统属性排在环境变量之前，是为了让测试与集成场景能确定性地覆盖用户目录：
     * 否则开发机上已设置 {@code AHA_HOME} 时，测试会去读写<b>真实</b>用户目录
     * （曾因此在 Windows 上读到自己写的 {@code ~/.aha/AHA.md} 而误判为加载逻辑出错）。</p>
     *
     * @return 用户级目录；无法确定时为 {@code null}
     */
    public static Path resolveUserHome() {
        String home = System.getProperty("AHA_HOME");
        if (home == null || home.isBlank()) {
            home = System.getenv("AHA_HOME");
        }
        if (home != null && !home.isBlank()) {
            return Path.of(home);
        }
        String userHome = System.getProperty("user.home");
        return userHome == null || userHome.isBlank() ? null : Path.of(userHome, ".aha");
    }

    /**
     * 读取提示词文件。
     *
     * @param file 文件路径
     * @return 文件内容
     * @throws ConfigException 读取失败时抛出
     */
    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new ConfigException("SYSTEM_PROMPT_LOAD_FAILED",
                    "无法读取系统提示词文件: " + file, e);
        }
    }

    /**
     * 若给定目录存在提示词文件，返回其路径。
     *
     * @param directory   目录
     * @param promptFiles 文件名列表
     * @return 命中的文件路径
     */
    public static Optional<Path> findPromptFile(Path directory, List<String> promptFiles) {
        if (directory == null) {
            return Optional.empty();
        }
        List<String> names = promptFiles == null || promptFiles.isEmpty()
                ? DEFAULT_PROMPT_FILES
                : promptFiles;
        for (String name : names) {
            Path candidate = directory.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
