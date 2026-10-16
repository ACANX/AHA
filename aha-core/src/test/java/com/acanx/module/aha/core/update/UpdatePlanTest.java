package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 换装计划：往返、路径绝对性、动作合法性（issue #51 §4.5）。
 *
 * <p>计划是 helper 的**唯一入参**，写坏一份比换装失败更糟：helper 会照着一份错计划去改安装目录。
 * 所以「相对路径的安装根」「逃出安装根的动作路径」「不认识的动作品种」都必须在**写之前**就被拒。</p>
 *
 * @since 0.2.0
 */
class UpdatePlanTest {

    private static final AhaVersion VERSION = AhaVersion.parse("0.1.3.00243");

    @TempDir
    Path tempDir;

    private UpdatePlan sample(List<UpdatePlan.Action> actions) {
        return UpdatePlan.create(VERSION, tempDir.resolve("install"), tempDir.resolve("backup"),
                4242, List.of(tempDir.resolve("AhaDesktop.bat").toString()), actions);
    }

    @Test
    void writesAndReadsBackTheWholePlan() {
        UpdatePlan plan = sample(List.of(UpdatePlan.Action.replace("aha-desktop.jar"),
                UpdatePlan.Action.delete("old-thing.txt")));
        Path file = tempDir.resolve("update-plan.json");

        plan.write(file);
        UpdatePlan back = UpdatePlan.read(file);

        assertThat(back.targetVersion()).isEqualTo(VERSION);
        assertThat(back.installRootPath()).isEqualTo(tempDir.resolve("install"));
        assertThat(back.backupDirPath()).isEqualTo(tempDir.resolve("backup"));
        assertThat(back.pid()).isEqualTo(4242);
        assertThat(back.restartList()).hasSize(1);
        assertThat(back.actionList()).extracting(UpdatePlan.Action::path)
                .containsExactly("aha-desktop.jar", "old-thing.txt");
        assertThat(back.actionList()).extracting(UpdatePlan.Action::normalizedKind)
                .containsExactly(SwapPlan.KIND_REPLACE, SwapPlan.KIND_DELETE);
        assertThat(back.actionList().get(1).deleting()).isTrue();
    }

    @Test
    void refusesRelativeInstallRootOrBackupDir() {
        assertThatThrownBy(() -> UpdatePlan.create(VERSION, Path.of("relative/install"),
                tempDir.resolve("backup"), 1, List.of(), List.of(UpdatePlan.Action.replace("a.jar")))
                .write(tempDir.resolve("p.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("必须是绝对路径");

        assertThatThrownBy(() -> UpdatePlan.create(VERSION, tempDir.resolve("install"),
                Path.of("relative/backup"), 1, List.of(), List.of(UpdatePlan.Action.replace("a.jar")))
                .write(tempDir.resolve("p.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("必须是绝对路径");
    }

    @Test
    void refusesActionPathsThatEscapeTheInstallRoot() {
        for (String escaping : new String[] {"../evil.txt", "/etc/passwd", "a/../../b", "lib\\x.jar",
                "C:/windows/x.dll"}) {
            assertThatThrownBy(() -> sample(List.of(UpdatePlan.Action.replace(escaping)))
                    .write(tempDir.resolve("p.json")))
                    .as("动作路径 %s 必须被拒绝", escaping)
                    .isInstanceOf(UpdateException.class);
        }
    }

    @Test
    void refusesUnknownActionKind() {
        assertThatThrownBy(() -> sample(List.of(new UpdatePlan.Action("aha-desktop.jar", "move")))
                .write(tempDir.resolve("p.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("动作类型不认识");
    }

    @Test
    void refusesEmptyActionListAndRelativeRestartCommand() {
        assertThatThrownBy(() -> sample(List.of()).write(tempDir.resolve("p.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("没有任何文件动作");

        UpdatePlan relativeRestart = UpdatePlan.create(VERSION, tempDir.resolve("install"),
                tempDir.resolve("backup"), 1, List.of("AhaDesktop.bat"),
                List.of(UpdatePlan.Action.replace("aha-desktop.jar")));
        assertThatThrownBy(() -> relativeRestart.write(tempDir.resolve("p.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("拉起命令");
    }

    @Test
    void refusesToReadAMissingOrBrokenPlan() {
        assertThatThrownBy(() -> UpdatePlan.read(tempDir.resolve("nope.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("换装计划不存在");

        Path broken;
        try {
            broken = tempDir.resolve("broken.json");
            Files.writeString(broken, "{ not json");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertThatThrownBy(() -> UpdatePlan.read(broken))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("无法解析");
    }
}
