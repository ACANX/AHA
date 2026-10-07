package com.acanx.module.aha.desktop.chat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 会话内命令目录（纯逻辑）。
 *
 * <p>命令名与 CLI 的会话内命令**同名**（{@code GUIDesign.md} 第 3.3 节：概念不因端而异）。
 * 这里只登记「有多少条命令、各自叫什么、干什么」，实际动作由界面按名字分发——
 * 于是「命令面板里列出了什么」可以脱离图形环境断言。</p>
 *
 * <p>只登记**真的能执行**的命令。做不到的命令不进目录：菜单里放一个点了没反应的项，
 * 比少一个功能更糟（这条教训来自本项目前面的「待接入」占位菜单项）。</p>
 *
 * @since 0.2.0
 */
public final class SlashCommands {

    /** 命令前缀。 */
    public static final char PREFIX = '/';

    /**
     * 一条命令。
     *
     * @param name        命令名（不含前缀）
     * @param label       中文名（面板里显示）
     * @param description 说明
     */
    public record Command(String name, String label, String description) {

        /**
         * 面板里那一行的文本。
         *
         * @return 形如 {@code /help　帮助　列出可用命令}
         */
        public String display() {
            return PREFIX + name + "　" + label + "　" + description;
        }
    }

    /** 命令目录（顺序即面板里的顺序）。 */
    public static final List<Command> CATALOG = List.of(
            new Command("help", "帮助", "列出可用命令（会把结果写进对话流）"),
            new Command("new", "新建会话", "开一个新会话，历史留在左栏列表里"),
            new Command("clear", "清空对话", "只清空当前界面，不动已保存的历史"),
            new Command("model", "供应商与模型", "打开供应商配置（含新增与修改）"),
            new Command("tools", "工具", "查看可用工具与权限标注"),
            new Command("log", "日志", "打开实时日志面板"),
            new Command("theme", "主题", "在暗色 / 亮色 / 跟随系统之间切换"),
            new Command("exit", "退出", "关闭窗口"));

    private SlashCommands() {
    }

    /**
     * 命令名 → 命令。
     *
     * @param name 命令名（不含前缀，大小写不敏感）
     * @return 命令
     */
    public static Optional<Command> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String needle = name.strip().toLowerCase(Locale.ROOT);
        return CATALOG.stream().filter(command -> command.name().equals(needle)).findFirst();
    }

    /**
     * 输入内容是否是「一条完整命令」（给动作分发用）。
     *
     * @param text 输入框里的文本
     * @return 形如 {@code /help} 时返回对应命令；带参数或不是命令时为空
     */
    public static Optional<Command> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.strip();
        if (trimmed.length() < 2 || trimmed.charAt(0) != PREFIX) {
            return Optional.empty();
        }
        String body = trimmed.substring(1).strip();
        if (body.isEmpty() || body.contains(" ") || body.contains("\n")) {
            return Optional.empty();
        }
        return byName(body);
    }

    /**
     * 候选命令（按已输入的前缀过滤）。
     *
     * @param typed 已输入的前缀（不含 {@value #PREFIX}），可为空
     * @return 候选命令
     */
    public static List<Command> match(String typed) {
        if (typed == null || typed.isBlank()) {
            return CATALOG;
        }
        String needle = typed.strip().toLowerCase(Locale.ROOT);
        return CATALOG.stream()
                .filter(command -> command.name().startsWith(needle))
                .toList();
    }

    /**
     * 补全用的文本（把候选替换进输入框）。
     *
     * @param command 命令
     * @return 形如 {@code /help}
     */
    public static String completionText(Command command) {
        return PREFIX + command.name();
    }
}
