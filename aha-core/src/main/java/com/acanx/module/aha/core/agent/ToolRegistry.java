package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.exception.ToolExecutionException;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.common.tool.ToolSettings;
import com.acanx.module.aha.common.tool.ToolProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * 工具注册表，基于 {@link ServiceLoader} 发现 {@link ToolProvider}。
 *
 * <p>调用工具前会依据 {@link PermissionPolicy} 校验权限，
 * 未获自动授权的调用抛出 {@link ToolExecutionException}（错误码 {@code PERMISSION_DENIED}）。</p>
 *
 * @since 0.1.0
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private volatile PermissionPolicy permissionPolicy;
    private volatile ToolApprover approver = ToolApprover.denyAll();
    /** 启用的工具名；为 {@code null} 时表示不过滤。 */
    private volatile Set<String> enabled;

    /**
     * 构造并加载全部工具，使用默认权限策略。
     *
     * @see PermissionPolicy#defaultPolicy()
     */
    public ToolRegistry() {
        this(PermissionPolicy.defaultPolicy());
    }

    /**
     * 构造并加载全部工具。
     *
     * @param permissionPolicy 权限策略
     */
    public ToolRegistry(PermissionPolicy permissionPolicy) {
        this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy");
        ServiceLoader.load(ToolProvider.class)
                .forEach(provider -> provider.tools().forEach(tool -> tools.put(tool.name(), tool)));
    }

    /**
     * 设置启用的工具集合。
     *
     * <p>与 {@code Tools.Enabled} 对应。不设置（或传空）时不过滤。</p>
     *
     * @param names 工具名，可为 {@code null}
     */
    public void setEnabled(List<String> names) {
        this.enabled = names == null || names.isEmpty() ? null : Set.copyOf(names);
    }

    /**
     * 工具是否已启用。
     *
     * @param name 工具名
     * @return 是否启用
     */
    public boolean isEnabled(String name) {
        Set<String> current = this.enabled;
        return current == null || current.contains(name);
    }

    /**
     * 向全部已注册工具注入配置。
     *
     * <p>在工具注册完成后调用一次。未覆盖 {@link Tool#configure} 的工具不受影响。</p>
     *
     * @param settings 配置视图，为 {@code null} 时使用空配置
     */
    public void configureAll(ToolSettings settings) {
        ToolSettings effective = settings == null ? ToolSettings.empty() : settings;
        tools.values().forEach(tool -> tool.configure(effective));
    }

    /**
     * 注册工具。
     *
     * @param tool 工具
     */
    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * 设置工具授权裁决器。
     *
     * <p>当所需权限未被自动放行时，由裁决器决定是否执行。
     * 不设置时一律拒绝（{@link ToolApprover#denyAll()}）。</p>
     *
     * @param approver 裁决器，为 {@code null} 时重置为一律拒绝
     */
    public void setApprover(ToolApprover approver) {
        this.approver = approver == null ? ToolApprover.denyAll() : approver;
    }

    /**
     * 返回当前授权裁决器。
     *
     * @return 裁决器
     */
    public ToolApprover approver() {
        return approver;
    }

    /**
     * 设置权限策略。
     *
     * @param policy 权限策略
     */
    public void setPermissionPolicy(PermissionPolicy policy) {
        this.permissionPolicy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * 返回当前权限策略。
     *
     * @return 权限策略
     */
    public PermissionPolicy permissionPolicy() {
        return permissionPolicy;
    }

    /**
     * 列出工具描述符。
     *
     * @return 工具描述符
     */
    public List<ToolDescriptor> list() {
        return tools.values().stream()
                .filter(tool -> isEnabled(tool.name()))
                .map(tool -> new ToolDescriptor(
                        tool.name(), tool.description(), tool.parameters(), tool.requiredPermission()))
                .toList();
    }

    /**
     * 是否已注册。
     *
     * @param name 工具名
     * @return 是否已注册
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * 查询工具。
     *
     * @param name 工具名
     * @return 工具
     */
    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 调用工具（含权限校验）。
     *
     * @param name   工具名
     * @param params 参数
     * @param token  取消令牌
     * @return 结果
     */
    /**
     * 执行已获用户确认的工具（跳过权限裁决，但不跳过启用检查）。
     *
     * <p>不能直接取到 {@link Tool} 后调用 {@code execute()}：那会绕过
     * {@code Tools.Enabled} 过滤，使未启用的工具仍可被调用。</p>
     *
     * @param name  工具名
     * @param params 参数
     * @param token  取消令牌
     * @return 结果
     */
    public ToolResult invokeApproved(String name, Map<String, Object> params, CancellationToken token) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolExecutionException("UNKNOWN_TOOL", "未知工具: " + name);
        }
        if (!isEnabled(name)) {
            throw new ToolExecutionException("TOOL_DISABLED",
                    "工具 " + name + " 未在 Tools.Enabled 中启用");
        }
        return tool.execute(params, token);
    }

    public ToolResult invoke(String name, Map<String, Object> params, CancellationToken token) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolExecutionException("UNKNOWN_TOOL", "未知工具: " + name);
        }
        if (!isEnabled(name)) {
            throw new ToolExecutionException("TOOL_DISABLED",
                    "工具 " + name + " 未在 Tools.Enabled 中启用");
        }
        ToolPermission permission = tool.requiredPermission();
        if (!permissionPolicy.isAutoApproved(name, permission)) {
            if (!approver.approve(name, permission, params)) {
                // 消息面向模型，保持简洁；面向用户的授权指引由裁决器或 CLI 输出
                throw new ToolExecutionException("PERMISSION_DENIED",
                        "工具 " + name + " 需要 " + permission + " 权限，未获授权");
            }
        }
        return tool.execute(params, token);
    }
}
