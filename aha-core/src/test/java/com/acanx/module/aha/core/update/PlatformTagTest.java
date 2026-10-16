package com.acanx.module.aha.core.update;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台标签的归一化与发布范围（issue #51 §2.1）。
 *
 * <p>归一化的目标值取发布页上<b>实际出现过</b>的标签（实查 2026-10-16）：
 * {@code windows-amd64} / {@code ubuntu-amd64} / {@code macos-arm64}。</p>
 *
 * @since 0.2.0
 */
class PlatformTagTest {

    @Test
    void normalizesWindowsLinuxAndMacos() {
        // 系统属性的真实取值就是这样带空格与大写的（`os.name` = "Windows 11" / "Mac OS X"）
        assertThat(PlatformTag.of("Windows 11", "amd64").value()).isEqualTo("windows-amd64");
        assertThat(PlatformTag.of("Windows 10", "x86_64").value()).isEqualTo("windows-amd64");
        assertThat(PlatformTag.of("Linux", "x86_64").value()).isEqualTo("ubuntu-amd64");
        assertThat(PlatformTag.of("Linux", "aarch64").value()).isEqualTo("ubuntu-arm64");
        assertThat(PlatformTag.of("Mac OS X", "aarch64").value()).isEqualTo("macos-arm64");
        assertThat(PlatformTag.of("Mac OS X", "x86_64").value()).isEqualTo("macos-amd64");
        assertThat(PlatformTag.of("Darwin", "arm64").value()).isEqualTo("macos-arm64");
    }

    @Test
    void normalizationIsIdempotentAndKeepsUnknownTokensVerbatim() {
        PlatformTag once = PlatformTag.of("FreeBSD", "riscv64");
        PlatformTag twice = PlatformTag.of(once.os(), once.arch());

        assertThat(once).isEqualTo(twice);
        assertThat(once.value()).isEqualTo("freebsd-riscv64");
        // 不认识的平台不抛异常 —— 由 hasPublishedPackages() 判否并给理由，界面照旧能开。
        assertThat(once.hasPublishedPackages()).isFalse();
    }

    @Test
    void rejectsBlankOsOrArch() {
        assertThatThrownBy(() -> PlatformTag.of("  ", "amd64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作系统名");
        assertThatThrownBy(() -> PlatformTag.of("Windows 11", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("架构名");
    }

    @Test
    void knowsWhichPlatformsHavePublishedPackages() {
        assertThat(PlatformTag.of("Windows 11", "amd64").hasPublishedPackages()).isTrue();
        assertThat(PlatformTag.of("Linux", "x86_64").hasPublishedPackages()).isTrue();
        assertThat(PlatformTag.of("Mac OS X", "aarch64").hasPublishedPackages()).isTrue();
        assertThat(PlatformTag.of("Windows 11", "aarch64").hasPublishedPackages()).isFalse();
        assertThat(PlatformTag.of("Mac OS X", "x86_64").hasPublishedPackages()).isFalse();
        assertThat(PlatformTag.of("Linux", "aarch64").hasPublishedPackages()).isFalse();
    }

    @Test
    void explainsWhyAPlatformHasNoPackages() {
        // 有包 ⇒ 无理由（空串）；没包 ⇒ 必须写清「为什么」，不能只说「不支持」
        assertThat(PlatformTag.of("Windows 11", "amd64").unpublishedReason()).isEmpty();
        assertThat(PlatformTag.of("Windows 11", "aarch64").unpublishedReason())
                .contains("Windows ARM64").contains("OpenJFX");
        assertThat(PlatformTag.of("Mac OS X", "x86_64").unpublishedReason())
                .contains("macOS Intel").contains("Apple Silicon");
        assertThat(PlatformTag.of("Linux", "aarch64").unpublishedReason())
                .contains("Linux ARM64").contains("ubuntu-amd64");
        assertThat(PlatformTag.of("FreeBSD", "amd64").unpublishedReason())
                .contains("freebsd-amd64");
    }

    @Test
    void detectReadsTheLocalMachineWithoutThrowing() {
        PlatformTag local = PlatformTag.detect();

        assertThat(local.value()).isNotBlank();
        assertThat(local.toString()).isEqualTo(local.value());
        // 与系统属性归一化保持一致（不写死具体平台，否则同一份测试会在别的 runner 上红）
        assertThat(local).isEqualTo(PlatformTag.of(System.getProperty("os.name"),
                System.getProperty("os.arch")));
    }
}
