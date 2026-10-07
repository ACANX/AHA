package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.core.service.AgentService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 工具管理命令。
 *
 * @since 0.1.0
 */
@Command(
        name = "tool",
        description = "工具管理",
        subcommands = {ToolCommand.ListSub.class, ToolCommand.InvokeSub.class},
        mixinStandardHelpOptions = true)
public final class ToolCommand implements Runnable {

    /** 默认自动放行的权限。 */
    private static final List<String> AUTO_APPROVED = List.of("READ", "NETWORK");

    @Override
    public void run() {
        System.out.println("用法: aha tool [list|invoke]");
    }

    /**
     * 列出工具。
     *
     * @since 0.1.0
     */
    @Command(name = "list", description = "列出工具", mixinStandardHelpOptions = true)
    public static final class ListSub implements Callable<Integer> {
        @Override
        public Integer call() {
            AgentService service = CliContext.service();
            System.out.printf("%-12s %-10s %-6s %s%n", "NAME", "PERMISSION", "AUTO", "DESCRIPTION");
            for (ToolDescriptor descriptor : service.listTools()) {
                String permission = descriptor.permission().name();
                String auto = AUTO_APPROVED.contains(permission) ? "yes" : "no";
                System.out.printf("%-12s %-10s %-6s %s%n",
                        descriptor.name(), permission, auto, descriptor.description());
            }
            return 0;
        }
    }

    /**
     * 调用工具。
     *
     * @since 0.1.0
     */
    @Command(name = "invoke", description = "调用工具", mixinStandardHelpOptions = true)
    public static final class InvokeSub implements Callable<Integer> {

        @Parameters(index = "0", description = "工具名")
        String name;

        @Option(names = {"-p", "--param"}, description = "参数，格式 key=value，可重复")
        Map<String, String> params = new LinkedHashMap<>();

        @Option(names = {"-y", "--yes"}, description = "确认执行 WRITE/EXECUTE/ADMIN 工具")
        boolean confirmed;

        @Override
        public Integer call() {
            AgentService service = CliContext.service();
            Map<String, Object> arguments = new LinkedHashMap<>(params);
            try {
                ToolResult result = service.invokeTool(name, arguments, confirmed);
                if (result.success()) {
                    System.out.println(result.output());
                    return 0;
                }
                System.err.println("[error] " + result.error());
                return 1;
            } catch (AhaException e) {
                if ("PERMISSION_DENIED".equals(e.code())) {
                    System.err.println("[error] " + Exceptions.message(e));
                    System.err.println("提示：使用 aha tool invoke " + name + " -y 确认执行");
                } else {
                    System.err.println("[error] " + Exceptions.message(e));
                }
                return 1;
            }
        }
    }
}
