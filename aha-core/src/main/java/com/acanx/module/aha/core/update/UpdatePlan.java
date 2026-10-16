package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 换装计划 {@code update-plan.json}：App 交给外部 helper 的**唯一入参**（issue #51 §4.5）。
 *
 * <p>为什么要把计划落成文件，而不是在命令行上递参数：helper 要等 App 退出、要能自证、要能回滚，
 * 中间可能隔几十秒；命令行参数在 Windows 上还牵扯引号与转义。文件是唯一入参，
 * helper 不解析任何用户输入 ⇒ 不存在注入面（§4.7）。</p>
 *
 * <p><b>计划里的所有路径都是绝对路径</b>：helper 不依赖工作目录，也不继承 App 的当前目录
 * （App 退出时它的目录可能已经不存在了）。{@link #write(Path)} 会校验这一点，
 * 也会校验每个动作路径都落在安装根内 —— 写坏一份计划比换装失败更糟。</p>
 *
 * @param version   目标版本
 * @param installRoot 安装根（绝对路径）
 * @param backupDir 备份目录（绝对路径）
 * @param pid       App 进程号（helper 据此等它退出）
 * @param restart   换装成功后拉起 App 的命令（绝对路径，串行执行）
 * @param actions   要做的文件动作（顺序执行）
 * @param createdAt 计划生成时间（ISO-8601）
 * @since 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePlan(
        @JsonProperty("version") String version,
        @JsonProperty("installRoot") String installRoot,
        @JsonProperty("backupDir") String backupDir,
        @JsonProperty("pid") long pid,
        @JsonProperty("restart") List<String> restart,
        @JsonProperty("actions") List<Action> actions,
        @JsonProperty("createdAt") String createdAt) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /**
     * 一个文件动作。
     *
     * @param path 安装根内的相对路径
     * @param kind 动作（{@code replace} / {@code delete}）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(@JsonProperty("path") String path, @JsonProperty("kind") String kind) {

        /**
         * 构造「替换 / 新增」动作。
         *
         * @param path 相对路径
         * @return 动作
         */
        public static Action replace(String path) {
            return new Action(path, SwapPlan.KIND_REPLACE);
        }

        /**
         * 构造「删除」动作。
         *
         * @param path 相对路径
         * @return 动作
         */
        public static Action delete(String path) {
            return new Action(path, SwapPlan.KIND_DELETE);
        }

        /**
         * 是否是删除动作。
         *
         * @return 删除返回 {@code true}
         */
        public boolean deleting() {
            return SwapPlan.KIND_DELETE.equals(normalizedKind());
        }

        /**
         * 规范化后的动作名（小写）。
         *
         * @return {@code replace} / {@code delete}
         */
        public String normalizedKind() {
            return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 组装计划（时间戳取当前时刻）。
     *
     * @param version     目标版本
     * @param installRoot 安装根
     * @param backupDir   备份目录
     * @param pid         进程号
     * @param restart     拉起命令
     * @param actions     文件动作
     * @return 计划
     */
    public static UpdatePlan create(AhaVersion version, Path installRoot, Path backupDir, long pid,
                                    List<String> restart, List<Action> actions) {
        return new UpdatePlan(version.value(), installRoot.toString(), backupDir.toString(), pid,
                List.copyOf(restart), List.copyOf(actions), Instant.now().toString());
    }

    /**
     * 写入计划文件（先校验再写，写坏的计划不该出现在磁盘上）。
     *
     * @param file 目标文件
     * @throws UpdateException 计划不合法或写入失败
     */
    public void write(Path file) {
        validate();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(this));
        } catch (IOException | RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "写入换装计划失败：" + file + "（" + e.getMessage() + "）", e);
        }
    }

    /**
     * 读取计划文件。
     *
     * @param file 计划文件
     * @return 计划
     * @throws UpdateException 文件不存在或内容无法解析
     */
    public static UpdatePlan read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "换装计划不存在：" + file);
        }
        try {
            UpdatePlan plan = JSON.readValue(Files.readString(file),
                    new TypeReference<UpdatePlan>() {
                    });
            if (plan == null) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "换装计划为空：" + file);
            }
            plan.validate();
            return plan;
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "读取换装计划失败：" + file + "（" + e.getMessage() + "）", e);
        } catch (RuntimeException e) {
            if (e instanceof UpdateException update) {
                throw update;
            }
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "换装计划无法解析：" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * 目标版本。
     *
     * @return 版本
     */
    public AhaVersion targetVersion() {
        return AhaVersion.parse(version);
    }

    /**
     * 安装根（绝对路径）。
     *
     * @return 安装根
     */
    public Path installRootPath() {
        return Path.of(installRoot);
    }

    /**
     * 备份目录（绝对路径）。
     *
     * @return 备份目录
     */
    public Path backupDirPath() {
        return Path.of(backupDir);
    }

    /**
     * 动作列表（永不为 {@code null}）。
     *
     * @return 动作列表
     */
    public List<Action> actionList() {
        return actions == null ? List.of() : List.copyOf(actions);
    }

    /**
     * 拉起命令（永不为 {@code null}）。
     *
     * @return 命令列表
     */
    public List<String> restartList() {
        return restart == null ? List.of() : List.copyOf(restart);
    }

    /**
     * 校验计划（{@link #write} 与 {@link #read} 都调它）。
     *
     * @throws UpdateException 任一项不合法
     */
    public void validate() {
        AhaVersion parsed;
        try {
            parsed = AhaVersion.parse(version);
        } catch (RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "换装计划的版本不合法：" + version, e);
        }
        Path install = PathSafety.requireAbsolute(installRoot, "换装计划的 installRoot");
        PathSafety.requireAbsolute(backupDir, "换装计划的 backupDir");
        if (actionList().isEmpty()) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "换装计划里没有任何文件动作（版本 " + parsed.value() + "）");
        }
        for (Action action : actionList()) {
            Path relative = PathSafety.requireRelative(action.path(), "换装计划的动作路径");
            String kind = action.normalizedKind();
            if (!SwapPlan.KIND_REPLACE.equals(kind) && !SwapPlan.KIND_DELETE.equals(kind)) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "换装计划的动作类型不认识：" + action.kind() + "（应为 replace / delete）");
            }
            if (!install.resolve(relative).normalize().startsWith(install.normalize())) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "换装计划的动作路径逃出了安装根：" + action.path());
            }
        }
        for (String command : restartList()) {
            PathSafety.requireAbsolute(command, "换装计划的拉起命令");
        }
    }
}
