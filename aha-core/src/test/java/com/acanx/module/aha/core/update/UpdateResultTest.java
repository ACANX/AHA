package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 换装回执：往返、读完即消费、以及**容忍 PowerShell 写出的 BOM**（issue #51 §4.5 第 6 步）。
 *
 * <p>BOM 那条不是防御性编程：helper 是 PowerShell 5.1，它的
 * {@code Set-Content -Encoding UTF8} 会写 UTF-8 BOM，而 App 侧用
 * {@code Files.readString} + JSON 解析读回来 —— 那三个字节会让解析直接失败，
 * 表现是「升级成功了但每次启动都看不到结果」。所以这条用例是按真实链路写的。</p>
 *
 * @since 0.2.0
 */
class UpdateResultTest {

    private static final AhaVersion VERSION = AhaVersion.parse("0.1.3.00243");

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsBackBothOutcomes() {
        Path file = tempDir.resolve("update-result.json");

        UpdateResult.success(VERSION, "0.1.3.00242").write(file);
        UpdateResult success = UpdateResult.readIfPresent(file).orElseThrow();
        assertThat(success.ok()).isTrue();
        assertThat(success.version()).isEqualTo("0.1.3.00243");
        assertThat(success.previousVersion()).isEqualTo("0.1.3.00242");
        assertThat(success.summary()).contains("已升级到 0.1.3.00243");

        UpdateResult.failure(VERSION, "0.1.3.00242", "校验和不符", true).write(file);
        UpdateResult failure = UpdateResult.readIfPresent(file).orElseThrow();
        assertThat(failure.ok()).isFalse();
        assertThat(failure.rolledBack()).isTrue();
        assertThat(failure.summary()).contains("校验和不符").contains("已回滚");

        UpdateResult noRollback = UpdateResult.failure(VERSION, null, "", false);
        assertThat(noRollback.summary()).contains("未能回滚").contains("原因未记录");
    }

    @Test
    void missingResultIsNotAnError() {
        assertThat(UpdateResult.readIfPresent(tempDir.resolve("nope.json"))).isEmpty();
        assertThat(UpdateResult.consume(tempDir.resolve("nope.json"))).isEmpty();
    }

    @Test
    void consumeRemovesTheFileSoTheUserSeesItOnce() throws IOException {
        Path file = tempDir.resolve("update-result.json");
        UpdateResult.success(VERSION, null).write(file);

        Optional<UpdateResult> first = UpdateResult.consume(file);
        Optional<UpdateResult> second = UpdateResult.consume(file);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(file).doesNotExist();
    }

    @Test
    void toleratesABomAndUnknownFieldsWrittenByPowerShell() throws IOException {
        Path file = tempDir.resolve("update-result.json");
        String json = """
                {"ok":false,"version":"0.1.3.00243","previousVersion":"0.1.3.00242",
                 "reason":"替换中途失败","rolledBack":true,"at":"2026-10-16T00:00:00Z",
                 "helperVersion":"1.0"}
                """;
        Files.writeString(file, "\uFEFF" + json);

        UpdateResult result = UpdateResult.readIfPresent(file).orElseThrow();

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("替换中途失败");
        assertThat(result.rolledBack()).isTrue();
    }

    @Test
    void brokenResultIsTreatedAsMissingInsteadOfBreakingStartup() throws IOException {
        Path file = tempDir.resolve("update-result.json");
        Files.writeString(file, "{ not json");

        // 「结果读不出来」不该让 App 起不来：至多是少展示一次结果
        assertThat(UpdateResult.readIfPresent(file)).isEmpty();
    }

    @Test
    void refusesToWriteIntoAPathThatCannotBeCreated() throws IOException {
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "not a directory");

        assertThatThrownBy(() -> UpdateResult.success(VERSION, null).write(blocker.resolve("x.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("写入换装回执失败");
    }
}
