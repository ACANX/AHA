package com.acanx.module.aha.desktop.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogCapture} 测试：面板的数据源必须真的接到日志系统上。
 *
 * <p>这是「门禁必须自证」在桌面端的对应物——日志面板有没有数据，不能靠肉眼看一次就算数：
 * 装 appender、写一条日志、断言它进了缓冲，全部由测试完成。</p>
 *
 * @since 0.2.0
 */
class LogCaptureTest {

    private static final Logger LOG = LoggerFactory.getLogger(LogCaptureTest.class);

    @AfterEach
    void tearDown() {
        LogCapture.uninstall();
        LogCapture.buffer().clear();
    }

    @Test
    void installIsIdempotent() {
        assertThat(LogCapture.install()).isTrue();
        assertThat(LogCapture.install()).as("重复安装必须无害").isTrue();
        assertThat(LogCapture.installed()).isTrue();
    }

    @Test
    void capturedLinesReachTheBuffer() {
        LogCapture.install();
        LogCapture.buffer().clear();

        LOG.info("面板自证探针 {}", 42);

        assertThat(LogCapture.buffer().snapshot(LogLevel.TRACE))
                .anySatisfy(line -> assertThat(line.message()).contains("面板自证探针 42"));
    }

    @Test
    void reinstallAfterUninstallCapturesAgain() {
        // 这个场景曾经静默失效：uninstall 只摘掉根记录器上的引用，appender 对象仍在配置里，
        // 于是「配置里已有该 appender」的判重逻辑会跳过后面的挂载。
        LogCapture.install();
        LogCapture.uninstall();
        LogCapture.buffer().clear();

        assertThat(LogCapture.install()).isTrue();
        LOG.info("重新安装后的探针");

        assertThat(LogCapture.buffer().snapshot(LogLevel.TRACE))
                .anySatisfy(line -> assertThat(line.message()).contains("重新安装后的探针"));
    }

    @Test
    void uninstallStopsCapturing() {
        LogCapture.install();
        LogCapture.uninstall();
        assertThat(LogCapture.installed()).isFalse();

        LogCapture.buffer().clear();
        LOG.info("卸载之后的日志不应进缓冲");

        assertThat(LogCapture.buffer().size()).isZero();
    }
}
