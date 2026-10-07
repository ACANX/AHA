package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.cli.tty.ExternalEditor;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.AgentConfig;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 会话内命令 {@code /memory}：查看与编辑 Agent 身份文件
 * （用户级 {@code ~/.aha/AHA.md}、项目级 {@code AGENTS.md} / {@code AHA.md}）。
 *
 * <p>对应 Claude Code 里 {@code /memory} 编辑 {@code CLAUDE.md} 的那套用法：把
 * 「我是谁、在这个项目里怎么做事」写进可编辑的 Markdown 文件，AHA 启动时读入并作为
 * system 消息注入（每个来源各自成条，不拼接）。之所以从 {@code /prompt} 改名：{@code prompt} 与「系统提示词配置」
 * 容易混为一谈，而 {@code memory} 与 Claude 的习惯一致，用户一眼能猜到是干什么的。</p>
 *
 * <p><b>写入目标默认是用户级</b>。会话里的临时追加多半是个人偏好（「一律用简体中文」这类），
 * 写进项目的 {@code AGENTS.md} 会被提交、影响所有协作者——此前的实现默认写「当前生效文件」，
 * 结果就是把个人偏好写进了版本库。</p>
 *
 * <p><b>用户级与项目级叠加生效</b>（见 {@code SystemPromptLoader}），因此写用户级不会被
 * 项目级遮蔽；先用户级、后项目级，更具体的项目约定在后面。</p>
 *
 * <p><b>生效时机</b>：{@code AgentEngine.prepare} 每轮都按会话配置重拼 system 消息，
 * 因此只要刷新会话配置，<b>下一轮即生效</b>，不必重开会话。反过来，用别的编辑器直接改文件
 * 不会影响已有会话，必须 {@code /memory reload}。</p>
 *
 * <p><b>权限边界</b>：身份文件定义 Agent 自身的行为约定，因此这组命令只由用户输入触发。
 * 会话内命令不做模型调用分派，模型无法自行改写自己的指令。</p>
 *
 * @since 0.1.0
 */
final class MemorySessionCommand implements SessionCommand {

    /** 会话配置 Extras 中记录身份来源的键名。 */
    static final String SOURCE_KEY = "PromptSource";

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public String usage() {
        return "/memory [view|files|edit|append <文本>|reload]"
                + " [--user|--project|--file <路径>|--name <文件名>]";
    }

    @Override
    public String description() {
        return "查看或编辑 Agent 身份文件（用户级 AHA.md / 项目级 AGENTS.md）";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        String action = args.isEmpty() ? "view" : args.get(0).toLowerCase(Locale.ROOT);
        Options options = Options.parse(args.subList(Math.min(1, args.size()), args.size()));
        return switch (action) {
            case "view" -> view(context);
            case "files" -> files(context);
            case "edit" -> edit(context, options);
            case "append" -> append(context, options);
            case "reload" -> reload(context);
            default -> {
                context.out().println("[error] 无法识别的子命令: " + args.get(0));
                context.out().println("       用法 " + usage());
                yield Outcome.CONTINUE;
            }
        };
    }

    // ── 子命令 ──────────────────────────────────────────────────────────

    /**
     * 查看生效身份的正文。
     *
     * @param context 会话上下文
     * @return 继续
     */
    private Outcome view(SessionContext context) {
        PrintStream out = context.out();
        SessionConfig session = context.sessionConfig();
        String text = session == null ? null : session.systemPrompt();

        out.println("身份");
        SessionOutput.field(out, "来源", describeSource(source(context)));
        SessionOutput.field(out, "字符", text == null ? "无" : String.valueOf(text.length()));

        List<Path> files = contributingFiles(context);
        if (files.isEmpty()) {
            out.println();
            out.println(text == null || text.isBlank() ? "（本会话未注入身份文本）" : text);
            return Outcome.CONTINUE;
        }
        // 全局在前：用户最关心的「我的全局记忆写了什么」不该被项目文件盖住。
        // 此前只打印合并后的正文且来源只报优先级最高那份，看起来就像「只有项目 AGENTS.md」。
        for (Path file : files) {
            out.println();
            out.println("── " + scopeOf(file) + "  " + file
                    + "（" + readFile(file).length() + " 字符）");
            out.println(readFile(file));
        }
        return Outcome.CONTINUE;
    }

