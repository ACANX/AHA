package com.acanx.module.aha.cli.session;

import java.io.PrintStream;
import java.util.List;

/**
 * 会话内命令 {@code /config}：查看当前生效的配置及其来源。
 *
 * <p>取值逻辑复用 {@link ConfigView}，与进程级命令 {@code aha config get} 保持一致。</p>
 *
 * @since 0.1.0
 */
final class ConfigSessionCommand implements SessionCommand {

    @Override
    public String name() {
        return "config";
    }

    @Override
    public String usage() {
        return "/config";
    }

    @Override
    public String description() {
        return "查看当前生效配置与来源";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();

        out.println("配置来源");
        out.printf("  %-12s %s%n", "主配置", ConfigView.describeMainConfig());
        out.printf("  %-12s %s%n", "模型配置", ConfigView.describeModelFile(context.config()));
        out.printf("  %-12s %s%n", "环境变量", describeEnv("AHA_HOME"));
        out.println();

        out.println("有效配置");
        for (ConfigView.Entry entry : ConfigView.entries(context.config())) {
            out.printf("  %-28s %s%n", entry.key(), entry.value());
        }
        return Outcome.CONTINUE;
    }

    /**
     * 展示环境变量，未设置时明确标注。
     *
     * <p>路径类配置常以 {@code ${AHA_HOME:-~/.aha}} 形式书写，把实际取值一并列出，
     * 用户才能判断「数据到底落在哪里」。</p>
     *
     * @param name 变量名
     * @return 描述文本
     */
    private static String describeEnv(String name) {
        if (name == null || name.isBlank()) {
            return "(未设置)";
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? name + " 未设置" : name + "=" + value;
    }
}
