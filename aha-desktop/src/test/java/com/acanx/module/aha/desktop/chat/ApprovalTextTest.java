package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.tool.ToolPermission;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApprovalText} 测试：授权弹窗的说辞（GUIDesign 第 4.4 节）。
 *
 * <p>这些文本是用户做放行决定的唯一依据，因此「必须说清什么」由测试钉住。</p>
 *
 * @since 0.2.0
 */
class ApprovalTextTest {

    @Test
    void headlineCarriesKindToolAndTarget() {
        // 类别标签来自 aha-common 的 ToolKind（CLI 与桌面端共用），不是这里自造的词
        assertThat(ApprovalText.headline("file-write", "src/Main.java"))
                .isEqualTo("写入文件  file-write  src/Main.java");
        assertThat(ApprovalText.headline("file-write", null)).isEqualTo("写入文件  file-write");
    }

    @Test
    void permissionLabelExplainsWhatItMeans() {
        assertThat(ApprovalText.permissionLabel(ToolPermission.WRITE))
                .isEqualTo("WRITE（修改、新建文件与目录）");
        assertThat(ApprovalText.permissionLabel(ToolPermission.EXECUTE)).contains("执行命令");
        assertThat(ApprovalText.permissionLabel(null)).isEqualTo("未知权限");
    }

    @Test
    void alwaysLabelNamesThePermissionBeingWidened() {
        // 笼统的「始终允许」会让用户不知道自己放宽了哪一类操作
        assertThat(ApprovalText.alwaysLabel(ToolPermission.EXECUTE))
                .isEqualTo("本会话内始终允许 EXECUTE");
        assertThat(ApprovalText.alwaysLabel(null)).contains("未知权限");
    }

    @Test
    void argumentsTextListsFullValuesSoCommandsAreComparable() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("command", "rm -rf build");
        args.put("timeoutSeconds", 60);

        assertThat(ApprovalText.argumentsText(args))
                .isEqualTo("command: rm -rf build\ntimeoutSeconds: 60");
        assertThat(ApprovalText.argumentsText(null)).isEqualTo("（无参数）");
        assertThat(ApprovalText.argumentsText(Map.of())).isEqualTo("（无参数）");
    }

    @Test
    void sessionAllowedNoteListsOtherPermissionsOnly() {
        String note = ApprovalText.sessionAllowedNote(
                Set.of(ToolPermission.READ, ToolPermission.EXECUTE), ToolPermission.WRITE);

        assertThat(note).contains("已自动允许").contains("EXECUTE").contains("READ");
        assertThat(note).as("正在被问的那一项不该出现在「已自动允许」里").doesNotContain("WRITE");
        assertThat(note).contains("撤回");
    }

    @Test
    void sessionAllowedNoteIsAbsentWhenNothingToSay() {
        assertThat(ApprovalText.sessionAllowedNote(Set.of(), ToolPermission.WRITE)).isNull();
        assertThat(ApprovalText.sessionAllowedNote(null, ToolPermission.WRITE)).isNull();
        // 只放行过正在被问的那一项时，也没有「已自动允许」可说
        assertThat(ApprovalText.sessionAllowedNote(Set.of(ToolPermission.WRITE),
                ToolPermission.WRITE)).isNull();
    }

    @Test
    void decisionNoteSpeaksPlainly() {
        assertThat(ApprovalText.decisionNote(DesktopToolApprover.Decision.ALLOW_ONCE)).contains("允许");
        assertThat(ApprovalText.decisionNote(DesktopToolApprover.Decision.ALLOW_SESSION))
                .contains("始终允许");
        assertThat(ApprovalText.decisionNote(DesktopToolApprover.Decision.DENY)).contains("拒绝");
        assertThat(ApprovalText.decisionNote(null)).contains("拒绝");
    }
}
