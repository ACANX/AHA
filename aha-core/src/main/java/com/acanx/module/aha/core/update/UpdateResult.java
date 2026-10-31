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
import java.util.Optional;

/**
 * 换装回执 {@code update-result.json}：helper 写完，App <b>下次启动</b>读它（issue #51 §4.5 第 6 步）。
 *
 * <p>为什么要回执而不是当场报结果：换装时 App 已经退出（正在运行的进程换不掉自己的文件），
 * 换装结果只能在**下一次启动**时展示。成功则静默（只留日志），失败则弹窗并指向
 * {@code ~/.aha/update/update.log}。</p>
 *
 * <p>读完要消费掉（{@link #consume(Path)}）：否则用户每次启动都会看到同一个旧结果。</p>
 *
 * @param ok              是否成功
 * @param version         目标版本
 * @param previousVersion 被替换下来的版本（拿不到时为空）
 * @param reason          失败原因（成功时为空）
 * @param rolledBack      失败时是否已回滚成功
 * @param at              完成时间（ISO-8601）
 * @since 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateResult(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("version") String version,
        @JsonProperty("previousVersion") String previousVersion,
        @JsonProperty("reason") String reason,
        @JsonProperty("rolledBack") boolean rolledBack,
        @JsonProperty("at") String at) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /**
     * 成功回执。
     *
     * @param version         目标版本
     * @param previousVersion 被替换下来的版本
     * @return 回执
     */
    public static UpdateResult success(AhaVersion version, String previousVersion) {
        return new UpdateResult(true, version.value(), previousVersion, "", false, Instant.now().toString());
    }

    /**
     * 失败回执。
     *
     * @param version         目标版本
     * @param previousVersion 被替换下来的版本
     * @param reason          失败原因
     * @param rolledBack      是否已回滚
     * @return 回执
     */
    public static UpdateResult failure(AhaVersion version, String previousVersion, String reason,
                                       boolean rolledBack) {
        return new UpdateResult(false, version.value(), previousVersion, reason, rolledBack,
                Instant.now().toString());
    }

    /**
     * 写入回执（helper 侧的实现写的是同一个格式）。
     *
     * @param file 目标文件
     * @throws UpdateException 写入失败
     */
    public void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(this));
        } catch (IOException | RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "写入换装回执失败：" + file + "（" + e.getMessage() + "）", e);
        }
    }

    /**
     * 读回执（不存在或读不出来时返回空 —— 没有回执是正常情况，不该因此报错）。
     *
     * @param file 回执文件
     * @return 回执
     */
    public static Optional<UpdateResult> readIfPresent(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            UpdateResult result = JSON.readValue(stripBom(Files.readString(file)),
                    new TypeReference<UpdateResult>() {
                    });
            return Optional.ofNullable(result);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * 读取并消费（读完删掉）：避免用户每次启动都看到同一个旧结果。
     *
     * @param file 回执文件
     * @return 回执；不存在或读不出来时为空
     */
    public static Optional<UpdateResult> consume(Path file) {
        Optional<UpdateResult> result = readIfPresent(file);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 删不掉不算错：下次启动至多再展示一次
        }
        return result;
    }

    /**
     * 给用户看的一句话结论（界面用，避免界面自己拼文案）。
     *
     * @return 中文结论
     */
    public String summary() {
        if (ok) {
            return "已升级到 " + version + "（点「检查更新」可核对当前版本）";
        }
        String rolled = rolledBack ? "已回滚到换装前的版本" : "**未能回滚**，请手动重装（备份在 ~/.aha/update/backup/）";
        return "升级到 " + version + " 失败：" + (reason == null || reason.isBlank() ? "原因未记录" : reason)
                + "；" + rolled;
    }

    /**
     * 去掉可能存在的 UTF-8 BOM。
     *
     * <p>回执与计划都可能由 Windows 侧的 PowerShell 写出，而 PS 5.1 的
     * {@code Set-Content -Encoding UTF8} 会写 BOM —— 那三个字节会让 JSON 解析直接失败。
     * 这不是理论问题：helper 写回执、App 读回执正好是这条路径。</p>
     */
    private static String stripBom(String text) {
        return text != null && text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