    /**
     * 列出全部候选身份文件。
     *
     * @param context 会话上下文
     * @return 继续
     */
    private Outcome files(SessionContext context) {
        PrintStream out = context.out();
        Optional<Path> effective = effectiveFile(context);
        out.println("身份文件");
        SessionOutput.field(out, "生效", describeSource(source(context)));
        SessionOutput.field(out, "顺序", String.join(" → ", promptFiles(context.config()))
                + "（用户级 + 项目级均注入，用户级在前；项目级就近优先）");
        out.println("  候选");
        for (Path path : candidatePaths(context.config())) {
            if (effective.isPresent() && effective.get().equals(path)) {
                out.println("    *  " + path + "（生效，" + readFile(path).length() + " 字符）");
            } else if (Files.isRegularFile(path)) {
                out.println("       " + path + "（存在，" + readFile(path).length() + " 字符）");
            } else {
                out.println("       " + path + "（不存在，可写入）");
            }
        }
        return Outcome.CONTINUE;
    }

    /**
     * 唤起外部编辑器并刷新会话。
     *
     * @param context 会话上下文
     * @param options 目标选项
     * @return 继续
     */
    private Outcome edit(SessionContext context, Options options) {
        PrintStream out = context.out();
        Target target = resolveTarget(context, options);
        Path file = target.file();
        SessionOutput.field(out, "范围", target.scope());
        try {
            if (!Files.isRegularFile(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, SystemPromptLoader.IDENTITY_SKELETON, StandardCharsets.UTF_8);
                SessionOutput.field(out, "新建", file.toString());
            }
        } catch (IOException e) {
            out.println("[error] 无法创建身份文件: " + e.getMessage());
            return Outcome.CONTINUE;
        }

        Optional<List<String>> editor = ExternalEditor.command();
        if (editor.isEmpty()) {
            out.println("[error] 未找到可用的外部编辑器（可设置 VISUAL 或 EDITOR）");
            SessionOutput.field(out, "文件", file.toString());
            return Outcome.CONTINUE;
        }
        SessionOutput.field(out, "编辑器", String.join(" ", editor.get()));
        SessionOutput.field(out, "文件", file.toString());

        int code;
        try {
            code = ExternalEditor.open(file, context.handover());
        } catch (IOException | UncheckedIOException e) {
            out.println("[error] 唤起编辑器失败: " + e.getMessage());
            return Outcome.CONTINUE;
        }
        out.println("编辑器已退出（退出码 " + code + "）");
        // 编辑后立即刷新：否则用户会以为改了却没生效（会话里的提示词是创建时固化的）
        return apply(context, file);
    }

    /**
     * 追加内容并刷新会话。
     *
     * @param context 会话上下文
     * @param options 目标选项
     * @return 继续
     */
    private Outcome append(SessionContext context, Options options) {
        PrintStream out = context.out();
        if (options.text().isEmpty()) {
            out.println("[error] 缺少要追加的文本");
            out.println("       用法 /memory append [--user|--project|--file <路径>] <文本>");
            return Outcome.CONTINUE;
        }
        Target target = resolveTarget(context, options);
        Path file = target.file();
        String text = String.join(" ", options.text());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            boolean existed = Files.isRegularFile(file);
            Files.writeString(file, "\n" + text + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            SessionOutput.field(out, existed ? "已追加" : "已新建",
                    text.length() + " 字符 → " + file);
            SessionOutput.field(out, "范围", target.scope());
        } catch (IOException e) {
            out.println("[error] 写入身份文件失败: " + e.getMessage());
            return Outcome.CONTINUE;
        }
        return apply(context, file);
    }

    /**
     * 重新读取身份文件并刷新会话。
     *
     * @param context 会话上下文
     * @return 继续
     */
    private Outcome reload(SessionContext context) {
        return apply(context, null);
    }

    // ── 内部 ────────────────────────────────────────────────────────────

    /**
     * 按当前环境重新解析身份并写入会话配置。
     *
     * <p>必须走解析器而不是直接读被改的那个文件：用户级与项目级是<b>叠加</b>生效的，
     * 只把单个文件的内容塞进会话会把另一份丢掉。</p>
     *
     * @param context 会话上下文
     * @param changed 本次改动的文件，可为 {@code null}（reload）
     * @return 继续
     */
    private Outcome apply(SessionContext context, Path changed) {
        PrintStream out = context.out();
        SystemPromptLoader.ResolvedPrompt resolved = SystemPromptLoader.resolve(null, null,
                workingDir(), promptFiles(context.config()), inlineFallback(context.config()));
        String text = resolved.text();

        SessionConfig current = context.sessionConfig();
        String before = current == null ? null : current.systemPrompt();
        int beforeChars = before == null ? 0 : before.length();

        if (!writeSession(context, text, resolved.source(), resolved.segments())) {
            out.println("[error] 更新会话配置失败，改动未生效");
            return Outcome.CONTINUE;
        }
        if (changed != null) {
            SessionOutput.field(out, "已改", changed.toString());
        }
        if (text.equals(before)) {
            SessionOutput.field(out, "刷新", "内容无变化（" + text.length() + " 字符）");
        } else {
            SessionOutput.field(out, "刷新", beforeChars + " 字符 → " + text.length() + " 字符");
        }
        SessionOutput.field(out, "生效", describeSource(resolved.source()));
        SessionOutput.field(out, "说明", "下一轮请求即生效（system 消息每轮重新拼装）");
        return Outcome.CONTINUE;
    }

