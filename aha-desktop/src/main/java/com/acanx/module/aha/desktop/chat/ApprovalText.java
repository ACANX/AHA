package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.tool.ToolKind;
import com.acanx.module.aha.common.tool.ToolPermission;

import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 授权弹窗上的文字（纯函数）。
 *
 * <p>口径来自 {@code GUIDesign.md} 第 4.4 节。弹窗是危险操作的**唯一**拦截点，
 * 因此「要说清什么」必须有测试钉住：用户是看着这几行字决定要不要放行的。</p>
 *
 * @since 0.2.0
 */
public final class ApprovalText {

    /** 弹窗标题。 */
    public static final String TITLE = "需要授权";

    /** 拒绝按钮。 */
    public static final String DENY = "拒绝";

    /** 本次允许按钮。 */
    public static final String ALLOW_ONCE = "本次允许";

    /** 参数为空时的占位。 */
    public static final String NO_ARGS = "（无参数）";

    private ApprovalText() {
    }

    /**
     * 首行：类别 + 工具名 + 目标。
     *
     * @param toolName 工具名
     * @param target   目标（如文件路径、命令），可为 {@code null}
     * @return 文本
     */
    public static String headline(String toolName, String target) {
        ToolKind kind = ToolKind.of(toolName);
        StringBuilder text = new StringBuilder();
        text.append(kind.label()).append("  ").append(toolName == null ? "(未知工具)" : toolName);
        if (target != null && !target.isBlank()) {
            text.append("  ").append(target);
        }
        return text.toString();
    }

    /**
     * 「本会话内始终允许」按钮的文案。
     *
     * <p>按钮上直接写出权限名，而不是笼统的「始终允许」：用户按下去时应当知道
     * 自己放宽的是哪一类操作（{@code READ} 与 {@code EXECUTE} 差得远）。</p>
     *
     * @param permission 权限
     * @return 文案
     */
    public static String alwaysLabel(ToolPermission permission) {
        return "本会话内始终允许 " + (permission == null ? "（未知权限）" : permission.name());
    }

    /**
     * 权限说明。
     *
     * @param permission 权限
     * @return 形如 {@code WRITE（修改文件与目录）}
     */
    public static String permissionLabel(ToolPermission permission) {
        if (permission == null) {
            return "未知权限";
        }
        return switch (permission) {
            case READ -> permission.name() + "（读取文件与目录）";
            case WRITE -> permission.name() + "（修改、新建文件与目录）";
            case NETWORK -> permission.name() + "（发起网络请求）";
            case EXECUTE -> permission.name() + "（在宿主机上执行命令）";
            case ADMIN -> permission.name() + "（管理级操作）";
        };
    }

    /**
     * 参数预览（每行一个键值对）。
     *
     * @param arguments 参数，可为 {@code null}
     * @return 文本；无参数时返回 {@link #NO_ARGS}
     */
    public static String argumentsText(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return NO_ARGS;
        }
        StringJoiner joiner = new StringJoiner("\n");
        arguments.forEach((key, value) -> joiner.add(key + ": " + value));
        return joiner.toString();
    }

    /**
     * 「本会话已自动允许」提示。
     *
     * <p>只列**已经**放行的权限，不列当前这次要问的（那一条正在被问）。
     * 没有已放行权限时返回 {@code null}，界面不显示这一行。</p>
     *
     * @param sessionAllowed 本会话已放行的权限
     * @param asking         当前正在询问的权限
     * @return 文本；没有可说的内容时返回 {@code null}
     */
    public static String sessionAllowedNote(Set<ToolPermission> sessionAllowed,
                                            ToolPermission asking) {
        if (sessionAllowed == null || sessionAllowed.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("、");
        sessionAllowed.stream()
                .filter(permission -> permission != asking)
                .sorted()
                .forEach(permission -> joiner.add(permission.name()));
        String list = joiner.toString();
        if (list.isEmpty()) {
            return null;
        }
        return "本次会话内，以下权限已自动允许：" + list
                + "（「工具 → 全部撤销本会话授权」可以撤回）";
    }

    /**
     * 结论：用户选了什么。
     *
     * @param decision 选择
     * @return 一句话
     */
    public static String decisionNote(DesktopToolApprover.Decision decision) {
        if (decision == null) {
            return DENY;
        }
        return switch (decision) {
            case ALLOW_ONCE -> "已允许本次";
            case ALLOW_SESSION -> "本会话内已始终允许该权限";
            case DENY -> "已拒绝";
        };
    }
}
