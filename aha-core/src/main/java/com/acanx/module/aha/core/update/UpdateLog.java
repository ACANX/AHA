package com.acanx.module.aha.core.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 升级日志：追加式写到 {@code <AHA_HOME>/update/update.log}（issue #51 §4.7）。
 *
 * <p>每次检查、每次下载、每次换装都留痕（版本、来源 tag、资产名、校验结果、结论），
 * 出问题时有据可查；helper 与 App <b>写同一个文件</b>，所以整条链路是连续的。</p>
 *
 * <p><b>写日志失败不阻断升级</b>：日志是给排障用的，不是升级的前置条件。磁盘满、目录只读之类的情况下，
 * 让升级因为「写不进日志」而失败，是把工具的优先级摆错了。所以本类的写入一律是 best-effort ——
 * 失败时静默跳过（调用方拿不到异常，但升级照常进行）。这一点与「失败要能读懂」并不冲突：
 * 真正影响升级结果的失败仍然照常抛出并展示。</p>
 *
 * @since 0.2.0
 */
public final class UpdateLog {

    private final Path file;

    /**
     * 构造。
     *
     * @param paths 路径布局
     */
    public UpdateLog(UpdatePaths paths) {
        this.file = paths.logFile();
    }

    /**
     * 日志文件路径。
     *
     * @return {@code <根>/update.log}
     */
    public Path file() {
        return file;
    }

    /**
     * 追加一行：{@code <ISO 时间> [事件] 说明}。
     *
     * @param event   事件名（如 {@code check} / {@code download} / {@code swap} / {@code result}）
     * @param message 说明（不要放密钥 / token —— 日志会随排障请求被贴出来）
     */
    public void append(String event, String message) {
        String line = Instant.now() + " [" + safe(event) + "] " + safe(message) + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // best-effort：见类注释
        }
    }

    /**
     * 读出全部日志行（文件不存在或读不出来时返回空列表）。
     *
     * @return 行列表（按写入顺序）
     */
    public List<String> lines() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>(List.of(Files.readString(file).split("\\R")));
            lines.removeIf(String::isEmpty);
            return List.copyOf(lines);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
    }
}
