package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.core.agent.PermissionPolicy;
import com.acanx.module.aha.core.agent.ToolRegistry;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/**
 * 会话内命令 {@code /tool}：查看已启用工具，或某个工具的权限与参数。
 *
 * <p>回答的是「模型到底能调用什么」：{@code Tools.Enabled} 会过滤掉未启用的工具，
 * 因此列出的是<b>实际可被模型调用</b>的集合，而不是注册表里的全部工具。</p>
 *
 * @since 0.1.0
 */
final class ToolSessionCommand implements SessionCommand {

    /** 参数 Schema 的最大展示长度。 */
    private static final int MAX_SCHEMA = 300;

    @Override
    public String name() {
        return "tool";
    }

    @Override
    public String usage() {
        return "/tool [名称]";
    }

    @Override
    public String description() {
        return "查看工具列表或某个工具的权限与参数";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        if (args.isEmpty()) {
            list(context, out);
            return Outcome.CONTINUE;
        }
        detail(context, out, args.get(0));
        return Outcome.CONTINUE;
    }

    private void list(SessionContext context, PrintStream out) {
        List<ToolDescriptor> tools = context.service().listTools();
        if (tools.isEmpty()) {
            out.println("没有已启用的工具。");
            out.println("提示：用 aha tool list 查看全部工具，并在 Aha.Tools.Enabled 中启用。");
            return;
        }
        PermissionPolicy policy = policy(context);
        out.printf("工具（%d 个启用）%n", tools.size());
        for (ToolDescriptor tool : tools) {
            out.printf("  %-14s %-8s %-9s %s%n",
                    tool.name(),
                    tool.permission() == null ? "-" : tool.permission(),
                    approveLabel(policy, tool.name(), tool.permission()),
                    tool.description() == null ? "" : tool.description());
        }
        out.println();
        out.println("用法  /tool <名称>  查看权限与参数详情");
    }

    private void detail(SessionContext context, PrintStream out, String name) {
        ToolRegistry registry = context.local() == null
                ? null : context.local().engine().toolRegistry();
        Optional<Tool> tool = registry == null ? Optional.empty() : registry.find(name);

        if (tool.isEmpty()) {
            // 本地运行时才能区分「不存在」与「存在但被禁用」
            boolean registered = registry != null && registry.contains(name);
            if (!registered) {
                out.println("[error] 未找到工具：" + name);
                out.println("        用 /tool 查看已启用工具，或 aha tool list 查看全部工具。");
                return;
            }
        }

        String description;
        ToolPermission permission;
        String schema;
        if (tool.isPresent()) {
            description = tool.get().description();
            permission = tool.get().requiredPermission();
            schema = String.valueOf(tool.get().parameters());
        } else {
            ToolDescriptor descriptor = context.service().listTools().stream()
                    .filter(item -> item.name().equals(name)).findFirst().orElse(null);
            if (descriptor == null) {
                out.println("[error] 未找到工具：" + name);
                return;
            }
            description = descriptor.description();
            permission = descriptor.permission();
            schema = String.valueOf(descriptor.parameters());
        }

        out.println("工具 " + name);
        SessionOutput.field(out, "权限", permission == null ? "-" : permission.name());
        SessionOutput.field(out, "状态", registry == null || registry.isEnabled(name) ? "已启用" : "已禁用（不在 Tools.Enabled 中）");
        SessionOutput.field(out, "放行", approveDetail(policy(context), name, permission));
        SessionOutput.field(out, "说明", description);
        SessionOutput.field(out, "参数", clamp(schema));
    }

    private static PermissionPolicy policy(SessionContext context) {
        return context.local() == null ? null : context.local().engine().toolRegistry().permissionPolicy();
    }

    private static String approveLabel(PermissionPolicy policy, String name, ToolPermission permission) {
        if (policy == null || permission == null) {
            return "-";
        }
        return policy.isAutoApproved(name, permission) ? "AUTO" : "CONFIRM";
    }

    private static String approveDetail(PermissionPolicy policy, String name, ToolPermission permission) {
        if (policy == null || permission == null) {
            return "未知（非本地运行时无法判定）";
        }
        return policy.isAutoApproved(name, permission)
                ? "AUTO（策略自动放行）"
                : "CONFIRM（需交互确认，或在 Tools.AutoApprove 中放行）";
    }

    private static String clamp(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_SCHEMA
                ? value
                : value.substring(0, MAX_SCHEMA) + "…（已截断）";
    }
}