    /**
     * 写入会话配置并记录来源。
     *
     * @param context 会话上下文
     * @param text    身份文本
     * @param source  来源描述
     * @return 是否写入成功
     */
    private static boolean writeSession(SessionContext context, String text, String source,
                                        List<String> segments) {
        LocalAgentService local = context.local();
        SessionConfig current = context.sessionConfig();
        if (local == null || current == null) {
            return false;
        }
        Map<String, Object> extras = new LinkedHashMap<>(
                current.extras() == null ? Map.of() : current.extras());
        extras.put(SOURCE_KEY, source);
        extras.put(SystemPromptLoader.SEGMENTS_KEY, segments);
        return local.sessionManager().update(context.sessionId(),
                new SessionConfig(current.model(), text, extras));
    }

    /**
     * 当前身份来源。
     *
     * <p>优先读会话配置 Extras：{@code /memory} 改过之后 {@code SessionContext} 里的字符串
     * 就过期了，而 {@code /context}、{@code /session} 也读同一处，口径必须一致。</p>
     *
     * @param context 会话上下文
     * @return 来源描述
     */
    static String source(SessionContext context) {
        SessionConfig session = context.sessionConfig();
        if (session != null && session.extras() != null) {
            Object value = session.extras().get(SOURCE_KEY);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return context.promptSource();
    }

    /**
     * 把来源描述转成可读文本。
     *
     * @param source 来源描述
     * @return 可读文本；无来源时返回 {@code null}
     */
    static String describeSource(String source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case "builtin" -> "内置默认身份";
            case "existing-session" -> "沿用已有会话";
            case "--system" -> "命令行 --system";
            case "Aha.Agent.SystemPrompt" -> "Aha.Agent.SystemPrompt（内联）";
            default -> {
                if (SystemPromptLoader.isMerged(source)) {
                    yield "用户级 + 项目级（" + SystemPromptLoader.filePaths(source).size() + " 份，叠加）";
                }
                yield source.startsWith("file:") ? source.substring("file:".length()) : source;
            }
        };
    }

