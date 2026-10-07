package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.common.tool.ToolPermission;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制台工具授权裁决器。
 *
 * <p>交互式对话中，工具需要 {@code WRITE} / {@code EXECUTE} / {@code ADMIN} 权限时
 * 暂停并向用户确认，而不是直接以 {@code PERMISSION_DENIED} 结束——后者只告诉用户
 * “未获自动授权”，既没有确认入口，也看不出如何授权。</p>
 *
 * <p>选项：</p>
 * <ul>
 *   <li>{@code y} —— 允许本次调用</li>
 *   <li>{@code n} 或直接回车 —— 拒绝本次调用</li>
 *   <li>{@code a} —— 本次会话内始终允许该权限（不再询问）</li>
 * </ul>
 *
 * <p>提问必须走 {@link ToolPrompt} 而不是直接读 {@code LineReader}：授权发生在回合线程上，
 * 而 {@code LineReader} 由主线程持有（主线程常驻 {@code readLine}，JLine 的 Ctrl+C
 * 才可靠）。回合线程直接读会与之争用同一把读锁。</p>
 *
 * @since 0.1.0
 */
final class ConsoleToolApprover implements ToolApprover {

    /** 单次会话内始终允许的权限。 */
    private final Set<ToolPermission> sessionAllowed = ConcurrentHashMap.newKeySet();

    private final ToolPrompt prompt;

    /**
     * 授权的问答通道。
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    interface ToolPrompt {

        /**
         * 输出问题并读取一行回答。
         *
         * @param question 问题文本（可多行）
         * @return 用户回答；{@code null} 表示无法读取（应视为拒绝）
         */
        String ask(String question);
    }

    /**
     * 构造裁决器。
     *
     * @param prompt 问答通道
     */
    ConsoleToolApprover(ToolPrompt prompt) {
        this.prompt = prompt;
    }

    /**
     * 构造裁决器（直接读写 {@code LineReader}，用于非交互与测试）。
     *
     * <p>仅当调用方就是持有 reader 的线程时可用；回合线程上请用 {@link ToolPrompt} 版本。</p>
     *
     * @param reader JLine 读取器
     */
    ConsoleToolApprover(LineReader reader) {
        this(question -> {
            System.out.println(question);
            try {
                return reader.readLine("");
            } catch (UserInterruptException | EndOfFileException e) {
                return null;
            }
        });
    }

    @Override
    public boolean approve(String toolName, ToolPermission permission, Map<String, Object> arguments) {
        if (sessionAllowed.contains(permission)) {
            return true;
        }
        String answer = prompt.ask(question(toolName, permission, arguments));
        if (answer == null) {
            return false;
        }
        return switch (answer.trim().toLowerCase()) {
            case "y", "yes" -> true;
            case "a", "all" -> {
                sessionAllowed.add(permission);
                PrintStream out = System.out;
                out.println("已允许本次会话内全部 " + permission + " 操作。");
                yield true;
            }
            default -> {
                printAuthorizationHint(permission);
                yield false;
            }
        };
    }

    /**
     * 拒绝后输出授权指引。
     *
     * <p>模型只会看到一句简洁的“未获授权”，完整方式在此直接告诉用户，
     * 避免被模型转述得冗长。</p>
     *
     * @param permission 被拒绝的权限
     */
    private static void printAuthorizationHint(ToolPermission permission) {
        System.out.println("已拒绝本次调用。后续如需授权：");
        System.out.println("  · 在提示处输入 y 允许本次，或 a 本次会话内始终允许");
        System.out.println("  · 在 Aha.yaml 设置 Tools.AutoApprove: [" + permission + "]");
        System.out.println("  · 启动时加 --yes 自动授权");
    }

    /**
     * 构造确认提示文本：工具、所需权限与参数摘要。
     *
     * @param toolName   工具名
     * @param permission 所需权限
     * @param arguments  调用参数
     * @return 提示文本
     */
    private static String question(String toolName, ToolPermission permission,
                                   Map<String, Object> arguments) {
        StringBuilder text = new StringBuilder();
        text.append("需要授权：").append(toolName).append(" 请求 ").append(permission).append(" 权限");
        if (arguments != null && !arguments.isEmpty()) {
            summary(arguments).forEach((key, value) ->
                    text.append('\n').append("  ").append(key).append(" = ").append(value));
        }
        text.append('\n').append("  [y] 允许本次   [n] 拒绝   [a] 本次会话内始终允许 ")
                .append(permission);
        return text.toString();
    }

    /**
     * 生成参数摘要，长值截断以免刷屏。
     *
     * @param arguments 原始参数
     * @return 摘要
     */
    private static Map<String, String> summary(Map<String, Object> arguments) {
        Map<String, String> result = new LinkedHashMap<>();
        arguments.forEach((key, value) -> {
            String text = value == null ? "null" : String.valueOf(value);
            text = text.replace("\n", "\\n");
            if (text.length() > 120) {
                text = text.substring(0, 120) + "…（共 " + text.length() + " 字符）";
            }
            result.put(key, text);
        });
        return result;
    }
}
