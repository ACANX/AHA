package com.acanx.module.aha.cli.tty;

import java.util.function.Supplier;

/**
 * 终端让位：把终端临时交给外部程序（编辑器等）时使用。
 *
 * <p>JLine 处于 raw mode 时外部程序无法正常读写终端——按键不会回显、Ctrl+C 也不生效。
 * 因此让位前必须恢复终端属性，程序退出后再重新进入 raw mode。具体实现见
 * {@code ChatCommand}（持有 JLine {@code Terminal}）。</p>
 *
 * <p>抽成接口而不是直接传 {@code Terminal}：一是让会话内命令不必依赖 JLine，
 * 二是测试与非交互场景可以直接用 {@link #DIRECT}。</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface TerminalHandover {

    /** 不让位：直接把终端交给外部程序（测试与非交互场景）。 */
    TerminalHandover DIRECT = action -> action.get();

    /**
     * 让出终端并执行操作。
     *
     * @param action 在终端被让出期间执行的操作，返回其退出码
     * @return 操作结果
     */
    int run(Supplier<Integer> action);
}