    /**
     * 解析写入目标。
     *
     * <p>默认用户级。项目级需要显式 {@code --project}——它会被提交、影响协作者，
     * 不该是顺手一敲的默认结果。</p>
     *
     * @param context 会话上下文
     * @param options 目标选项
     * @return 目标
     */
    private static Target resolveTarget(SessionContext context, Options options) {
        if (options.file() != null) {
            return new Target(expand(options.file()), "指定路径");
        }
        List<String> names = promptFiles(context.config());
        if (options.project()) {
            Path root = SystemPromptLoader.findProjectRoot(workingDir());
            Path base = root != null ? root : workingDir();
            if (options.name() != null) {
                return new Target(base.resolve(options.name()), "项目级（指定文件名）");
            }
            // 项目里已有身份文件就继续用那个，否则用候选名里的第一个
            for (String name : names) {
                Path candidate = base.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return new Target(candidate, "项目级（已有文件）");
                }
            }
            return new Target(base.resolve(names.get(0)), "项目级（新建）");
        }
        // 默认文件名取候选列表的第一个：这样 `Agent.PromptFiles` 换名字时，
        // 读路径与写路径会一起跟着换，不需要改代码
        String name = options.name() != null ? options.name() : names.get(0);
        Path home = SystemPromptLoader.resolveUserHome();
        if (home == null) {
            return new Target(workingDir().resolve(name), "工作目录（无法确定用户级目录）");
        }
        return new Target(home.resolve(name), "用户级（个人全局身份）");
    }

    /**
     * 展开 {@code ~} 前缀。
     *
     * @param value 路径文本
     * @return 路径
     */
    private static Path expand(String value) {
        if (value.startsWith("~")) {
            String home = System.getProperty("user.home");
            if (home != null && !home.isBlank()) {
                String rest = value.substring(1).replaceFirst("^[/\\\\]", "");
                return rest.isEmpty() ? Path.of(home) : Path.of(home, rest);
            }
        }
        return Path.of(value);
    }

    /**
     * 身份文件候选名。
     *
     * @param config 主配置
     * @return 文件名列表
     */
    private static List<String> promptFiles(AhaConfig config) {
        AgentConfig agent = config == null ? null : config.agent();
        List<String> names = agent == null ? null : agent.promptFiles();
        return names == null || names.isEmpty() ? SystemPromptLoader.DEFAULT_PROMPT_FILES : names;
    }

    /**
     * 配置里的内联兜底身份。
     *
     * @param config 主配置
     * @return 内联文本，可为 {@code null}
     */
    private static String inlineFallback(AhaConfig config) {
        AgentConfig agent = config == null ? null : config.agent();
        return agent == null ? null : agent.systemPrompt();
    }

    /**
     * 全部候选路径（含不存在者），按查找顺序排列。
     *
     * @param config 主配置
     * @return 候选路径
     */
    private static List<Path> candidatePaths(AhaConfig config) {
        List<String> names = promptFiles(config);
        List<Path> paths = new ArrayList<>();
        Path home = SystemPromptLoader.resolveUserHome();
        if (home != null) {
            for (String name : names) {
                paths.add(home.resolve(name));
            }
        }
        Path cursor = workingDir();
        while (cursor != null) {
            for (String name : names) {
                paths.add(cursor.resolve(name));
            }
            cursor = cursor.getParent();
        }
        return paths;
    }

    /**
     * 当前生效的身份文件。
     *
     * @param context 会话上下文
     * @return 生效文件；来源不是文件或文件已不存在时为空
     */
    private static Optional<Path> effectiveFile(SessionContext context) {
        List<Path> paths = SystemPromptLoader.filePaths(source(context));
        for (int i = paths.size() - 1; i >= 0; i--) {
            if (Files.isRegularFile(paths.get(i))) {
                return Optional.of(paths.get(i));
            }
        }
        if (!paths.isEmpty()) {
            return Optional.empty();
        }
        String source = source(context);
        if (source != null && source.startsWith("file:")) {
            Path path = Path.of(source.substring("file:".length()));
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        for (Path path : candidatePaths(context.config())) {
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    /**
     * 本次生效所涉及的身份文件（按优先级从低到高：用户级在前）。
     *
     * @param context 会话上下文
     * @return 文件列表
     */
    private static List<Path> contributingFiles(SessionContext context) {
        List<Path> paths = SystemPromptLoader.filePaths(source(context));
        if (!paths.isEmpty()) {
            return paths.stream().filter(Files::isRegularFile).toList();
        }
        List<Path> result = new ArrayList<>();
        for (Path path : candidatePaths(context.config())) {
            if (Files.isRegularFile(path)) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * 判断文件是用户级还是项目级。
     *
     * @param file 文件
     * @return 范围描述
     */
    private static String scopeOf(Path file) {
        return SystemPromptLoader.isUserLevel(file) ? "用户级" : "项目级";
    }

    /**
     * 读取文件文本。
     *
     * @param file 文件
     * @return 文本（已 strip，与 {@link SystemPromptLoader} 一致）
     */
    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    new IOException("无法读取 " + file + ": " + e.getMessage(), e));
        }
    }

    /**
     * 当前工作目录。
     *
     * @return 工作目录
     */
    private static Path workingDir() {
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    /**
     * 写入目标。
     *
     * @param file  目标文件
     * @param scope 范围描述（给用户看的）
     */
    private record Target(Path file, String scope) {
    }

    /**
     * 目标选项。
     *
     * @param project 是否写项目级
     * @param file    显式路径，可为 {@code null}
     * @param name    显式文件名，可为 {@code null}
     * @param text    追加文本
     */
    private record Options(boolean project, String file, String name, List<String> text) {

        /**
         * 解析参数。
         *
         * @param args 参数
         * @return 选项
         */
        static Options parse(List<String> args) {
            boolean project = false;
            String file = null;
            String name = null;
            List<String> text = new ArrayList<>();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                switch (arg) {
                    case "--project", "-p" -> project = true;
                    case "--user", "-u" -> project = false;
                    case "--file", "-f" -> {
                        if (i + 1 < args.size()) {
                            file = args.get(++i);
                        }
                    }
                    case "--name", "-n" -> {
                        if (i + 1 < args.size()) {
                            name = args.get(++i);
                        }
                    }
                    default -> text.add(arg);
                }
            }
            return new Options(project, file, name, text);
        }
    }
}
