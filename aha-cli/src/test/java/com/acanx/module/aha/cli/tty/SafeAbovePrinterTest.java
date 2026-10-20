package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SafeAbovePrinter} 测试（issue #384）。
 *
 * <p>回归背景：JLine 4.4.6 在含 CJK 的长行上从 {@code printAbove} 抛
 * {@code IllegalArgumentException: offset is out of bounds}，异常一路冒到
 * {@code ChatCommand#runTurn}，整轮回答被判失败。渲染异常必须被兜在渲染层。</p>
 *
 * @since 0.1.5
 */
class SafeAbovePrinterTest {

    @Test
    void usesPrintAboveWhileHealthy() {
        List<String> above = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        SafeAbovePrinter printer = new SafeAbovePrinter(above::add, fallback::add, null, null);

        printer.accept("第一行");
        printer.accept("第二行");

        assertThat(above).containsExactly("第一行", "第二行");
        assertThat(fallback).isEmpty();
        assertThat(printer.degraded()).isFalse();
    }

    @Test
    void degradesToFallbackAfterRenderFailureAndStopsRetrying() {
        AtomicInteger aboveCalls = new AtomicInteger();
        List<String> fallback = new ArrayList<>();
        SafeAbovePrinter printer = new SafeAbovePrinter(
                line -> {
                    aboveCalls.incrementAndGet();
                    throw new IllegalArgumentException("offset is out of bounds: 76");
                },
                fallback::add, null, null);

        printer.accept("含中文字符的长行");
        printer.accept("后续行");

        // 降级后**不再重试** printAbove：JLine 的缺陷每行都会复现，重试只会刷日志
        assertThat(aboveCalls).hasValue(1);
        assertThat(fallback).containsExactly("含中文字符的长行", "后续行");
        assertThat(printer.degraded()).isTrue();
    }

    @Test
    void fallbackIsWrappedBySuspendAndResume() {
        List<String> events = new ArrayList<>();
        SafeAbovePrinter printer = new SafeAbovePrinter(
                line -> {
                    throw new IllegalStateException("boom");
                },
                line -> events.add("write:" + line),
                () -> events.add("suspend"),
                () -> events.add("resume"));

        printer.accept("正文");

        assertThat(events).containsExactly("suspend", "write:正文", "resume");
    }

    @Test
    void fallbackFailureDoesNotEscape() {
        SafeAbovePrinter printer = new SafeAbovePrinter(
                line -> {
                    throw new IllegalArgumentException("offset is out of bounds: 76");
                },
                line -> {
                    throw new IllegalStateException("terminal gone");
                },
                null, null);

        // 渲染层的问题不允许反过来杀掉回合：连降级都失败也只能丢这一行
        printer.accept("正文");
        assertThat(printer.degraded()).isTrue();
    }

    @Test
    void longCjkLineThroughAboveStreamSurvivesRenderFailure() {
        List<String> written = new ArrayList<>();
        List<String> original = new ArrayList<>();
        SafeAbovePrinter printer = new SafeAbovePrinter(
                line -> {
                    // 模拟 JLine 的越界缺陷
                    throw new IllegalArgumentException("offset is out of bounds: 76");
                },
                written::add, null, null);
        AboveStream stream = new AboveStream(printer, StandardCharsets.UTF_8);
        PrintStream out = new PrintStream(stream, false, StandardCharsets.UTF_8);

        String longLine = "中文注释".repeat(60);
        out.println(longLine);
        out.flush();

        assertThat(String.join("", written)).isEqualTo(longLine);
        // 反向对照：正常路径下同一段内容确实会走 printAbove（否则这条用例证明不了降级）
        SafeAbovePrinter healthy = new SafeAbovePrinter(original::add, written::add, null, null);
        PrintStream healthyOut = new PrintStream(
                new AboveStream(healthy, StandardCharsets.UTF_8), false, StandardCharsets.UTF_8);
        healthyOut.println(longLine);
        healthyOut.flush();
        assertThat(original).containsExactly(longLine);
    }
}
